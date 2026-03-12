package com.knowledgebox.api;

import com.knowledgebox.domain.agent.ModelType;

public record ModelCatalogView(
        Long id,
        String code,
        String displayName,
        String provider,
        ModelType modelType,
        String description,
        boolean enabled,
        boolean publicSelectable,
        boolean defaultForPublic
) {
}
