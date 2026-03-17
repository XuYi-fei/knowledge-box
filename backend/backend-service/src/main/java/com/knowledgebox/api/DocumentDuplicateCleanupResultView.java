package com.knowledgebox.api;

public record DocumentDuplicateCleanupResultView(
        long duplicateDocumentsDeleted,
        long mergedTagBindings,
        long rewiredSourceReviews,
        long rewiredPublishedReviews,
        long rewiredIngestionJobs,
        long deletedTagBindings,
        long deletedAssets,
        long deletedChunks,
        long vectorRowsDeleted,
        long refreshedKeeperTags,
        long deletedDocuments,
        DocumentIndexRebuildJobView indexRebuildJob
) {
}
