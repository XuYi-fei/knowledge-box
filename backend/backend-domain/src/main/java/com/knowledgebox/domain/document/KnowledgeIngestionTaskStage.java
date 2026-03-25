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
@Table(name = "knowledge_ingestion_task_stage")
public class KnowledgeIngestionTaskStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private KnowledgeIngestionTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_code", nullable = false, length = 32)
    private KnowledgeIngestionTaskStageCode stageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeIngestionTaskStageStatus status = KnowledgeIngestionTaskStageStatus.PENDING;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    public KnowledgeIngestionTask getTask() {
        return task;
    }

    public void setTask(KnowledgeIngestionTask task) {
        this.task = task;
    }

    public KnowledgeIngestionTaskStageCode getStageCode() {
        return stageCode;
    }

    public void setStageCode(KnowledgeIngestionTaskStageCode stageCode) {
        this.stageCode = stageCode;
    }

    public KnowledgeIngestionTaskStageStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeIngestionTaskStageStatus status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
