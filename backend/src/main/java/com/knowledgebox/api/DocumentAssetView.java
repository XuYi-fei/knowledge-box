package com.knowledgebox.api;

public record DocumentAssetView(
        Long id,
        String originalPath,
        String storedUrl,
        String provider,
        String objectKey,
        String contentType,
        Long contentLength
) {
}
