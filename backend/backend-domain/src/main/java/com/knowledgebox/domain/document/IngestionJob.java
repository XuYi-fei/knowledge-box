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
@Table(name = "ingestion_job")
public class IngestionJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IngestionJobStatus status = IngestionJobStatus.PENDING;

    @Column(nullable = false, length = 64)
    private String jobType = "MARKDOWN_INGESTION";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail = "{}";

    public KnowledgeDocument getDocument() {
        return document;
    }

    public void setDocument(KnowledgeDocument document) {
        this.document = document;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionJobStatus status) {
        this.status = status;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}

