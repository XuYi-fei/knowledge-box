package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSkillBindingRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1000) String description,
        boolean enabled
) {
}
