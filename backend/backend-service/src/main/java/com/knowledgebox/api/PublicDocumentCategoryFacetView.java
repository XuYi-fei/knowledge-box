package com.knowledgebox.api;

import java.util.List;

public record PublicDocumentCategoryFacetView(
        Long id,
        String name,
        long documentCount,
        List<PublicDocumentTagFacetView> tags
) {
}
