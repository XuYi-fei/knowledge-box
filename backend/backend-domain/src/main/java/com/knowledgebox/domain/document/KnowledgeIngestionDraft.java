package com.knowledgebox.domain.document;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_ingestion_draft")
public class KnowledgeIngestionDraft extends BaseEntity {

    @Column(name = "draft_code", nullable = false, unique = true, length = 64)
    private String draftCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private KnowledgeIngestionDraftSourceType sourceType = KnowledgeIngestionDraftSourceType.INLINE;

    @Column(name = "source_filename", length = 256)
    private String sourceFilename;

    @Column(name = "source_content", nullable = false, columnDefinition = "TEXT")
    private String sourceContent = "";

    @Column(name = "source_file_provider", length = 32)
    private String sourceFileProvider;

    @Column(name = "source_file_object_key", length = 512)
    private String sourceFileObjectKey;

    @Column(name = "source_file_url", length = 512)
    private String sourceFileUrl;

    @Column(name = "source_file_content_type", length = 128)
    private String sourceFileContentType;

    @Column(name = "source_file_content_length")
    private Long sourceFileContentLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeIngestionDraftStatus status = KnowledgeIngestionDraftStatus.CREATED;

    @Column(nullable = false, length = 64)
    private String stage = "CREATED";

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "generated_markdown", columnDefinition = "TEXT")
    private String generatedMarkdown;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "suggested_title", length = 128)
    private String suggestedTitle;

    @Column(name = "suggested_category_name", length = 128)
    private String suggestedCategoryName;

    @Column(name = "suggested_tags_json", nullable = false, columnDefinition = "TEXT")
    private String suggestedTagsJson = "[]";

    @Column(name = "analysis_reasoning", columnDefinition = "TEXT")
    private String analysisReasoning;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_review_request_id")
    private DocumentReviewRequest confirmedReviewRequest;

    public String getDraftCode() {
        return draftCode;
    }

    public void setDraftCode(String draftCode) {
        this.draftCode = draftCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public KnowledgeIngestionDraftSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(KnowledgeIngestionDraftSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public void setSourceContent(String sourceContent) {
        this.sourceContent = sourceContent;
    }

    public String getSourceFileProvider() {
        return sourceFileProvider;
    }

    public void setSourceFileProvider(String sourceFileProvider) {
        this.sourceFileProvider = sourceFileProvider;
    }

    public String getSourceFileObjectKey() {
        return sourceFileObjectKey;
    }

    public void setSourceFileObjectKey(String sourceFileObjectKey) {
        this.sourceFileObjectKey = sourceFileObjectKey;
    }

    public String getSourceFileUrl() {
        return sourceFileUrl;
    }

    public void setSourceFileUrl(String sourceFileUrl) {
        this.sourceFileUrl = sourceFileUrl;
    }

    public String getSourceFileContentType() {
        return sourceFileContentType;
    }

    public void setSourceFileContentType(String sourceFileContentType) {
        this.sourceFileContentType = sourceFileContentType;
    }

    public Long getSourceFileContentLength() {
        return sourceFileContentLength;
    }

    public void setSourceFileContentLength(Long sourceFileContentLength) {
        this.sourceFileContentLength = sourceFileContentLength;
    }

    public KnowledgeIngestionDraftStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeIngestionDraftStatus status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getGeneratedMarkdown() {
        return generatedMarkdown;
    }

    public void setGeneratedMarkdown(String generatedMarkdown) {
        this.generatedMarkdown = generatedMarkdown;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getSuggestedTitle() {
        return suggestedTitle;
    }

    public void setSuggestedTitle(String suggestedTitle) {
        this.suggestedTitle = suggestedTitle;
    }

    public String getSuggestedCategoryName() {
        return suggestedCategoryName;
    }

    public void setSuggestedCategoryName(String suggestedCategoryName) {
        this.suggestedCategoryName = suggestedCategoryName;
    }

    public String getSuggestedTagsJson() {
        return suggestedTagsJson;
    }

    public void setSuggestedTagsJson(String suggestedTagsJson) {
        this.suggestedTagsJson = suggestedTagsJson;
    }

    public String getAnalysisReasoning() {
        return analysisReasoning;
    }

    public void setAnalysisReasoning(String analysisReasoning) {
        this.analysisReasoning = analysisReasoning;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public DocumentReviewRequest getConfirmedReviewRequest() {
        return confirmedReviewRequest;
    }

    public void setConfirmedReviewRequest(DocumentReviewRequest confirmedReviewRequest) {
        this.confirmedReviewRequest = confirmedReviewRequest;
    }
}
