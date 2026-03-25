package com.knowledgebox.api;

public record KnowledgeIngestionUploadResultView(
        KnowledgeIngestionUploadMode mode,
        KnowledgeIngestionDraftView draft,
        KnowledgeIngestionTaskSummaryView task,
        String message,
        boolean reusedExistingTask
) {
}
