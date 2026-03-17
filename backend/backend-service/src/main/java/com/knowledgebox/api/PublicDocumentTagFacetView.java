package com.knowledgebox.api;

public record PublicDocumentTagFacetView(
        Long id,
        String name,
        long documentCount
) {
}
