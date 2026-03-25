package com.knowledgebox.domain.document;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_ingestion_task")
public class KnowledgeIngestionTask extends BaseEntity {

    @Column(name = "task_code", nullable = false, unique = true, length = 64)
    private String taskCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_filename", nullable = false, length = 256)
    private String sourceFilename;

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

    @Column(name = "source_file_content_hash", nullable = false, length = 64)
    private String sourceFileContentHash;

    @Column(name = "source_page_count", nullable = false)
    private Integer sourcePageCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeIngestionTaskStatus status = KnowledgeIngestionTaskStatus.QUEUED;

    @Column(nullable = false, length = 64)
    private String stage = KnowledgeIngestionTaskStageCode.UPLOAD_STORED.name();

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "cancel_requested", nullable = false)
    private Boolean cancelRequested = Boolean.FALSE;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "planned_document_count", nullable = false)
    private Integer plannedDocumentCount = 0;

    @Column(name = "generated_document_count", nullable = false)
    private Integer generatedDocumentCount = 0;

    @Column(name = "failed_document_count", nullable = false)
    private Integer failedDocumentCount = 0;

    @Column(name = "cancelled_document_count", nullable = false)
    private Integer cancelledDocumentCount = 0;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
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

    public String getSourceFileContentHash() {
        return sourceFileContentHash;
    }

    public void setSourceFileContentHash(String sourceFileContentHash) {
        this.sourceFileContentHash = sourceFileContentHash;
    }

    public Integer getSourcePageCount() {
        return sourcePageCount;
    }

    public void setSourcePageCount(Integer sourcePageCount) {
        this.sourcePageCount = sourcePageCount;
    }

    public KnowledgeIngestionTaskStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeIngestionTaskStatus status) {
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

    public Boolean getCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(Boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getPlannedDocumentCount() {
        return plannedDocumentCount;
    }

    public void setPlannedDocumentCount(Integer plannedDocumentCount) {
        this.plannedDocumentCount = plannedDocumentCount;
    }

    public Integer getGeneratedDocumentCount() {
        return generatedDocumentCount;
    }

    public void setGeneratedDocumentCount(Integer generatedDocumentCount) {
        this.generatedDocumentCount = generatedDocumentCount;
    }

    public Integer getFailedDocumentCount() {
        return failedDocumentCount;
    }

    public void setFailedDocumentCount(Integer failedDocumentCount) {
        this.failedDocumentCount = failedDocumentCount;
    }

    public Integer getCancelledDocumentCount() {
        return cancelledDocumentCount;
    }

    public void setCancelledDocumentCount(Integer cancelledDocumentCount) {
        this.cancelledDocumentCount = cancelledDocumentCount;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }
}
