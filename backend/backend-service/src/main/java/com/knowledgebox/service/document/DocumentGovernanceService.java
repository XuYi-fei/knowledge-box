package com.knowledgebox.service.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgebox.api.BatchDocumentReviewActionResultView;
import com.knowledgebox.api.CreateDocumentReviewRequest;
import com.knowledgebox.api.DocumentAssetView;
import com.knowledgebox.api.DocumentCategoryView;
import com.knowledgebox.api.DocumentColumnDocumentView;
import com.knowledgebox.api.DocumentColumnView;
import com.knowledgebox.api.DocumentIndexRebuildJobView;
import com.knowledgebox.api.DocumentPastedImageUploadView;
import com.knowledgebox.api.DocumentReviewAssetView;
import com.knowledgebox.api.DocumentReviewChunkView;
import com.knowledgebox.api.DocumentReviewRequestDetailView;
import com.knowledgebox.api.DocumentReviewRequestPageView;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.api.DocumentTagView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.PublicDocumentCategoryFacetView;
import com.knowledgebox.api.PublicDocumentFacetView;
import com.knowledgebox.api.PublicDocumentPageView;
import com.knowledgebox.api.PublicDocumentSummaryView;
import com.knowledgebox.api.PublicDocumentTagFacetView;
import com.knowledgebox.api.UpdateDocumentSourceRequest;
import com.knowledgebox.api.UpdateReviewTaxonomyRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentAsset;
import com.knowledgebox.domain.document.DocumentAssetRole;
import com.knowledgebox.domain.document.DocumentCategory;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.domain.document.DocumentColumn;
import com.knowledgebox.domain.document.DocumentIndexRebuildJob;
import com.knowledgebox.domain.document.DocumentIndexRebuildStatus;
import com.knowledgebox.domain.document.DocumentReviewAsset;
import com.knowledgebox.domain.document.DocumentReviewChunk;
import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.DocumentReviewStatus;
import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentTag;
import com.knowledgebox.domain.document.DocumentTagBinding;
import com.knowledgebox.domain.document.DocumentTaxonomySource;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.KnowledgeDocument;
import com.knowledgebox.repository.DocumentAssetRepository;
import com.knowledgebox.repository.DocumentCategoryRepository;
import com.knowledgebox.repository.DocumentChunkRepository;
import com.knowledgebox.repository.DocumentColumnRepository;
import com.knowledgebox.repository.DocumentIndexRebuildJobRepository;
import com.knowledgebox.repository.DocumentReviewAssetRepository;
import com.knowledgebox.repository.DocumentReviewChunkRepository;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.DocumentTagBindingRepository;
import com.knowledgebox.repository.DocumentTagRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.service.chat.KnowledgeBaseIndexingService;
import com.knowledgebox.service.chat.KnowledgeBaseRetrievalService;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentGovernanceService {

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final int PUBLIC_DOCUMENT_EXCERPT_LIMIT = 180;

    private final KnowledgeBoxProperties properties;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentAssetRepository documentAssetRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentCategoryRepository documentCategoryRepository;
    private final DocumentColumnRepository documentColumnRepository;
    private final DocumentTagRepository documentTagRepository;
    private final DocumentTagBindingRepository documentTagBindingRepository;
    private final DocumentReviewRequestRepository documentReviewRequestRepository;
    private final DocumentReviewAssetRepository documentReviewAssetRepository;
    private final DocumentReviewChunkRepository documentReviewChunkRepository;
    private final DocumentIndexRebuildJobRepository documentIndexRebuildJobRepository;
    private final StorageService storageService;
    private final DocumentTaxonomyAgentService documentTaxonomyAgentService;
    private final KnowledgeBaseIndexingService knowledgeBaseIndexingService;
    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectMapper objectMapper;

    public DocumentGovernanceService(
            KnowledgeBoxProperties properties,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            DocumentAssetRepository documentAssetRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentCategoryRepository documentCategoryRepository,
            DocumentColumnRepository documentColumnRepository,
            DocumentTagRepository documentTagRepository,
            DocumentTagBindingRepository documentTagBindingRepository,
            DocumentReviewRequestRepository documentReviewRequestRepository,
            DocumentReviewAssetRepository documentReviewAssetRepository,
            DocumentReviewChunkRepository documentReviewChunkRepository,
            DocumentIndexRebuildJobRepository documentIndexRebuildJobRepository,
            StorageService storageService,
            DocumentTaxonomyAgentService documentTaxonomyAgentService,
            KnowledgeBaseIndexingService knowledgeBaseIndexingService,
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentAssetRepository = documentAssetRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentCategoryRepository = documentCategoryRepository;
        this.documentColumnRepository = documentColumnRepository;
        this.documentTagRepository = documentTagRepository;
        this.documentTagBindingRepository = documentTagBindingRepository;
        this.documentReviewRequestRepository = documentReviewRequestRepository;
        this.documentReviewAssetRepository = documentReviewAssetRepository;
        this.documentReviewChunkRepository = documentReviewChunkRepository;
        this.documentIndexRebuildJobRepository = documentIndexRebuildJobRepository;
        this.storageService = storageService;
        this.documentTaxonomyAgentService = documentTaxonomyAgentService;
        this.knowledgeBaseIndexingService = knowledgeBaseIndexingService;
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.vectorStoreProvider = vectorStoreProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentView> documents() {
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAllForAdmin();
        Map<Long, List<String>> tagNamesByDocumentId = loadTagNamesByDocumentId(documents);
        return documents.stream()
                .map(document -> toDocumentView(document, tagNamesByDocumentId.getOrDefault(document.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentView documentDetail(Long id) {
        KnowledgeDocument document = knowledgeDocumentRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文档不存在"));
        List<String> tags = documentTagBindingRepository.findByDocument_IdOrderByIdAsc(id).stream()
                .map(binding -> binding.getTag().getName())
                .toList();
        return toDocumentView(document, tags);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentView userVisibleDocumentDetail(Long id) {
        KnowledgeDocument document = knowledgeDocumentRepository.findByIdWithCategoryAndVisibilityTypeAndStatus(
                        id,
                        DocumentVisibilityType.PUBLIC,
                        DocumentStatus.READY
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文档不存在或暂不可查看"));
        List<String> tags = documentTagBindingRepository.findByDocument_IdOrderByIdAsc(id).stream()
                .map(binding -> binding.getTag().getName())
                .toList();
        return toDocumentView(document, tags, loadColumnDocuments(document));
    }

    @Transactional(readOnly = true)
    public PublicDocumentPageView publicDocuments(@Nullable Long categoryId, List<Long> tagIds, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 48);
        List<Long> normalizedTagIds = normalizeTagIds(tagIds);
        List<PublicDocumentAggregate> documents = loadPublicDocumentAggregates(categoryId, normalizedTagIds);
        int fromIndex = Math.min((safePage - 1) * safePageSize, documents.size());
        int toIndex = Math.min(fromIndex + safePageSize, documents.size());
        List<PublicDocumentSummaryView> items = documents.subList(fromIndex, toIndex).stream()
                .map(aggregate -> {
                    KnowledgeDocument document = aggregate.document();
                    return new PublicDocumentSummaryView(
                        document.getId(),
                        document.getTitle(),
                        document.getCategory() == null ? null : document.getCategory().getName(),
                        aggregate.tagNames(),
                        buildPublicDocumentExcerpt(document.getSourceMarkdown()),
                        document.getUpdatedAt()
                    );
                })
                .toList();
        return new PublicDocumentPageView(items, documents.size(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public PublicDocumentFacetView publicDocumentFacets() {
        List<PublicDocumentAggregate> documents = loadPublicDocumentAggregates(null, List.of());
        Map<Long, CategoryFacetAccumulator> categories = new LinkedHashMap<>();
        Map<Long, TagFacetAccumulator> allTags = new LinkedHashMap<>();
        for (PublicDocumentAggregate aggregate : documents) {
            KnowledgeDocument document = aggregate.document();
            CategoryFacetAccumulator categoryAccumulator = null;
            if (document.getCategory() != null) {
                categoryAccumulator = categories.computeIfAbsent(
                        document.getCategory().getId(),
                        ignored -> new CategoryFacetAccumulator(document.getCategory().getId(), document.getCategory().getName())
                );
                categoryAccumulator.incrementDocumentCount();
            }

            for (DocumentTag tag : aggregate.tags()) {
                TagFacetAccumulator allTagAccumulator = allTags.computeIfAbsent(
                        tag.getId(),
                        ignored -> new TagFacetAccumulator(tag.getId(), tag.getName())
                );
                allTagAccumulator.incrementDocumentCount();
                if (categoryAccumulator != null) {
                    categoryAccumulator.tag(tag).incrementDocumentCount();
                }
            }
        }

        List<PublicDocumentCategoryFacetView> categoryViews = categories.values().stream()
                .sorted(Comparator.comparing(CategoryFacetAccumulator::name, String.CASE_INSENSITIVE_ORDER))
                .map(CategoryFacetAccumulator::toView)
                .toList();
        List<PublicDocumentTagFacetView> allTagViews = allTags.values().stream()
                .sorted(Comparator.comparing(TagFacetAccumulator::name, String.CASE_INSENSITIVE_ORDER))
                .map(TagFacetAccumulator::toView)
                .toList();
        return new PublicDocumentFacetView(documents.size(), categoryViews, allTagViews);
    }

    @Transactional(readOnly = true)
    public List<DocumentCategoryView> categories() {
        return documentCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new DocumentCategoryView(category.getId(), category.getName(), category.getSource()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentColumnView> columns() {
        return documentColumnRepository.findAllByOrderByNameAsc().stream()
                .map(column -> new DocumentColumnView(column.getId(), column.getName(), column.getSource()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentTagView> tags() {
        return documentTagRepository.findAllByOrderByNameAsc().stream()
                .map(tag -> new DocumentTagView(tag.getId(), tag.getName(), tag.getSource()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentReviewRequestPageView reviewRequests(@Nullable DocumentReviewStatus status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        );
        Page<DocumentReviewRequest> reviewPage = status == null
                ? documentReviewRequestRepository.findAll(pageable)
                : documentReviewRequestRepository.findByStatus(status, pageable);
        List<DocumentReviewRequestSummaryView> items = reviewPage.getContent().stream()
                .map(this::toReviewSummaryView)
                .toList();
        return new DocumentReviewRequestPageView(items, reviewPage.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public DocumentReviewRequestDetailView reviewRequestDetail(Long id) {
        DocumentReviewRequest review = loadReviewRequest(id);
        List<DocumentReviewAssetView> assets = documentReviewAssetRepository.findByReviewRequest_IdOrderByIdAsc(id).stream()
                .map(asset -> new DocumentReviewAssetView(
                        asset.getId(),
                        asset.getOriginalPath(),
                        asset.getStoredUrl(),
                        asset.getProvider(),
                        asset.getObjectKey(),
                        asset.getContentType(),
                        asset.getContentLength()
                ))
                .toList();
        List<DocumentReviewChunkView> chunks = documentReviewChunkRepository.findByReviewRequest_IdOrderByChunkIndexAsc(id).stream()
                .map(chunk -> new DocumentReviewChunkView(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getHeadingPath(),
                        chunk.getAnchor(),
                        chunk.getContent(),
                        chunk.getMetadataJson()
                ))
                .toList();
        return toReviewDetailView(review, assets, chunks);
    }

    @Transactional
    public DocumentUploadResult createUploadReview(
            MultipartFile markdown,
            List<MultipartFile> assets,
            @Nullable String titleOverride,
            @Nullable DocumentVisibilityType visibilityType,
            @Nullable String extensionJson,
            Long operatorId
    ) {
        String originalFilename = StringUtils.hasText(markdown.getOriginalFilename()) ? markdown.getOriginalFilename() : "document.md";
        String markdownContent = readBytes(markdown);
        String title = StringUtils.hasText(titleOverride) ? titleOverride.trim() : deriveTitle(originalFilename, markdownContent);
        NormalizedImportExtension normalizedExtension = normalizeImportExtension(extensionJson, markdownContent);
        ensureImportReviewAllowed(normalizedExtension);
        DocumentReviewRequest review = new DocumentReviewRequest();
        review.setRequestCode(UUID.randomUUID().toString());
        review.setTitle(title);
        review.setSourceFilename(originalFilename);
        review.setUploaderType(DocumentUploaderType.ADMIN);
        review.setUploaderUserId(operatorId);
        review.setVisibilityType(visibilityType == null ? DocumentVisibilityType.PUBLIC : visibilityType);
        review.setStatus(DocumentReviewStatus.CREATED);
        review.setStage("CREATED");
        review.setProgressPercent(0);
        review.setSourceMarkdown(markdownContent);
        review.setExtensionJson(normalizedExtension.json());
        review.setSelectedCategoryName(null);
        review.setSelectedColumnName(null);
        review.setSelectedTagsJson("[]");
        review.setSuggestedTagsJson("[]");
        review = documentReviewRequestRepository.save(review);

        List<PendingAsset> pendingAssets = toPendingAssets(assets);
        startReviewPipeline(review.getId(), markdownContent, originalFilename, pendingAssets);
        return new DocumentUploadResult(
                review.getTitle(),
                review.getSourceFilename(),
                review.getNormalizedMarkdownPath(),
                List.of(),
                review.getId(),
                review.getRequestCode()
        );
    }

    @Transactional
    public DocumentUploadResult createUploadReview(CreateDocumentReviewRequest request, Long operatorId) {
        String title = request.title().trim();
        String sourceFilename = request.sourceFilename().trim();
        String sourceMarkdown = request.sourceMarkdown();
        NormalizedImportExtension normalizedExtension = normalizeImportExtension(request.extensionJson(), sourceMarkdown);
        ensureImportReviewAllowed(normalizedExtension);
        DocumentReviewRequest review = new DocumentReviewRequest();
        review.setRequestCode(UUID.randomUUID().toString());
        review.setTitle(title);
        review.setSourceFilename(sourceFilename);
        review.setUploaderType(DocumentUploaderType.ADMIN);
        review.setUploaderUserId(operatorId);
        review.setVisibilityType(request.visibilityType() == null ? DocumentVisibilityType.PUBLIC : request.visibilityType());
        review.setStatus(DocumentReviewStatus.CREATED);
        review.setStage("CREATED");
        review.setProgressPercent(0);
        review.setSourceMarkdown(sourceMarkdown);
        review.setExtensionJson(normalizedExtension.json());
        review.setSelectedCategoryName(normalizeOptionalName(request.selectedCategoryName()));
        review.setSelectedColumnName(normalizeOptionalName(request.selectedColumnName()));
        review.setSelectedTagsJson("[]");
        review.setSuggestedTagsJson("[]");
        review = documentReviewRequestRepository.save(review);

        startReviewPipeline(review.getId(), sourceMarkdown, sourceFilename, List.of());
        return new DocumentUploadResult(
                review.getTitle(),
                review.getSourceFilename(),
                review.getNormalizedMarkdownPath(),
                List.of(),
                review.getId(),
                review.getRequestCode()
        );
    }

    @Transactional
    public DocumentReviewRequestSummaryView createPreparedPendingReview(PreparedPendingReviewRequest request) {
        String markdown = request.sourceMarkdown() == null ? "" : request.sourceMarkdown().trim();
        if (!StringUtils.hasText(markdown)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_MARKDOWN_REQUIRED", "整理后的 Markdown 不能为空");
        }
        List<String> selectedTags = normalizeTagNames(request.selectedTags());
        String normalizedPath = storeNormalizedMarkdown(request.sourceFilename(), markdown);

        DocumentReviewRequest review = new DocumentReviewRequest();
        review.setRequestCode(UUID.randomUUID().toString());
        review.setTitle(StringUtils.hasText(request.title()) ? request.title().trim() : deriveTitle(request.sourceFilename(), markdown));
        review.setSourceFilename(StringUtils.hasText(request.sourceFilename()) ? request.sourceFilename().trim() : "document.md");
        review.setUploaderType(request.uploaderType() == null ? DocumentUploaderType.ADMIN : request.uploaderType());
        review.setUploaderUserId(request.uploaderUserId());
        review.setVisibilityType(request.visibilityType() == null ? DocumentVisibilityType.PUBLIC : request.visibilityType());
        review.setStatus(DocumentReviewStatus.PROCESSING);
        review.setStage("PREPARE_REVIEW");
        review.setProgressPercent(90);
        review.setSourceMarkdown(markdown);
        review.setNormalizedMarkdownPath(normalizedPath);
        review.setExtensionJson(normalizeJsonObject(request.extensionJson()));
        review.setVectorConfigJson("{}");
        review.setSuggestedCategoryName(normalizeOptionalName(request.selectedCategoryName()));
        review.setSuggestedTagsJson(writeJson(selectedTags));
        review.setSelectedCategoryName(normalizeOptionalName(request.selectedCategoryName()));
        review.setSelectedColumnName(normalizeOptionalName(request.selectedColumnName()));
        review.setSelectedTagsJson(writeJson(selectedTags));
        review.setTaxonomyReasoning(normalizeOptionalName(request.taxonomyReasoning()));
        review = documentReviewRequestRepository.save(review);

        if (request.sourceAsset() != null) {
            DocumentReviewAsset asset = new DocumentReviewAsset();
            asset.setReviewRequest(review);
            asset.setOriginalPath(request.sourceAsset().originalPath());
            asset.setStoredUrl(request.sourceAsset().storedUrl());
            asset.setAssetRole(request.sourceAsset().assetRole());
            asset.setProvider(request.sourceAsset().provider());
            asset.setObjectKey(request.sourceAsset().objectKey());
            asset.setContentType(request.sourceAsset().contentType());
            asset.setContentLength(request.sourceAsset().contentLength());
            documentReviewAssetRepository.save(asset);
        }

        List<DocumentReviewChunk> chunks = splitMarkdown(review, markdown);
        documentReviewChunkRepository.saveAll(chunks);
        review.setVectorConfigJson(writeJson(Map.of(
                "chunkSize", properties.getRetrieval().getChunkSize(),
                "chunkCount", chunks.size(),
                "assetCount", request.sourceAsset() == null ? 0 : 1,
                "vectorTable", properties.getRetrieval().getVectorTable()
        )));
        review.setStatus(DocumentReviewStatus.PENDING_REVIEW);
        review.setStage("PENDING_REVIEW");
        review.setProgressPercent(100);
        review.setErrorMessage(null);
        return toReviewSummaryView(documentReviewRequestRepository.save(review));
    }

    public DocumentPastedImageUploadView uploadPastedImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "请上传图片文件");
        }
        String originalFilename = StringUtils.cleanPath(
                StringUtils.hasText(image.getOriginalFilename()) ? image.getOriginalFilename() : "pasted-image"
        );
        String extension = resolveImageExtension(originalFilename, image.getContentType());
        byte[] bytes = readBytesRaw(image);
        String md5 = DigestUtils.md5DigestAsHex(bytes);
        String objectName = md5 + "." + extension;
        PendingAsset pendingAsset = new PendingAsset(objectName, image.getContentType(), bytes);
        StorageService.StoredObject stored = storageService.storeDeterministic("assets", objectName, pendingAsset.toMultipartFile());
        return new DocumentPastedImageUploadView(
                md5,
                stored.provider(),
                stored.objectKey(),
                stored.url(),
                stored.contentType(),
                stored.contentLength()
        );
    }

    @Transactional
    public DocumentReviewRequestSummaryView createEditReview(Long documentId, UpdateDocumentSourceRequest request, Long operatorId) {
        KnowledgeDocument sourceDocument = knowledgeDocumentRepository.findByIdWithCategory(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文档不存在"));
        documentReviewRequestRepository.findFirstBySourceDocument_IdAndStatusInOrderByUpdatedAtDescIdDesc(
                        documentId,
                        List.copyOf(EnumSet.of(
                                DocumentReviewStatus.CREATED,
                                DocumentReviewStatus.PROCESSING,
                                DocumentReviewStatus.PENDING_REVIEW
                        ))
                )
                .ifPresent(existing -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "REVIEW_ALREADY_IN_PROGRESS",
                            "文档已有审核单处理中，请先完成当前审核: " + existing.getRequestCode()
                    );
                });

        DocumentReviewRequest review = new DocumentReviewRequest();
        review.setRequestCode(UUID.randomUUID().toString());
        review.setSourceDocument(sourceDocument);
        review.setTitle(request.title().trim());
        review.setSourceFilename(request.sourceFilename().trim());
        review.setUploaderType(DocumentUploaderType.ADMIN);
        review.setUploaderUserId(operatorId);
        review.setVisibilityType(request.visibilityType() == null ? sourceDocument.getVisibilityType() : request.visibilityType());
        review.setStatus(DocumentReviewStatus.CREATED);
        review.setStage("CREATED");
        review.setProgressPercent(0);
        review.setSourceMarkdown(request.sourceMarkdown());
        review.setExtensionJson(normalizeJsonObject(request.extensionJson()));
        review.setSelectedCategoryName(sourceDocument.getCategory() == null ? null : sourceDocument.getCategory().getName());
        review.setSelectedColumnName(sourceDocument.getDocumentColumn() == null ? null : sourceDocument.getDocumentColumn().getName());
        review.setSelectedTagsJson("[]");
        review.setSuggestedTagsJson("[]");
        review = documentReviewRequestRepository.save(review);

        startReviewPipeline(review.getId(), request.sourceMarkdown(), request.sourceFilename(), List.of());
        return toReviewSummaryView(review);
    }

    @Transactional
    public DocumentReviewRequestSummaryView updateReviewTaxonomy(Long reviewId, UpdateReviewTaxonomyRequest request) {
        DocumentReviewRequest review = loadReviewRequest(reviewId);
        ensureReviewPending(review);
        review.setSelectedCategoryName(request.categoryName().trim());
        review.setSelectedColumnName(normalizeOptionalName(request.columnName()));
        review.setSelectedTagsJson(writeJson(normalizeTagNames(request.tags())));
        return toReviewSummaryView(documentReviewRequestRepository.save(review));
    }

    @Transactional
    public DocumentReviewRequestSummaryView approveReview(Long reviewId, Long operatorId, @Nullable String reason) {
        DocumentReviewRequest review = loadReviewRequest(reviewId);
        return toReviewSummaryView(approveReviewInternal(review, operatorId, reason));
    }

    @Transactional
    public BatchDocumentReviewActionResultView batchApproveReviews(List<Long> reviewIds, Long operatorId, @Nullable String reason) {
        List<Long> normalizedReviewIds = normalizeReviewIds(reviewIds);
        List<DocumentReviewRequestSummaryView> items = normalizedReviewIds.stream()
                .map(this::loadReviewRequest)
                .map(review -> toReviewSummaryView(approveReviewInternal(review, operatorId, reason)))
                .toList();
        return new BatchDocumentReviewActionResultView(items.size(), items);
    }

    @Transactional
    public DocumentReviewRequestSummaryView rejectReview(Long reviewId, Long operatorId, @Nullable String reason) {
        DocumentReviewRequest review = loadReviewRequest(reviewId);
        ensureReviewPending(review);
        review.setStatus(DocumentReviewStatus.REJECTED);
        review.setStage("REJECTED");
        review.setProgressPercent(100);
        review.setReviewedByUserId(operatorId);
        review.setReviewedAt(OffsetDateTime.now());
        review.setReviewReason(reason);
        return toReviewSummaryView(documentReviewRequestRepository.save(review));
    }

    @Transactional
    public DocumentIndexRebuildJobView triggerIndexRebuild(Long operatorId) {
        documentIndexRebuildJobRepository.findFirstByStatusOrderByStartedAtDesc(DocumentIndexRebuildStatus.RUNNING)
                .ifPresent(job -> {
                    throw new ApiException(HttpStatus.CONFLICT, "INDEX_REBUILD_RUNNING", "索引重建任务正在执行，请稍后重试");
                });
        String sourceTable = properties.getRetrieval().getVectorTable();
        String targetTable = sourceTable + "_shadow_" + System.currentTimeMillis();
        DocumentIndexRebuildJob job = new DocumentIndexRebuildJob();
        job.setJobCode(UUID.randomUUID().toString());
        job.setStatus(DocumentIndexRebuildStatus.RUNNING);
        job.setTriggeredByUserId(operatorId);
        job.setSourceVectorTable(sourceTable);
        job.setTargetVectorTable(targetTable);
        job.setDetailJson("{}");
        job = documentIndexRebuildJobRepository.save(job);

        Long jobId = job.getId();
        Thread.ofVirtual().name("kb-index-rebuild-" + job.getJobCode()).start(() -> runIndexRebuild(jobId));
        return toIndexRebuildView(job);
    }

    @Transactional(readOnly = true)
    public DocumentIndexRebuildJobView latestIndexRebuild() {
        return documentIndexRebuildJobRepository.findFirstByOrderByStartedAtDesc()
                .map(this::toIndexRebuildView)
                .orElse(null);
    }

    private void startReviewPipeline(Long reviewId, String markdown, String sourceFilename, List<PendingAsset> assets) {
        Runnable startTask = () -> Thread.ofVirtual()
                .name("kb-doc-review-" + reviewId)
                .start(() -> processReviewPipeline(reviewId, markdown, sourceFilename, assets));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startTask.run();
                }
            });
            return;
        }
        startTask.run();
    }

    @Transactional
    protected void processReviewPipeline(Long reviewId, String markdown, String sourceFilename, List<PendingAsset> assets) {
        DocumentReviewRequest review;
        try {
            review = loadReviewRequest(reviewId);
        } catch (ApiException exception) {
            return;
        }
        try {
            review.setStatus(DocumentReviewStatus.PROCESSING);
            review.setStage("UPLOAD_ASSETS");
            review.setProgressPercent(10);
            documentReviewRequestRepository.save(review);

            Map<String, StorageService.StoredObject> uploadedAssets = uploadAssets(assets);
            documentReviewAssetRepository.deleteByReviewRequest_Id(reviewId);
            documentReviewAssetRepository.saveAll(uploadedAssets.entrySet().stream()
                    .map(entry -> {
                        DocumentReviewAsset asset = new DocumentReviewAsset();
                        asset.setReviewRequest(review);
                        asset.setOriginalPath(entry.getKey());
                        asset.setStoredUrl(entry.getValue().url());
                        asset.setProvider(entry.getValue().provider());
                        asset.setObjectKey(entry.getValue().objectKey());
                        asset.setContentType(entry.getValue().contentType());
                        asset.setContentLength(entry.getValue().contentLength());
                        return asset;
                    })
                    .toList());

            review.setStage("NORMALIZE_MARKDOWN");
            review.setProgressPercent(30);
            String normalizedMarkdown = rewriteAssetReferences(markdown, uploadedAssets);
            String normalizedPath = storeNormalizedMarkdown(sourceFilename, normalizedMarkdown);
            review.setNormalizedMarkdownPath(normalizedPath);
            documentReviewRequestRepository.save(review);

            review.setStage("CHUNKING");
            review.setProgressPercent(60);
            List<DocumentReviewChunk> chunks = splitMarkdown(review, normalizedMarkdown);
            documentReviewChunkRepository.deleteByReviewRequest_Id(reviewId);
            documentReviewChunkRepository.saveAll(chunks);
            review.setVectorConfigJson(writeJson(Map.of(
                    "chunkSize", properties.getRetrieval().getChunkSize(),
                    "chunkCount", chunks.size(),
                    "assetCount", uploadedAssets.size(),
                    "vectorTable", properties.getRetrieval().getVectorTable()
            )));
            documentReviewRequestRepository.save(review);

            review.setStage("TAXONOMY_AGENT");
            review.setProgressPercent(85);
            DocumentTaxonomyAgentService.TaxonomySuggestion taxonomySuggestion = documentTaxonomyAgentService.suggest(
                    normalizedMarkdown,
                    documentCategoryRepository.findAllByOrderByNameAsc().stream().map(DocumentCategory::getName).toList(),
                    documentTagRepository.findAllByOrderByNameAsc().stream().map(DocumentTag::getName).toList()
            );
            review.setSuggestedCategoryName(taxonomySuggestion.categoryName());
            review.setSuggestedTagsJson(writeJson(normalizeTagNames(taxonomySuggestion.tagNames())));
            if (!StringUtils.hasText(review.getSelectedCategoryName())) {
                review.setSelectedCategoryName(taxonomySuggestion.categoryName());
            }
            if (!StringUtils.hasText(review.getSelectedTagsJson()) || "[]".equals(review.getSelectedTagsJson().trim())) {
                review.setSelectedTagsJson(writeJson(normalizeTagNames(taxonomySuggestion.tagNames())));
            }
            review.setTaxonomyReasoning(taxonomySuggestion.reasoning());
            review.setSourceMarkdown(markdown);
            review.setStatus(DocumentReviewStatus.PENDING_REVIEW);
            review.setStage("PENDING_REVIEW");
            review.setProgressPercent(100);
            review.setErrorMessage(null);
            documentReviewRequestRepository.save(review);
        } catch (Exception exception) {
            review.setStatus(DocumentReviewStatus.FAILED);
            review.setStage("FAILED");
            review.setProgressPercent(100);
            review.setErrorMessage(exception.getMessage());
            documentReviewRequestRepository.save(review);
        }
    }

    @Transactional
    protected void runIndexRebuild(Long jobId) {
        DocumentIndexRebuildJob job = documentIndexRebuildJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Index rebuild job not found: " + jobId));
        try {
            PgVectorStore shadowStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .schemaName(properties.getRetrieval().getVectorSchema())
                    .dimensions(properties.getRetrieval().getEmbeddingDimensions())
                    .vectorTableName(job.getTargetVectorTable())
                    .idType(PgIdType.TEXT)
                    .initializeSchema(true)
                    .build();
            shadowStore.afterPropertiesSet();

            List<DocumentChunk> chunks = documentChunkRepository.findAllWithDocument();
            knowledgeBaseIndexingService.addDocumentsInBatches(shadowStore, chunks.stream()
                    .map(knowledgeBaseRetrievalService::toVectorDocument)
                    .toList());

            String schema = properties.getRetrieval().getVectorSchema();
            String source = job.getSourceVectorTable();
            String target = job.getTargetVectorTable();
            String backup = source + "_backup_" + System.currentTimeMillis();

            jdbcTemplate.execute("ALTER TABLE IF EXISTS " + schema + "." + source + " RENAME TO " + backup);
            jdbcTemplate.execute("ALTER TABLE " + schema + "." + target + " RENAME TO " + source);
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + schema + "." + backup + " CASCADE");

            job.setStatus(DocumentIndexRebuildStatus.SUCCEEDED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setDetailJson(writeJson(Map.of("chunkCount", chunks.size())));
            job.setErrorMessage(null);
            documentIndexRebuildJobRepository.save(job);
        } catch (Exception exception) {
            job.setStatus(DocumentIndexRebuildStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMessage(resolveRootCauseMessage(exception));
            documentIndexRebuildJobRepository.save(job);
        }
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return StringUtils.hasText(message) ? message : throwable.toString();
    }

    private Map<Long, List<String>> loadTagNamesByDocumentId(List<KnowledgeDocument> documents) {
        return loadTagNamesByDocumentIds(documents.stream().map(KnowledgeDocument::getId).toList());
    }

    private Map<Long, List<String>> loadTagNamesByDocumentIds(Collection<Long> documentIds) {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        if (documentIds == null || documentIds.isEmpty()) {
            return map;
        }
        for (Long documentId : documentIds) {
            map.put(documentId, new ArrayList<>());
        }
        for (DocumentTagBinding binding : documentTagBindingRepository.findAllWithTagByDocumentIds(documentIds)) {
            if (binding.getDocument() == null || binding.getDocument().getId() == null || binding.getTag() == null) {
                continue;
            }
            map.computeIfAbsent(binding.getDocument().getId(), ignored -> new ArrayList<>())
                    .add(binding.getTag().getName());
        }
        return map.entrySet().stream()
                .collect(LinkedHashMap::new, (target, entry) -> target.put(entry.getKey(), List.copyOf(entry.getValue())), LinkedHashMap::putAll);
    }

    private Map<Long, List<DocumentTagBinding>> loadTagBindingsByDocumentIds(Collection<Long> documentIds) {
        Map<Long, List<DocumentTagBinding>> map = new LinkedHashMap<>();
        if (documentIds == null || documentIds.isEmpty()) {
            return map;
        }
        for (Long documentId : documentIds) {
            map.put(documentId, new ArrayList<>());
        }
        for (DocumentTagBinding binding : documentTagBindingRepository.findAllWithTagByDocumentIds(documentIds)) {
            if (binding.getDocument() == null || binding.getDocument().getId() == null) {
                continue;
            }
            map.computeIfAbsent(binding.getDocument().getId(), ignored -> new ArrayList<>()).add(binding);
        }
        return map.entrySet().stream()
                .collect(LinkedHashMap::new, (target, entry) -> target.put(entry.getKey(), List.copyOf(entry.getValue())), LinkedHashMap::putAll);
    }

    private List<PublicDocumentAggregate> loadPublicDocumentAggregates(@Nullable Long categoryId, List<Long> tagIds) {
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAll(
                publicDocumentSpecification(categoryId, tagIds),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        );
        Map<Long, List<DocumentTagBinding>> bindingsByDocumentId = loadTagBindingsByDocumentIds(
                documents.stream().map(KnowledgeDocument::getId).toList()
        );
        LinkedHashMap<String, PublicDocumentAggregateAccumulator> grouped = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            PublicDocumentAggregateAccumulator accumulator = grouped.computeIfAbsent(
                    buildPublicDocumentDedupKey(document),
                    ignored -> new PublicDocumentAggregateAccumulator(document)
            );
            accumulator.absorb(bindingsByDocumentId.getOrDefault(document.getId(), List.of()));
        }
        return grouped.values().stream()
                .map(PublicDocumentAggregateAccumulator::toAggregate)
                .toList();
    }

    private String buildPublicDocumentDedupKey(KnowledgeDocument document) {
        Long categoryId = document.getCategory() == null ? null : document.getCategory().getId();
        String title = document.getTitle() == null ? "" : document.getTitle().trim();
        String contentFingerprint = DigestUtils.md5DigestAsHex(
                (document.getSourceMarkdown() == null ? "" : document.getSourceMarkdown()).getBytes(StandardCharsets.UTF_8)
        );
        return categoryId + "|" + title + "|" + contentFingerprint;
    }

    private Specification<KnowledgeDocument> publicDocumentSpecification(@Nullable Long categoryId, List<Long> tagIds) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("visibilityType"), DocumentVisibilityType.PUBLIC));
            predicates.add(criteriaBuilder.equal(root.get("status"), DocumentStatus.READY));
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }
            if (!tagIds.isEmpty() && query != null) {
                var subquery = query.subquery(Long.class);
                var bindingRoot = subquery.from(DocumentTagBinding.class);
                subquery.select(bindingRoot.get("document").get("id"));
                subquery.where(
                        criteriaBuilder.equal(bindingRoot.get("document").get("id"), root.get("id")),
                        bindingRoot.get("tag").get("id").in(tagIds)
                );
                predicates.add(criteriaBuilder.exists(subquery));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private List<Long> normalizeTagIds(@Nullable List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String buildPublicDocumentExcerpt(String sourceMarkdown) {
        String plainText = (sourceMarkdown == null ? "" : sourceMarkdown)
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(plainText)) {
            return "当前文档暂未提取到可展示摘要。";
        }
        return plainText.length() <= PUBLIC_DOCUMENT_EXCERPT_LIMIT
                ? plainText
                : plainText.substring(0, PUBLIC_DOCUMENT_EXCERPT_LIMIT) + "...";
    }

    private KnowledgeDocumentView toDocumentView(KnowledgeDocument document, List<String> tags) {
        return toDocumentView(document, tags, List.of());
    }

    private KnowledgeDocumentView toDocumentView(
            KnowledgeDocument document,
            List<String> tags,
            List<DocumentColumnDocumentView> columnDocuments
    ) {
        return new KnowledgeDocumentView(
                document.getId(),
                document.getTitle(),
                document.getSourceFilename(),
                document.getStatus(),
                document.getVisibilityType(),
                document.getUploaderType(),
                document.getUploaderUserId(),
                document.getNormalizedMarkdownPath(),
                document.getSourceMarkdown(),
                document.getExtensionJson(),
                document.getVectorConfigJson(),
                document.getCategory() == null ? null : document.getCategory().getName(),
                document.getDocumentColumn() == null ? null : document.getDocumentColumn().getName(),
                writeJson(tags),
                columnDocuments,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private List<DocumentColumnDocumentView> loadColumnDocuments(KnowledgeDocument currentDocument) {
        if (currentDocument.getDocumentColumn() == null || currentDocument.getDocumentColumn().getId() == null) {
            return List.of();
        }
        List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAllByColumnIdAndVisibilityTypeAndStatusOrderByTimeline(
                currentDocument.getDocumentColumn().getId(),
                DocumentVisibilityType.PUBLIC,
                DocumentStatus.READY
        );
        LinkedHashMap<String, KnowledgeDocument> deduplicated = new LinkedHashMap<>();
        for (KnowledgeDocument candidate : documents) {
            String key = buildPublicDocumentDedupKey(candidate);
            KnowledgeDocument existing = deduplicated.get(key);
            if (existing == null || currentDocument.getId().equals(candidate.getId())) {
                deduplicated.put(key, candidate);
            }
        }
        return deduplicated.values().stream()
                .map(document -> new DocumentColumnDocumentView(
                        document.getId(),
                        document.getTitle(),
                        document.getCreatedAt()
                ))
                .toList();
    }

    private DocumentReviewRequestSummaryView toReviewSummaryView(DocumentReviewRequest review) {
        return new DocumentReviewRequestSummaryView(
                review.getId(),
                review.getRequestCode(),
                review.getSourceDocument() == null ? null : review.getSourceDocument().getId(),
                review.getPublishedDocument() == null ? null : review.getPublishedDocument().getId(),
                review.getTitle(),
                review.getSourceFilename(),
                review.getUploaderType(),
                review.getUploaderUserId(),
                review.getVisibilityType(),
                review.getStatus(),
                review.getStage(),
                review.getProgressPercent(),
                review.getSuggestedCategoryName(),
                review.getSuggestedTagsJson(),
                review.getSelectedCategoryName(),
                review.getSelectedColumnName(),
                review.getSelectedTagsJson(),
                review.getErrorMessage(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private DocumentReviewRequestDetailView toReviewDetailView(
            DocumentReviewRequest review,
            List<DocumentReviewAssetView> assets,
            List<DocumentReviewChunkView> chunks
    ) {
        return new DocumentReviewRequestDetailView(
                review.getId(),
                review.getRequestCode(),
                review.getSourceDocument() == null ? null : review.getSourceDocument().getId(),
                review.getPublishedDocument() == null ? null : review.getPublishedDocument().getId(),
                review.getTitle(),
                review.getSourceFilename(),
                review.getUploaderType(),
                review.getUploaderUserId(),
                review.getVisibilityType(),
                review.getStatus(),
                review.getStage(),
                review.getProgressPercent(),
                review.getSourceMarkdown(),
                review.getNormalizedMarkdownPath(),
                review.getExtensionJson(),
                review.getVectorConfigJson(),
                review.getSuggestedCategoryName(),
                review.getSuggestedTagsJson(),
                review.getSelectedCategoryName(),
                review.getSelectedColumnName(),
                review.getSelectedTagsJson(),
                review.getTaxonomyReasoning(),
                review.getReviewReason(),
                review.getReviewedByUserId(),
                review.getReviewedAt(),
                review.getErrorMessage(),
                assets,
                chunks,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private DocumentIndexRebuildJobView toIndexRebuildView(DocumentIndexRebuildJob job) {
        return new DocumentIndexRebuildJobView(
                job.getId(),
                job.getJobCode(),
                job.getStatus(),
                job.getTriggeredByUserId(),
                job.getSourceVectorTable(),
                job.getTargetVectorTable(),
                job.getDetailJson(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private Map<String, StorageService.StoredObject> uploadAssets(List<PendingAsset> assets) {
        Map<String, StorageService.StoredObject> uploaded = new LinkedHashMap<>();
        for (PendingAsset asset : assets) {
            if (!StringUtils.hasText(asset.originalFilename()) || asset.bytes().length == 0) {
                continue;
            }
            StorageService.StoredObject stored = storageService.store("assets", asset.toMultipartFile());
            uploaded.put(asset.originalFilename(), stored);
        }
        return uploaded;
    }

    private String rewriteAssetReferences(String markdown, Map<String, StorageService.StoredObject> uploadedAssets) {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown == null ? "" : markdown);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String originalPath = matcher.group(2).trim();
            String fileName = java.nio.file.Path.of(originalPath).getFileName().toString();
            String replacementPath = uploadedAssets.containsKey(fileName)
                    ? uploadedAssets.get(fileName).url()
                    : originalPath;
            matcher.appendReplacement(builder, Matcher.quoteReplacement("![" + matcher.group(1) + "](" + replacementPath + ")"));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String storeNormalizedMarkdown(String sourceFilename, String normalizedMarkdown) {
        MultipartFile file = new PendingAsset(sourceFilename == null ? "document.md" : sourceFilename, "text/markdown", normalizedMarkdown.getBytes(StandardCharsets.UTF_8))
                .toMultipartFile();
        return storageService.store("docs", file).url();
    }

    private List<DocumentReviewChunk> splitMarkdown(DocumentReviewRequest review, String markdown) {
        List<DocumentReviewChunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;

        for (String rawLine : (markdown == null ? "" : markdown).split("\\R")) {
            String line = rawLine.stripTrailing();
            if (line.matches("^#{1,6}\\s+.*")) {
                chunkIndex = flushChunk(chunks, review, headingStack, buffer, chunkIndex);
                updateHeadingStack(headingStack, line);
                buffer.append(line).append('\n');
                continue;
            }
            if (buffer.length() + line.length() + 1 > properties.getRetrieval().getChunkSize()) {
                chunkIndex = flushChunk(chunks, review, headingStack, buffer, chunkIndex);
            }
            buffer.append(line).append('\n');
        }
        flushChunk(chunks, review, headingStack, buffer, chunkIndex);
        return chunks;
    }

    private int flushChunk(
            List<DocumentReviewChunk> chunks,
            DocumentReviewRequest review,
            List<String> headingStack,
            StringBuilder buffer,
            int chunkIndex
    ) {
        String content = buffer.toString().trim();
        if (!StringUtils.hasText(content)) {
            buffer.setLength(0);
            return chunkIndex;
        }
        DocumentReviewChunk chunk = new DocumentReviewChunk();
        chunk.setReviewRequest(review);
        chunk.setChunkIndex(chunkIndex);
        chunk.setHeadingPath(headingStack.isEmpty() ? "未分节" : String.join(" / ", headingStack));
        chunk.setAnchor(anchorFor(headingStack, chunkIndex));
        chunk.setContent(content);
        chunk.setMetadataJson(writeJson(Map.of(
                "documentTitle", review.getTitle(),
                "headingPath", chunk.getHeadingPath(),
                "chunkIndex", chunkIndex
        )));
        chunks.add(chunk);
        buffer.setLength(0);
        return chunkIndex + 1;
    }

    private void updateHeadingStack(List<String> headingStack, String headingLine) {
        int level = 0;
        while (level < headingLine.length() && headingLine.charAt(level) == '#') {
            level++;
        }
        String heading = headingLine.substring(level).trim();
        while (headingStack.size() >= level) {
            headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(heading);
    }

    private String anchorFor(List<String> headingStack, int chunkIndex) {
        String base = headingStack.isEmpty() ? "chunk" : headingStack.get(headingStack.size() - 1);
        String normalized = base.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-|-$)", "");
        return (normalized.isBlank() ? "chunk" : normalized) + "-" + chunkIndex;
    }

    private String deriveTitle(String sourceFilename, String markdown) {
        for (String line : (markdown == null ? "" : markdown).split("\\R")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        String fallback = StringUtils.hasText(sourceFilename) ? sourceFilename : "Untitled Document";
        return fallback.replace(".md", "");
    }

    private String resolveImageExtension(String originalFilename, @Nullable String contentType) {
        String byFilename = normalizeExtension(StringUtils.getFilenameExtension(originalFilename));
        if (StringUtils.hasText(byFilename) && isCommonImageExtension(byFilename)) {
            return byFilename;
        }
        String byContentType = extensionFromContentType(contentType);
        if (StringUtils.hasText(byContentType)) {
            return byContentType;
        }
        if (StringUtils.hasText(byFilename)) {
            return byFilename;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_EXTENSION_UNKNOWN", "无法识别图片扩展名");
    }

    private boolean isCommonImageExtension(String extension) {
        return extension.equals("png")
                || extension.equals("jpg")
                || extension.equals("jpeg")
                || extension.equals("gif")
                || extension.equals("webp")
                || extension.equals("bmp")
                || extension.equals("svg");
    }

    @Nullable
    private String extensionFromContentType(@Nullable String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator).trim();
        }
        return switch (normalized) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp", "image/x-ms-bmp" -> "bmp";
            case "image/svg+xml" -> "svg";
            default -> normalized.startsWith("image/") ? normalizeExtension(normalized.substring("image/".length())) : null;
        };
    }

    @Nullable
    private String normalizeExtension(@Nullable String extension) {
        if (!StringUtils.hasText(extension)) {
            return null;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized;
    }

    private List<PendingAsset> toPendingAssets(List<MultipartFile> assets) {
        List<PendingAsset> pendingAssets = new ArrayList<>();
        for (MultipartFile asset : assets) {
            if (asset == null || asset.isEmpty()) {
                continue;
            }
            pendingAssets.add(new PendingAsset(
                    StringUtils.cleanPath(asset.getOriginalFilename() == null ? "asset.bin" : asset.getOriginalFilename()),
                    asset.getContentType(),
                    readBytesRaw(asset)
            ));
        }
        return pendingAssets;
    }

    private String readBytes(MultipartFile file) {
        return new String(readBytesRaw(file), StandardCharsets.UTF_8);
    }

    private byte[] readBytesRaw(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", "文件读取失败");
        }
    }

    private void ensureReviewPending(DocumentReviewRequest review) {
        if (review.getStatus() != DocumentReviewStatus.PENDING_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATUS_INVALID", "当前审核单状态不允许此操作");
        }
    }

    private DocumentReviewRequest approveReviewInternal(DocumentReviewRequest review, Long operatorId, @Nullable String reason) {
        ensureReviewPending(review);

        String categoryName = StringUtils.hasText(review.getSelectedCategoryName())
                ? review.getSelectedCategoryName().trim()
                : review.getSuggestedCategoryName();
        String columnName = normalizeOptionalName(review.getSelectedColumnName());
        List<String> tagNames = parseTagNames(review.getSelectedTagsJson());
        if (tagNames.isEmpty()) {
            tagNames = parseTagNames(review.getSuggestedTagsJson());
        }

        DocumentCategory category = ensureCategory(categoryName, DocumentTaxonomySource.MANUAL);
        DocumentColumn documentColumn = ensureColumn(columnName, DocumentTaxonomySource.MANUAL);
        List<DocumentTag> tags = ensureTags(tagNames, DocumentTaxonomySource.MANUAL);

        KnowledgeDocument published = review.getSourceDocument() == null
                ? new KnowledgeDocument()
                : review.getSourceDocument();
        published.setTitle(review.getTitle());
        published.setSourceFilename(review.getSourceFilename());
        published.setUploaderType(review.getUploaderType());
        published.setUploaderUserId(review.getUploaderUserId());
        published.setVisibilityType(review.getVisibilityType());
        published.setStatus(DocumentStatus.READY);
        published.setNormalizedMarkdownPath(review.getNormalizedMarkdownPath() == null ? "" : review.getNormalizedMarkdownPath());
        published.setSourceMarkdown(review.getSourceMarkdown());
        published.setExtensionJson(review.getExtensionJson());
        published.setVectorConfigJson(review.getVectorConfigJson());
        published.setCategory(category);
        published.setDocumentColumn(documentColumn);
        published.setTags(writeJson(tagNames));
        published = knowledgeDocumentRepository.save(published);
        final KnowledgeDocument publishedDocument = published;

        List<DocumentChunk> oldChunks = documentChunkRepository.findByDocument_IdOrderByChunkIndexAsc(publishedDocument.getId());
        knowledgeBaseIndexingService.delete(oldChunks);
        documentChunkRepository.deleteByDocument_Id(publishedDocument.getId());
        documentAssetRepository.deleteByDocument_Id(publishedDocument.getId());
        documentTagBindingRepository.deleteByDocument_Id(publishedDocument.getId());
        documentTagBindingRepository.flush();

        List<DocumentAsset> assets = documentReviewAssetRepository.findByReviewRequest_IdOrderByIdAsc(review.getId()).stream()
                .map(asset -> {
                    DocumentAsset created = new DocumentAsset();
                    created.setDocument(publishedDocument);
                    created.setOriginalPath(asset.getOriginalPath());
                    created.setStoredUrl(asset.getStoredUrl());
                    created.setAssetRole(asset.getAssetRole());
                    created.setProvider(asset.getProvider());
                    created.setObjectKey(asset.getObjectKey());
                    created.setContentType(asset.getContentType());
                    created.setContentLength(asset.getContentLength());
                    return created;
                })
                .toList();
        documentAssetRepository.saveAll(assets);

        List<DocumentChunk> chunks = documentReviewChunkRepository.findByReviewRequest_IdOrderByChunkIndexAsc(review.getId()).stream()
                .map(chunk -> {
                    DocumentChunk created = new DocumentChunk();
                    created.setDocument(publishedDocument);
                    created.setChunkIndex(chunk.getChunkIndex());
                    created.setHeadingPath(chunk.getHeadingPath());
                    created.setAnchor(chunk.getAnchor());
                    created.setContent(chunk.getContent());
                    created.setMetadataJson(chunk.getMetadataJson());
                    return created;
                })
                .toList();
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        knowledgeBaseIndexingService.index(savedChunks);

        List<DocumentTagBinding> bindings = tags.stream()
                .map(tag -> {
                    DocumentTagBinding binding = new DocumentTagBinding();
                    binding.setDocument(publishedDocument);
                    binding.setTag(tag);
                    return binding;
                })
                .toList();
        documentTagBindingRepository.saveAll(bindings);

        review.setPublishedDocument(publishedDocument);
        review.setStatus(DocumentReviewStatus.APPROVED);
        review.setStage("APPROVED");
        review.setProgressPercent(100);
        review.setReviewedByUserId(operatorId);
        review.setReviewedAt(OffsetDateTime.now());
        review.setReviewReason(reason);
        return documentReviewRequestRepository.save(review);
    }

    private List<Long> normalizeReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_IDS_REQUIRED", "请至少选择一条审核单");
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long reviewId : reviewIds) {
            if (reviewId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_ID_INVALID", "审核单 ID 不能为空");
            }
            normalized.add(reviewId);
        }
        return List.copyOf(normalized);
    }

    private DocumentReviewRequest loadReviewRequest(Long id) {
        return documentReviewRequestRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_REQUEST_NOT_FOUND", "审核请求不存在"));
    }

    private DocumentCategory ensureCategory(String name, DocumentTaxonomySource source) {
        String normalized = StringUtils.hasText(name) ? name.trim() : "未分类";
        return documentCategoryRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    DocumentCategory category = new DocumentCategory();
                    category.setName(normalized);
                    category.setSource(source);
                    return documentCategoryRepository.save(category);
                });
    }

    private DocumentColumn ensureColumn(@Nullable String name, DocumentTaxonomySource source) {
        String normalized = normalizeOptionalName(name);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return documentColumnRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    DocumentColumn column = new DocumentColumn();
                    column.setName(normalized);
                    column.setSource(source);
                    return documentColumnRepository.save(column);
                });
    }

    private String normalizeOptionalName(@Nullable String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<DocumentTag> ensureTags(List<String> names, DocumentTaxonomySource source) {
        Map<Long, DocumentTag> tagsById = new LinkedHashMap<>();
        for (String raw : normalizeTagNames(names)) {
            DocumentTag tag = documentTagRepository.findByNameIgnoreCase(raw)
                    .orElseGet(() -> {
                        DocumentTag created = new DocumentTag();
                        created.setName(raw);
                        created.setSource(source);
                        return documentTagRepository.save(created);
                    });
            tagsById.putIfAbsent(tag.getId(), tag);
        }
        return new ArrayList<>(tagsById.values());
    }

    private List<String> normalizeTagNames(@Nullable List<String> tags) {
        Map<String, String> uniqueByLowerCase = new LinkedHashMap<>();
        if (tags != null) {
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    String trimmed = tag.trim();
                    String key = trimmed.toLowerCase(Locale.ROOT);
                    uniqueByLowerCase.putIfAbsent(key, trimmed);
                }
            }
        }
        return uniqueByLowerCase.values().stream()
                .limit(Math.max(1, properties.getDocument().getTaxonomy().getMaxTags()))
                .toList();
    }

    private List<String> parseTagNames(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return normalizeTagNames(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private String normalizeJsonObject(@Nullable String json) {
        if (!StringUtils.hasText(json)) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                return "{}";
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private NormalizedImportExtension normalizeImportExtension(@Nullable String json, String sourceMarkdown) {
        String fallbackFingerprint = DigestUtils.md5DigestAsHex((sourceMarkdown == null ? "" : sourceMarkdown).getBytes(StandardCharsets.UTF_8));
        if (!StringUtils.hasText(json)) {
            return new NormalizedImportExtension("{}", null, fallbackFingerprint, false);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                return new NormalizedImportExtension("{}", null, fallbackFingerprint, false);
            }
            ObjectNode objectNode = (ObjectNode) node;
            String importKey = textValue(objectNode.get("importKey"));
            boolean importLike = StringUtils.hasText(importKey)
                    || objectNode.has("yuqueSource")
                    || StringUtils.hasText(textValue(objectNode.path("migration").path("tool")));
            String contentFingerprint = textValue(objectNode.get("contentFingerprint"));
            if (importLike && !StringUtils.hasText(contentFingerprint)) {
                contentFingerprint = fallbackFingerprint;
                objectNode.put("contentFingerprint", contentFingerprint);
            }
            return new NormalizedImportExtension(
                    objectMapper.writeValueAsString(objectNode),
                    importKey,
                    StringUtils.hasText(contentFingerprint) ? contentFingerprint : fallbackFingerprint,
                    importLike
            );
        } catch (Exception exception) {
            return new NormalizedImportExtension("{}", null, fallbackFingerprint, false);
        }
    }

    private void ensureImportReviewAllowed(NormalizedImportExtension extension) {
        if (!extension.importLike()) {
            return;
        }
        if (StringUtils.hasText(extension.importKey())
                && (documentReviewRequestRepository.existsByImportKey(extension.importKey())
                || knowledgeDocumentRepository.existsByImportKey(extension.importKey()))) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_IMPORT_DUPLICATE", "相同来源的导入文档已存在，无需重复导入");
        }
        if (documentReviewRequestRepository.existsActiveOrApprovedByContentFingerprint(extension.contentFingerprint())
                || knowledgeDocumentRepository.existsByContentFingerprint(extension.contentFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_IMPORT_DUPLICATE", "正文相同的导入文档已存在，无需重复导入");
        }
    }

    private String textValue(@Nullable JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private record NormalizedImportExtension(
            String json,
            @Nullable String importKey,
            String contentFingerprint,
            boolean importLike
    ) {
    }

    private static final class TagFacetAccumulator {
        private final Long id;
        private final String name;
        private long documentCount;

        private TagFacetAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private String name() {
            return name;
        }

        private void incrementDocumentCount() {
            documentCount += 1;
        }

        private PublicDocumentTagFacetView toView() {
            return new PublicDocumentTagFacetView(id, name, documentCount);
        }
    }

    private static final class CategoryFacetAccumulator {
        private final Long id;
        private final String name;
        private long documentCount;
        private final Map<Long, TagFacetAccumulator> tags = new LinkedHashMap<>();

        private CategoryFacetAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private String name() {
            return name;
        }

        private void incrementDocumentCount() {
            documentCount += 1;
        }

        private TagFacetAccumulator tag(DocumentTag tag) {
            return tags.computeIfAbsent(tag.getId(), ignored -> new TagFacetAccumulator(tag.getId(), tag.getName()));
        }

        private PublicDocumentCategoryFacetView toView() {
            List<PublicDocumentTagFacetView> tagViews = tags.values().stream()
                    .sorted(Comparator.comparing(TagFacetAccumulator::name, String.CASE_INSENSITIVE_ORDER))
                    .map(TagFacetAccumulator::toView)
                    .toList();
            return new PublicDocumentCategoryFacetView(id, name, documentCount, tagViews);
        }
    }

    private static final class PublicDocumentAggregateAccumulator {
        private final KnowledgeDocument document;
        private final LinkedHashMap<Long, DocumentTag> tags = new LinkedHashMap<>();

        private PublicDocumentAggregateAccumulator(KnowledgeDocument document) {
            this.document = document;
        }

        private void absorb(List<DocumentTagBinding> bindings) {
            for (DocumentTagBinding binding : bindings) {
                DocumentTag tag = binding.getTag();
                if (tag == null || tag.getId() == null) {
                    continue;
                }
                tags.putIfAbsent(tag.getId(), tag);
            }
        }

        private PublicDocumentAggregate toAggregate() {
            return new PublicDocumentAggregate(document, List.copyOf(tags.values()));
        }
    }

    private record PublicDocumentAggregate(
            KnowledgeDocument document,
            List<DocumentTag> tags
    ) {
        private List<String> tagNames() {
            return tags.stream().map(DocumentTag::getName).toList();
        }
    }

    private record PendingAsset(
            String originalFilename,
            String contentType,
            byte[] bytes
    ) {
        private MultipartFile toMultipartFile() {
            return new MultipartFile() {
                @Override
                public String getName() {
                    return originalFilename;
                }

                @Override
                public String getOriginalFilename() {
                    return originalFilename;
                }

                @Override
                public String getContentType() {
                    return contentType;
                }

                @Override
                public boolean isEmpty() {
                    return bytes.length == 0;
                }

                @Override
                public long getSize() {
                    return bytes.length;
                }

                @Override
                public byte[] getBytes() {
                    return bytes;
                }

                @Override
                public java.io.InputStream getInputStream() {
                    return new java.io.ByteArrayInputStream(bytes);
                }

                @Override
                public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                    java.nio.file.Files.write(dest.toPath(), bytes);
                }
            };
        }
    }

    public record PreparedPendingReviewRequest(
            String title,
            String sourceFilename,
            DocumentUploaderType uploaderType,
            Long uploaderUserId,
            DocumentVisibilityType visibilityType,
            String sourceMarkdown,
            String extensionJson,
            String selectedCategoryName,
            String selectedColumnName,
            List<String> selectedTags,
            String taxonomyReasoning,
            StoredReviewAsset sourceAsset
    ) {
    }

    public record StoredReviewAsset(
            String originalPath,
            String storedUrl,
            DocumentAssetRole assetRole,
            String provider,
            String objectKey,
            String contentType,
            Long contentLength
    ) {
    }
}
