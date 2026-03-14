package com.knowledgebox.api;

public record DocumentReviewAssetView(
        Long id,
        String originalPath,
        String storedUrl,
        String provider,
        String objectKey,
        String contentType,
        Long contentLength
) {
}
