package com.knowledgebox.api;

public record DocumentPastedImageUploadView(
        String md5,
        String provider,
        String objectKey,
        String url,
        String contentType,
        Long contentLength
) {
}
