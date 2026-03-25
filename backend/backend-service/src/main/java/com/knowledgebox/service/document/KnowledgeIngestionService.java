package com.knowledgebox.service.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ConfirmKnowledgeIngestionDraftRequest;
import com.knowledgebox.api.CreateKnowledgeIngestionInlineDraftRequest;
import com.knowledgebox.api.KnowledgeIngestionDraftView;
import com.knowledgebox.api.KnowledgeIngestionOptionsView;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.document.DocumentAssetRole;
import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftStatus;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeIngestionDraftRepository;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeIngestionService {

    private static final String DEFAULT_INLINE_FILENAME = "inline-content.md";

    private final KnowledgeIngestionDraftRepository draftRepository;
    private final DocumentReviewRequestRepository documentReviewRequestRepository;
    private final DocumentGovernanceService documentGovernanceService;
    private final KnowledgeIngestionAgentService ingestionAgentService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionService(
            KnowledgeIngestionDraftRepository draftRepository,
            DocumentReviewRequestRepository documentReviewRequestRepository,
            DocumentGovernanceService documentGovernanceService,
            KnowledgeIngestionAgentService ingestionAgentService,
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.documentReviewRequestRepository = documentReviewRequestRepository;
        this.documentGovernanceService = documentGovernanceService;
        this.ingestionAgentService = ingestionAgentService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public KnowledgeIngestionOptionsView options() {
        return new KnowledgeIngestionOptionsView(
                documentGovernanceService.categories(),
                documentGovernanceService.columns(),
                documentGovernanceService.tags()
        );
    }

    @Transactional
    public KnowledgeIngestionDraftView createUploadDraft(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_FILE_REQUIRED", "请上传 Markdown 或 PDF 文件");
        }
        String sourceFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        KnowledgeIngestionDraftSourceType sourceType = resolveSourceType(sourceFilename, file.getContentType());
        byte[] bytes = readBytes(file);
        String sourceContent = sourceType == KnowledgeIngestionDraftSourceType.PDF
                ? extractPdfText(bytes, sourceFilename)
                : new String(bytes, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(sourceContent)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_SOURCE_EMPTY", "上传内容为空，无法生成知识文档");
        }
        StorageService.StoredObject stored = storageService.store("knowledge-ingestion-source", file);

        KnowledgeIngestionDraft draft = new KnowledgeIngestionDraft();
        draft.setDraftCode(UUID.randomUUID().toString());
        draft.setUserId(userId);
        draft.setSourceType(sourceType);
        draft.setSourceFilename(sourceFilename);
        draft.setSourceContent(sourceContent);
        draft.setSourceFileProvider(stored.provider());
        draft.setSourceFileObjectKey(stored.objectKey());
        draft.setSourceFileUrl(stored.url());
        draft.setSourceFileContentType(stored.contentType());
        draft.setSourceFileContentLength(stored.contentLength());
        draft.setStatus(KnowledgeIngestionDraftStatus.CREATED);
        draft.setStage("CREATED");
        draft.setProgressPercent(0);
        draft.setSuggestedTagsJson("[]");
        draft = draftRepository.save(draft);

        startAnalysisPipeline(draft.getId());
        return toView(draft);
    }

    @Transactional
    public KnowledgeIngestionDraftView createInlineDraft(CreateKnowledgeIngestionInlineDraftRequest request, Long userId) {
        String content = request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_CONTENT_REQUIRED", "请输入要整理的正文内容");
        }
        KnowledgeIngestionDraft draft = new KnowledgeIngestionDraft();
        draft.setDraftCode(UUID.randomUUID().toString());
        draft.setUserId(userId);
        draft.setSourceType(KnowledgeIngestionDraftSourceType.INLINE);
        draft.setSourceFilename(StringUtils.hasText(request.sourceFilename()) ? request.sourceFilename().trim() : DEFAULT_INLINE_FILENAME);
        draft.setSourceContent(content);
        draft.setStatus(KnowledgeIngestionDraftStatus.CREATED);
        draft.setStage("CREATED");
        draft.setProgressPercent(0);
        draft.setSuggestedTagsJson("[]");
        draft = draftRepository.save(draft);

        startAnalysisPipeline(draft.getId());
        return toView(draft);
    }

    @Transactional(readOnly = true)
    public KnowledgeIngestionDraftView draftDetail(Long draftId, Long userId) {
        return toView(loadOwnedDraft(draftId, userId));
    }

    @Transactional
    public KnowledgeIngestionDraftView confirmDraft(Long draftId, ConfirmKnowledgeIngestionDraftRequest request, Long userId) {
        KnowledgeIngestionDraft draft = loadOwnedDraft(draftId, userId);
        if (draft.getStatus() != KnowledgeIngestionDraftStatus.AWAITING_CONFIRMATION) {
            throw new ApiException(HttpStatus.CONFLICT, "INGESTION_DRAFT_STATUS_INVALID", "当前草稿尚未完成分析或已被确认");
        }

        String title = normalizeTitle(request.title(), draft);
        String categoryName = StringUtils.hasText(request.categoryName()) ? request.categoryName().trim() : normalizeOptional(draft.getSuggestedCategoryName());
        List<String> tags = normalizeTags(request.tags());
        if (tags.isEmpty()) {
            tags = normalizeTags(parseTagsJson(draft.getSuggestedTagsJson()));
        }

        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("ingestionDraftId", draft.getId());
        extension.put("ingestionDraftCode", draft.getDraftCode());
        extension.put("sourceType", draft.getSourceType().name());
        extension.put("sourceFilename", draft.getSourceFilename());
        if (StringUtils.hasText(draft.getSourceFileObjectKey())) {
            extension.put("sourceFileObjectKey", draft.getSourceFileObjectKey());
        }
        if (StringUtils.hasText(draft.getSourceFileUrl())) {
            extension.put("sourceFileUrl", draft.getSourceFileUrl());
        }

        DocumentGovernanceService.StoredReviewAsset sourceAsset = null;
        if (StringUtils.hasText(draft.getSourceFileUrl())) {
            sourceAsset = new DocumentGovernanceService.StoredReviewAsset(
                    draft.getSourceFilename(),
                    draft.getSourceFileUrl(),
                    DocumentAssetRole.SOURCE_FILE,
                    draft.getSourceFileProvider(),
                    draft.getSourceFileObjectKey(),
                    draft.getSourceFileContentType(),
                    draft.getSourceFileContentLength()
            );
        }

        DocumentReviewRequestSummaryView reviewSummary = documentGovernanceService.createPreparedPendingReview(
                new DocumentGovernanceService.PreparedPendingReviewRequest(
                        title,
                        draft.getSourceFilename(),
                        DocumentUploaderType.USER,
                        userId,
                        DocumentVisibilityType.PUBLIC,
                        draft.getGeneratedMarkdown(),
                        writeJson(extension),
                        categoryName,
                        normalizeOptional(request.columnName()),
                        tags,
                        draft.getAnalysisReasoning(),
                        sourceAsset
                )
        );
        DocumentReviewRequest review = documentReviewRequestRepository.findById(reviewSummary.id())
                .orElseThrow(() -> new IllegalStateException("Prepared review request was not created for draft " + draftId));

        draft.setStatus(KnowledgeIngestionDraftStatus.CONFIRMED);
        draft.setStage("CONFIRMED");
        draft.setProgressPercent(100);
        draft.setConfirmedReviewRequest(review);
        draft.setErrorMessage(null);
        draftRepository.save(draft);
        return toView(draft);
    }

    @Transactional
    protected void processDraftAnalysis(Long draftId) {
        KnowledgeIngestionDraft draft = draftRepository.findById(draftId).orElse(null);
        if (draft == null) {
            return;
        }
        try {
            draft.setStatus(KnowledgeIngestionDraftStatus.PROCESSING);
            draft.setStage("ANALYZING");
            draft.setProgressPercent(20);
            draft.setErrorMessage(null);
            draftRepository.save(draft);

            KnowledgeIngestionAgentService.DraftAnalysisResult analysis = ingestionAgentService.analyze(draft);
            draft.setSuggestedTitle(analysis.title());
            draft.setSuggestedCategoryName(analysis.categoryName());
            draft.setSuggestedTagsJson(writeJson(analysis.tagNames()));
            draft.setSummaryText(analysis.summary());
            draft.setAnalysisReasoning(analysis.reasoning());
            draft.setGeneratedMarkdown(analysis.markdown());
            draft.setStatus(KnowledgeIngestionDraftStatus.AWAITING_CONFIRMATION);
            draft.setStage("AWAITING_CONFIRMATION");
            draft.setProgressPercent(100);
            draft.setErrorMessage(null);
            draftRepository.save(draft);
        } catch (Exception exception) {
            draft.setStatus(KnowledgeIngestionDraftStatus.FAILED);
            draft.setStage("FAILED");
            draft.setProgressPercent(100);
            draft.setErrorMessage(resolveRootCauseMessage(exception));
            draftRepository.save(draft);
        }
    }

    private void startAnalysisPipeline(Long draftId) {
        Runnable startTask = () -> Thread.ofVirtual()
                .name("kb-ingestion-draft-" + draftId)
                .start(() -> processDraftAnalysis(draftId));
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

    private KnowledgeIngestionDraft loadOwnedDraft(Long draftId, Long userId) {
        return draftRepository.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INGESTION_DRAFT_NOT_FOUND", "草稿不存在或无权访问"));
    }

    private KnowledgeIngestionDraftView toView(KnowledgeIngestionDraft draft) {
        DocumentReviewRequest confirmedReview = draft.getConfirmedReviewRequest();
        return new KnowledgeIngestionDraftView(
                draft.getId(),
                draft.getDraftCode(),
                draft.getSourceType(),
                draft.getSourceFilename(),
                draft.getSourceFileUrl(),
                draft.getSourceFileContentType(),
                draft.getSourceFileContentLength(),
                draft.getStatus(),
                draft.getStage(),
                draft.getProgressPercent(),
                draft.getGeneratedMarkdown(),
                draft.getSummaryText(),
                draft.getSuggestedTitle(),
                draft.getSuggestedCategoryName(),
                draft.getSuggestedTagsJson(),
                draft.getAnalysisReasoning(),
                draft.getErrorMessage(),
                confirmedReview == null ? null : confirmedReview.getId(),
                confirmedReview == null ? null : confirmedReview.getRequestCode(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private KnowledgeIngestionDraftSourceType resolveSourceType(String filename, @Nullable String contentType) {
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown")) {
            return KnowledgeIngestionDraftSourceType.MARKDOWN;
        }
        if (lowerFilename.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType)) {
            return KnowledgeIngestionDraftSourceType.PDF;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_FILE_TYPE_UNSUPPORTED", "当前仅支持上传 Markdown 或文本型 PDF");
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_FILE_READ_FAILED", "无法读取上传文件");
        }
    }

    private String extractPdfText(byte[] bytes, String filename) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_EMPTY", "PDF 未提取到文本，当前仅支持文本型 PDF");
            }
            return text.trim();
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_PARSE_FAILED", "无法解析 PDF 文件: " + filename);
        }
    }

    private String normalizeTitle(@Nullable String title, KnowledgeIngestionDraft draft) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (StringUtils.hasText(draft.getSuggestedTitle())) {
            return draft.getSuggestedTitle().trim();
        }
        String fallback = StringUtils.hasText(draft.getSourceFilename()) ? draft.getSourceFilename().trim() : DEFAULT_INLINE_FILENAME;
        return fallback.replaceAll("\\.(md|markdown|pdf|txt)$", "");
    }

    private String normalizeOptional(@Nullable String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeTags(@Nullable List<String> tags) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (tags != null) {
            for (String tag : tags) {
                if (!StringUtils.hasText(tag)) {
                    continue;
                }
                String trimmed = tag.trim();
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return unique.values().stream().limit(5).toList();
    }

    private List<String> parseTagsJson(@Nullable String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize ingestion metadata", exception);
        }
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : throwable.toString();
    }
}
