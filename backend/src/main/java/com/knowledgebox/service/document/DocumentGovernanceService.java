package com.knowledgebox.service.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.CreateDocumentReviewRequest;
import com.knowledgebox.api.DocumentAssetView;
import com.knowledgebox.api.DocumentCategoryView;
import com.knowledgebox.api.DocumentIndexRebuildJobView;
import com.knowledgebox.api.DocumentPastedImageUploadView;
import com.knowledgebox.api.DocumentReviewAssetView;
import com.knowledgebox.api.DocumentReviewChunkView;
import com.knowledgebox.api.DocumentReviewRequestDetailView;
import com.knowledgebox.api.DocumentReviewRequestPageView;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.api.DocumentTagView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.UpdateDocumentSourceRequest;
import com.knowledgebox.api.UpdateReviewTaxonomyRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentAsset;
import com.knowledgebox.domain.document.DocumentCategory;
import com.knowledgebox.domain.document.DocumentChunk;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final KnowledgeBoxProperties properties;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentAssetRepository documentAssetRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentCategoryRepository documentCategoryRepository;
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
    public List<DocumentCategoryView> categories() {
        return documentCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new DocumentCategoryView(category.getId(), category.getName(), category.getSource()))
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
        review.setExtensionJson(normalizeJsonObject(extensionJson));
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
        review.setExtensionJson(normalizeJsonObject(request.extensionJson()));
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
        review.setSelectedTagsJson(writeJson(normalizeTagNames(request.tags())));
        return toReviewSummaryView(documentReviewRequestRepository.save(review));
    }

    @Transactional
    public DocumentReviewRequestSummaryView approveReview(Long reviewId, Long operatorId, @Nullable String reason) {
        DocumentReviewRequest review = loadReviewRequest(reviewId);
        ensureReviewPending(review);

        String categoryName = StringUtils.hasText(review.getSelectedCategoryName())
                ? review.getSelectedCategoryName().trim()
                : review.getSuggestedCategoryName();
        List<String> tagNames = parseTagNames(review.getSelectedTagsJson());
        if (tagNames.isEmpty()) {
            tagNames = parseTagNames(review.getSuggestedTagsJson());
        }

        DocumentCategory category = ensureCategory(categoryName, DocumentTaxonomySource.MANUAL);
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
        published.setTags(writeJson(tagNames));
        published = knowledgeDocumentRepository.save(published);
        final KnowledgeDocument publishedDocument = published;

        List<DocumentChunk> oldChunks = documentChunkRepository.findByDocument_IdOrderByChunkIndexAsc(publishedDocument.getId());
        knowledgeBaseIndexingService.delete(oldChunks);
        documentChunkRepository.deleteByDocument_Id(publishedDocument.getId());
        documentAssetRepository.deleteByDocument_Id(publishedDocument.getId());
        documentTagBindingRepository.deleteByDocument_Id(publishedDocument.getId());
        documentTagBindingRepository.flush();

        List<DocumentAsset> assets = documentReviewAssetRepository.findByReviewRequest_IdOrderByIdAsc(reviewId).stream()
                .map(asset -> {
                    DocumentAsset created = new DocumentAsset();
                    created.setDocument(publishedDocument);
                    created.setOriginalPath(asset.getOriginalPath());
                    created.setStoredUrl(asset.getStoredUrl());
                    created.setProvider(asset.getProvider());
                    created.setObjectKey(asset.getObjectKey());
                    created.setContentType(asset.getContentType());
                    created.setContentLength(asset.getContentLength());
                    return created;
                })
                .toList();
        documentAssetRepository.saveAll(assets);

        List<DocumentChunk> chunks = documentReviewChunkRepository.findByReviewRequest_IdOrderByChunkIndexAsc(reviewId).stream()
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
        return toReviewSummaryView(documentReviewRequestRepository.save(review));
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
            review.setSelectedCategoryName(taxonomySuggestion.categoryName());
            review.setSelectedTagsJson(writeJson(normalizeTagNames(taxonomySuggestion.tagNames())));
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
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            List<String> tags = documentTagBindingRepository.findByDocument_IdOrderByIdAsc(document.getId()).stream()
                    .map(binding -> binding.getTag().getName())
                    .toList();
            map.put(document.getId(), tags);
        }
        return map;
    }

    private KnowledgeDocumentView toDocumentView(KnowledgeDocument document, List<String> tags) {
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
                writeJson(tags),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
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
}
