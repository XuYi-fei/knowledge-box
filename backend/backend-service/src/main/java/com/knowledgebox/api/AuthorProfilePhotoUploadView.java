package com.knowledgebox.api;

public record AuthorProfilePhotoUploadView(
        String provider,
        String objectKey,
        String url,
        String contentType,
        Long contentLength
) {
}
