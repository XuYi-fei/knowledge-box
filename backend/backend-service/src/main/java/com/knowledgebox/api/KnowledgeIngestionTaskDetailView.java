package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeIngestionTaskDetailView(
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
        List<KnowledgeIngestionTaskStageView> stages,
        List<KnowledgeIngestionTaskDocumentSummaryView> documents,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
