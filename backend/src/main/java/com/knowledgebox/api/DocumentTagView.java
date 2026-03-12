package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentTaxonomySource;

public record DocumentTagView(
        Long id,
        String name,
        DocumentTaxonomySource source
) {
}
