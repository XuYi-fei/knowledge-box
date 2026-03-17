package com.knowledgebox.service.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.DocumentDuplicateCleanupItemView;
import com.knowledgebox.api.DocumentDuplicateCleanupPreviewView;
import com.knowledgebox.api.DocumentDuplicateCleanupRequest;
import com.knowledgebox.api.DocumentDuplicateCleanupResultView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentIndexRebuildStatus;
import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentTagBinding;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.KnowledgeDocument;
import com.knowledgebox.repository.DocumentIndexRebuildJobRepository;
import com.knowledgebox.repository.DocumentTagBindingRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import jakarta.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentDuplicateCleanupService {

    private static final int MAX_LIMIT = 1000;
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeBoxProperties properties;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentTagBindingRepository documentTagBindingRepository;
    private final DocumentIndexRebuildJobRepository documentIndexRebuildJobRepository;
    private final ObjectMapper objectMapper;

    public DocumentDuplicateCleanupService(
            JdbcTemplate jdbcTemplate,
            KnowledgeBoxProperties properties,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            DocumentTagBindingRepository documentTagBindingRepository,
            DocumentIndexRebuildJobRepository documentIndexRebuildJobRepository,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentTagBindingRepository = documentTagBindingRepository;
        this.documentIndexRebuildJobRepository = documentIndexRebuildJobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DocumentDuplicateCleanupPreviewView preview(
            @Nullable DocumentVisibilityType visibilityType,
            @Nullable DocumentStatus status,
            @Nullable String keepStrategy,
            @Nullable Integer limit
    ) {
        CleanupSpec spec = CleanupSpec.normalize(visibilityType, status, keepStrategy, limit);
        List<DuplicateTargetRow> rows = queryDuplicateTargets(spec);
        return new DocumentDuplicateCleanupPreviewView(
                rows.stream().map(DuplicateTargetRow::toView).toList(),
                rows.size(),
                spec.visibilityType(),
                spec.status(),
                spec.keepStrategy().name(),
                spec.limit()
        );
    }

    @Transactional
    public DocumentDuplicateCleanupResultView cleanup(DocumentDuplicateCleanupRequest request) {
        CleanupSpec spec = CleanupSpec.normalize(
                request == null ? null : request.visibilityType(),
                request == null ? null : request.status(),
                request == null ? null : request.keepStrategy(),
                request == null ? null : request.limit()
        );
        ensureIndexRebuildNotRunning();
        List<DuplicateTargetRow> rows = queryDuplicateTargets(spec);
        if (rows.isEmpty()) {
            return new DocumentDuplicateCleanupResultView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        }

        String mappingCte = mappingCte(rows);
        int mergedTagBindings = jdbcTemplate.update(
                mappingCte
                        + """
                        insert into document_tag_binding (document_id, tag_id)
                        select distinct mapping.keep_id, binding.tag_id
                        from document_tag_binding binding
                        join mapping on mapping.delete_id = binding.document_id
                        on conflict (document_id, tag_id) do nothing
                        """
        );
        int rewiredSourceReviews = jdbcTemplate.update(
                mappingCte
                        + """
                        update document_review_request review
                           set source_document_id = mapping.keep_id,
                               updated_at = now()
                          from mapping
                         where review.source_document_id = mapping.delete_id
                        """
        );
        int rewiredPublishedReviews = jdbcTemplate.update(
                mappingCte
                        + """
                        update document_review_request review
                           set published_document_id = mapping.keep_id,
                               updated_at = now()
                          from mapping
                         where review.published_document_id = mapping.delete_id
                        """
        );
        int rewiredIngestionJobs = jdbcTemplate.update(
                mappingCte
                        + """
                        update ingestion_job job
                           set document_id = mapping.keep_id,
                               updated_at = now()
                          from mapping
                         where job.document_id = mapping.delete_id
                        """
        );
        int deletedTagBindings = jdbcTemplate.update(
                mappingCte
                        + """
                        delete from document_tag_binding binding
                         using mapping
                         where binding.document_id = mapping.delete_id
                        """
        );
        int deletedAssets = jdbcTemplate.update(
                mappingCte
                        + """
                        delete from document_asset asset
                         using mapping
                         where asset.document_id = mapping.delete_id
                        """
        );

        List<Long> duplicateChunkIds = jdbcTemplate.queryForList(
                mappingCte
                        + """
                        select chunk.id
                          from document_chunk chunk
                          join mapping on mapping.delete_id = chunk.document_id
                        """,
                Long.class
        );
        long vectorRowsDeleted = deleteVectorRows(duplicateChunkIds);
        int deletedChunks = jdbcTemplate.update(
                mappingCte
                        + """
                        delete from document_chunk chunk
                         using mapping
                         where chunk.document_id = mapping.delete_id
                        """
        );
        int deletedDocuments = jdbcTemplate.update(
                mappingCte
                        + """
                        delete from knowledge_document doc
                         using mapping
                         where doc.id = mapping.delete_id
                        """
        );
        int refreshedKeeperTags = refreshKeeperTags(rows.stream().map(DuplicateTargetRow::keepDocumentId).distinct().toList());
        return new DocumentDuplicateCleanupResultView(
                rows.size(),
                mergedTagBindings,
                rewiredSourceReviews,
                rewiredPublishedReviews,
                rewiredIngestionJobs,
                deletedTagBindings,
                deletedAssets,
                deletedChunks,
                vectorRowsDeleted,
                refreshedKeeperTags,
                deletedDocuments,
                null
        );
    }

    private void ensureIndexRebuildNotRunning() {
        documentIndexRebuildJobRepository.findFirstByStatusOrderByStartedAtDesc(DocumentIndexRebuildStatus.RUNNING)
                .ifPresent(job -> {
                    throw new ApiException(HttpStatus.CONFLICT, "INDEX_REBUILD_RUNNING", "索引重建任务正在执行，请稍后重试");
                });
    }

    private List<DuplicateTargetRow> queryDuplicateTargets(CleanupSpec spec) {
        List<Object> args = new ArrayList<>();
        args.add(spec.visibilityType().name());
        args.add(spec.status().name());
        String limitClause = "";
        if (spec.limit() > 0) {
            limitClause = " limit ?";
            args.add(spec.limit());
        }
        String sql = """
                with ranked as (
                    select
                        doc.id,
                        doc.category_id,
                        coalesce(category.name, '') as category_name,
                        doc.title,
                        doc.source_filename,
                        md5(coalesce(doc.source_markdown, '')) as content_fingerprint,
                        coalesce(doc.extension_json, '{}')::jsonb ->> 'importKey' as import_key,
                        row_number() over (
                            partition by doc.category_id,
                                         lower(btrim(coalesce(doc.title, ''))),
                                         md5(coalesce(doc.source_markdown, '')),
                                         doc.visibility_type,
                                         doc.status
                            order by doc.id %s
                        ) as row_no,
                        first_value(doc.id) over (
                            partition by doc.category_id,
                                         lower(btrim(coalesce(doc.title, ''))),
                                         md5(coalesce(doc.source_markdown, '')),
                                         doc.visibility_type,
                                         doc.status
                            order by doc.id %s
                        ) as keep_id,
                        count(*) over (
                            partition by doc.category_id,
                                         lower(btrim(coalesce(doc.title, ''))),
                                         md5(coalesce(doc.source_markdown, '')),
                                         doc.visibility_type,
                                         doc.status
                        ) as group_size
                    from knowledge_document doc
                    left join document_category category on category.id = doc.category_id
                    where doc.visibility_type = ?
                      and doc.status = ?
                ),
                targets as (
                    select *
                    from ranked
                    where group_size > 1
                      and row_no > 1
                    order by category_name asc, title asc, id asc
                %s
                )
                select
                    targets.keep_id,
                    keep_document.source_filename as keep_source_filename,
                    coalesce(keep_document.extension_json, '{}')::jsonb ->> 'importKey' as keep_import_key,
                    targets.id as duplicate_document_id,
                    targets.source_filename as duplicate_source_filename,
                    coalesce(targets.import_key, '') as duplicate_import_key,
                    nullif(targets.category_name, '') as category_name,
                    targets.title,
                    targets.content_fingerprint,
                    (select count(*) from document_chunk chunk where chunk.document_id = targets.id) as chunk_count,
                    (select count(*) from document_asset asset where asset.document_id = targets.id) as asset_count,
                    (select count(*) from document_tag_binding binding where binding.document_id = targets.id) as tag_count,
                    (select count(*) from document_review_request review where review.source_document_id = targets.id) as source_review_ref_count,
                    (select count(*) from document_review_request review where review.published_document_id = targets.id) as published_review_ref_count,
                    (select count(*) from ingestion_job job where job.document_id = targets.id) as ingestion_ref_count
                from targets
                join knowledge_document keep_document on keep_document.id = targets.keep_id
                order by targets.category_name asc, targets.title asc, targets.id asc
                """.formatted(spec.keepStrategy().sqlOrderDirection(), spec.keepStrategy().sqlOrderDirection(), limitClause);
        return jdbcTemplate.query(sql, this::mapDuplicateTargetRow, args.toArray());
    }

    private DuplicateTargetRow mapDuplicateTargetRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new DuplicateTargetRow(
                resultSet.getLong("keep_id"),
                resultSet.getString("keep_source_filename"),
                resultSet.getString("keep_import_key"),
                resultSet.getLong("duplicate_document_id"),
                resultSet.getString("duplicate_source_filename"),
                resultSet.getString("duplicate_import_key"),
                resultSet.getString("category_name"),
                resultSet.getString("title"),
                resultSet.getString("content_fingerprint"),
                resultSet.getLong("chunk_count"),
                resultSet.getLong("asset_count"),
                resultSet.getLong("tag_count"),
                resultSet.getLong("source_review_ref_count"),
                resultSet.getLong("published_review_ref_count"),
                resultSet.getLong("ingestion_ref_count")
        );
    }

    private String mappingCte(List<DuplicateTargetRow> rows) {
        return "with mapping(delete_id, keep_id) as (values "
                + rows.stream()
                .map(row -> "(" + row.duplicateDocumentId() + ", " + row.keepDocumentId() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow()
                + ") ";
    }

    private long deleteVectorRows(Collection<Long> chunkIds) {
        if (!properties.getRetrieval().isVectorEnabled() || chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        String vectorSchema = properties.getRetrieval().getVectorSchema();
        String vectorTable = properties.getRetrieval().getVectorTable();
        if (!StringUtils.hasText(vectorSchema) || !StringUtils.hasText(vectorTable)) {
            return 0;
        }
        if (!SQL_IDENTIFIER.matcher(vectorSchema).matches() || !SQL_IDENTIFIER.matcher(vectorTable).matches()) {
            throw new IllegalStateException("Unsafe vector table identifier: " + vectorSchema + "." + vectorTable);
        }
        List<String> vectorIds = chunkIds.stream().map(chunkId -> "chunk-" + chunkId).toList();
        String placeholders = String.join(", ", java.util.Collections.nCopies(vectorIds.size(), "?"));
        String sql = "delete from \"" + vectorSchema + "\".\"" + vectorTable + "\" where id in (" + placeholders + ")";
        return jdbcTemplate.update(sql, vectorIds.toArray());
    }

    private int refreshKeeperTags(List<Long> keepIds) {
        if (keepIds == null || keepIds.isEmpty()) {
            return 0;
        }
        Map<Long, List<String>> tagNamesByDocumentId = new LinkedHashMap<>();
        for (DocumentTagBinding binding : documentTagBindingRepository.findAllWithTagByDocumentIds(keepIds)) {
            tagNamesByDocumentId.computeIfAbsent(binding.getDocument().getId(), ignored -> new ArrayList<>()).add(binding.getTag().getName());
        }
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAllById(keepIds);
        for (KnowledgeDocument document : documents) {
            document.setTags(writeJson(tagNamesByDocumentId.getOrDefault(document.getId(), List.of())));
        }
        knowledgeDocumentRepository.saveAll(documents);
        return documents.size();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private record DuplicateTargetRow(
            Long keepDocumentId,
            String keepSourceFilename,
            String keepImportKey,
            Long duplicateDocumentId,
            String duplicateSourceFilename,
            String duplicateImportKey,
            String categoryName,
            String title,
            String contentFingerprint,
            long chunkCount,
            long assetCount,
            long tagCount,
            long sourceReviewRefCount,
            long publishedReviewRefCount,
            long ingestionRefCount
    ) {
        private DocumentDuplicateCleanupItemView toView() {
            return new DocumentDuplicateCleanupItemView(
                    keepDocumentId,
                    keepSourceFilename,
                    keepImportKey,
                    duplicateDocumentId,
                    duplicateSourceFilename,
                    duplicateImportKey,
                    categoryName,
                    title,
                    contentFingerprint,
                    chunkCount,
                    assetCount,
                    tagCount,
                    sourceReviewRefCount,
                    publishedReviewRefCount,
                    ingestionRefCount
            );
        }
    }

    private record CleanupSpec(
            DocumentVisibilityType visibilityType,
            DocumentStatus status,
            KeepStrategy keepStrategy,
            int limit
    ) {
        private static CleanupSpec normalize(
                @Nullable DocumentVisibilityType visibilityType,
                @Nullable DocumentStatus status,
                @Nullable String keepStrategy,
                @Nullable Integer limit
        ) {
            DocumentVisibilityType resolvedVisibility = visibilityType == null ? DocumentVisibilityType.PUBLIC : visibilityType;
            DocumentStatus resolvedStatus = status == null ? DocumentStatus.READY : status;
            KeepStrategy resolvedKeepStrategy = KeepStrategy.from(keepStrategy);
            int resolvedLimit = limit == null ? 200 : Math.min(Math.max(limit, 0), MAX_LIMIT);
            return new CleanupSpec(resolvedVisibility, resolvedStatus, resolvedKeepStrategy, resolvedLimit);
        }
    }

    private enum KeepStrategy {
        OLDEST("asc"),
        NEWEST("desc");

        private final String sqlOrderDirection;

        KeepStrategy(String sqlOrderDirection) {
            this.sqlOrderDirection = sqlOrderDirection;
        }

        private String sqlOrderDirection() {
            return sqlOrderDirection;
        }

        private static KeepStrategy from(@Nullable String value) {
            if (!StringUtils.hasText(value)) {
                return OLDEST;
            }
            try {
                return KeepStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_DUPLICATE_KEEP_STRATEGY_INVALID", "保留策略仅支持 OLDEST 或 NEWEST");
            }
        }
    }
}
