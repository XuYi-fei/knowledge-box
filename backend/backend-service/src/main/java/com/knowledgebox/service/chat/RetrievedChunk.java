package com.knowledgebox.service.chat;

public record RetrievedChunk(
        Long documentId,
        String documentTitle,
        String headingPath,
        String anchor,
        String snippet,
        double score,
        boolean publicVisible
) {
}
