package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskStatus;
import java.time.OffsetDateTime;

public record KnowledgeIngestionTaskSummaryView(
        Long id,
        String taskCode,
        String sourceFilename,
        String sourceFileUrl,
        String sourceFileContentType,
        Long sourceFileContentLength,
        Integer sourcePageCount,
        KnowledgeIngestionTaskStatus status,
        String stage,
        Integer progressPercent,
        Boolean cancelRequested,
        Integer plannedDocumentCount,
        Integer generatedDocumentCount,
        Integer failedDocumentCount,
        Integer cancelledDocumentCount,
        String summaryText,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
