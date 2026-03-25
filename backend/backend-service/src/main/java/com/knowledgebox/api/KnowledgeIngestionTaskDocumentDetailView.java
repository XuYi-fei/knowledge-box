package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocumentStatus;
import java.time.OffsetDateTime;

public record KnowledgeIngestionTaskDocumentDetailView(
        Long id,
        String documentCode,
        Integer segmentIndex,
        Integer pageFromNumber,
        Integer pageToNumber,
        KnowledgeIngestionTaskDocumentStatus status,
        String suggestedTitle,
        String suggestedCategoryName,
        String suggestedTagsJson,
        String summaryText,
        String analysisReasoning,
        String generatedMarkdown,
        String errorMessage,
        Long reviewRequestId,
        String reviewRequestCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
