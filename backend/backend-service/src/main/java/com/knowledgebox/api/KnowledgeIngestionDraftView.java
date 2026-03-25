package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftStatus;
import java.time.OffsetDateTime;

public record KnowledgeIngestionDraftView(
        Long id,
        String draftCode,
        KnowledgeIngestionDraftSourceType sourceType,
        String sourceFilename,
        String sourceFileUrl,
        String sourceFileContentType,
        Long sourceFileContentLength,
        KnowledgeIngestionDraftStatus status,
        String stage,
        Integer progressPercent,
        String generatedMarkdown,
        String summaryText,
        String suggestedTitle,
        String suggestedCategoryName,
        String suggestedTagsJson,
        String analysisReasoning,
        String errorMessage,
        Long confirmedReviewRequestId,
        String confirmedReviewRequestCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
