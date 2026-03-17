package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentTaxonomySource;

public record DocumentColumnView(
        Long id,
        String name,
        DocumentTaxonomySource source
) {
}
