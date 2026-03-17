package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentReviewStatus;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import java.time.OffsetDateTime;
import java.util.List;

public record DocumentReviewRequestDetailView(
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
        String sourceMarkdown,
        String normalizedMarkdownPath,
        String extensionJson,
        String vectorConfigJson,
        String suggestedCategoryName,
        String suggestedTagsJson,
        String selectedCategoryName,
        String selectedColumnName,
        String selectedTagsJson,
        String taxonomyReasoning,
        String reviewReason,
        Long reviewedByUserId,
        OffsetDateTime reviewedAt,
        String errorMessage,
        List<DocumentReviewAssetView> assets,
        List<DocumentReviewChunkView> chunks,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
