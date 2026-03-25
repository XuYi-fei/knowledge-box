package com.knowledgebox.domain.document;

public enum KnowledgeIngestionTaskStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
