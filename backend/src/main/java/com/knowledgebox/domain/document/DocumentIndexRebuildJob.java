package com.knowledgebox.domain.document;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_index_rebuild_job")
public class DocumentIndexRebuildJob extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String jobCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DocumentIndexRebuildStatus status = DocumentIndexRebuildStatus.RUNNING;

    @Column
    private Long triggeredByUserId;

    @Column(nullable = false, length = 128)
    private String sourceVectorTable;

    @Column(nullable = false, length = 128)
    private String targetVectorTable;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detailJson = "{}";

    @Column(nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column
    private OffsetDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public DocumentIndexRebuildStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentIndexRebuildStatus status) {
        this.status = status;
    }

    public Long getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public void setTriggeredByUserId(Long triggeredByUserId) {
        this.triggeredByUserId = triggeredByUserId;
    }

    public String getSourceVectorTable() {
        return sourceVectorTable;
    }

    public void setSourceVectorTable(String sourceVectorTable) {
        this.sourceVectorTable = sourceVectorTable;
    }

    public String getTargetVectorTable() {
        return targetVectorTable;
    }

    public void setTargetVectorTable(String targetVectorTable) {
        this.targetVectorTable = targetVectorTable;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
