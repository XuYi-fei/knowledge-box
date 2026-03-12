package com.knowledgebox.api;

import com.knowledgebox.domain.agent.ModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateModelCatalogRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String provider,
        @NotNull ModelType modelType,
        @Size(max = 1000) String description,
        boolean enabled,
        boolean publicSelectable,
        boolean defaultForPublic
) {
}
