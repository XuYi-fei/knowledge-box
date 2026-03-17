package com.knowledgebox.api;

public record DocumentDuplicateCleanupItemView(
        Long keepDocumentId,
        String keepSourceFilename,
        String keepImportKey,
        Long duplicateDocumentId,
        String duplicateSourceFilename,
        String duplicateImportKey,
        String categoryName,
        String title,
        String contentFingerprint,
        long chunkCount,
        long assetCount,
        long tagCount,
        long sourceReviewRefCount,
        long publishedReviewRefCount,
        long ingestionRefCount
) {
}
