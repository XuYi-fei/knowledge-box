package com.knowledgebox.api;

public record ChatCitationView(
        Long documentId,
        String documentTitle,
        String headingPath,
        String anchor,
        String snippet
) {
}

