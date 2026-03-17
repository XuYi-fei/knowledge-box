package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentReviewStatus;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import java.time.OffsetDateTime;

public record DocumentReviewRequestSummaryView(
        Long id,
        String requestCode,
        Long sourceDocumentId,
        Long publishedDocumentId,
        String title,
        String sourceFilename,
        DocumentUploaderType uploaderType,
        Long uploaderUserId,
        DocumentVisibilityType visibilityType,
        DocumentReviewStatus status,
        String stage,
        Integer progressPercent,
        String suggestedCategoryName,
        String suggestedTagsJson,
        String selectedCategoryName,
        String selectedColumnName,
        String selectedTagsJson,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
