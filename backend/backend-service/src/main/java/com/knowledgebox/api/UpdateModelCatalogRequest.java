package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateModelCatalogRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String provider,
        @Size(max = 1000) String description,
        boolean enabled,
        boolean publicSelectable,
        boolean defaultForPublic
) {
}
