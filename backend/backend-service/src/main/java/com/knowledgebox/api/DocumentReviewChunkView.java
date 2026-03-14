package com.knowledgebox.api;

public record DocumentReviewChunkView(
        Long id,
        Integer chunkIndex,
        String headingPath,
        String anchor,
        String content,
        String metadataJson
) {
}
