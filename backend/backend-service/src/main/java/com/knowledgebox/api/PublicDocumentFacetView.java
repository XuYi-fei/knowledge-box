package com.knowledgebox.api;

import java.util.List;

public record PublicDocumentFacetView(
        long totalDocumentCount,
        List<PublicDocumentCategoryFacetView> categories,
        List<PublicDocumentTagFacetView> allTags
) {
}
