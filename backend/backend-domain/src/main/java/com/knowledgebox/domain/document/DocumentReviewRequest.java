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
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_review_request")
public class DocumentReviewRequest extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private KnowledgeDocument sourceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_document_id")
    private KnowledgeDocument publishedDocument;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 256)
    private String sourceFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentUploaderType uploaderType = DocumentUploaderType.ADMIN;

    @Column
    private Long uploaderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentVisibilityType visibilityType = DocumentVisibilityType.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DocumentReviewStatus status = DocumentReviewStatus.CREATED;

    @Column(nullable = false, length = 64)
    private String stage = "CREATED";

    @Column(nullable = false)
    private Integer progressPercent = 0;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceMarkdown = "";

    @Column(length = 512)
    private String normalizedMarkdownPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String extensionJson = "{}";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String vectorConfigJson = "{}";

    @Column(length = 128)
    private String suggestedCategoryName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String suggestedTagsJson = "[]";

    @Column(length = 128)
    private String selectedCategoryName;

    @Column(length = 128)
    private String selectedColumnName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String selectedTagsJson = "[]";

    @Column(columnDefinition = "TEXT")
    private String taxonomyReasoning;

    @Column(columnDefinition = "TEXT")
    private String reviewReason;

    @Column
    private Long reviewedByUserId;

    @Column
    private OffsetDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    public KnowledgeDocument getSourceDocument() {
        return sourceDocument;
    }

    public void setSourceDocument(KnowledgeDocument sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    public KnowledgeDocument getPublishedDocument() {
        return publishedDocument;
    }

    public void setPublishedDocument(KnowledgeDocument publishedDocument) {
        this.publishedDocument = publishedDocument;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public DocumentUploaderType getUploaderType() {
        return uploaderType;
    }

    public void setUploaderType(DocumentUploaderType uploaderType) {
        this.uploaderType = uploaderType;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public DocumentVisibilityType getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(DocumentVisibilityType visibilityType) {
        this.visibilityType = visibilityType;
    }

    public DocumentReviewStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentReviewStatus status) {
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

    public String getSourceMarkdown() {
        return sourceMarkdown;
    }

    public void setSourceMarkdown(String sourceMarkdown) {
        this.sourceMarkdown = sourceMarkdown;
    }

    public String getNormalizedMarkdownPath() {
        return normalizedMarkdownPath;
    }

    public void setNormalizedMarkdownPath(String normalizedMarkdownPath) {
        this.normalizedMarkdownPath = normalizedMarkdownPath;
    }

    public String getExtensionJson() {
        return extensionJson;
    }

    public void setExtensionJson(String extensionJson) {
        this.extensionJson = extensionJson;
    }

    public String getVectorConfigJson() {
        return vectorConfigJson;
    }

    public void setVectorConfigJson(String vectorConfigJson) {
        this.vectorConfigJson = vectorConfigJson;
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

    public String getSelectedCategoryName() {
        return selectedCategoryName;
    }

    public void setSelectedCategoryName(String selectedCategoryName) {
        this.selectedCategoryName = selectedCategoryName;
    }

    public String getSelectedColumnName() {
        return selectedColumnName;
    }

    public void setSelectedColumnName(String selectedColumnName) {
        this.selectedColumnName = selectedColumnName;
    }

    public String getSelectedTagsJson() {
        return selectedTagsJson;
    }

    public void setSelectedTagsJson(String selectedTagsJson) {
        this.selectedTagsJson = selectedTagsJson;
    }

    public String getTaxonomyReasoning() {
        return taxonomyReasoning;
    }

    public void setTaxonomyReasoning(String taxonomyReasoning) {
        this.taxonomyReasoning = taxonomyReasoning;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
