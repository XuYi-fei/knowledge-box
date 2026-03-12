package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentTaxonomySource;

public record DocumentCategoryView(
        Long id,
        String name,
        DocumentTaxonomySource source
) {
}
