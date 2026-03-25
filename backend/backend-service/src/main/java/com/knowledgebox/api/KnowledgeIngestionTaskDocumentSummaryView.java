package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocumentStatus;
import java.time.OffsetDateTime;

public record KnowledgeIngestionTaskDocumentSummaryView(
        Long id,
        String documentCode,
        Integer segmentIndex,
        Integer pageFromNumber,
        Integer pageToNumber,
        KnowledgeIngestionTaskDocumentStatus status,
        String suggestedTitle,
        String suggestedCategoryName,
        String summaryText,
        String errorMessage,
        Long reviewRequestId,
        String reviewRequestCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
