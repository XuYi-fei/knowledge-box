package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfigBundleImportDecisionRequest(
        @NotNull ConfigBundleResourceType resourceType,
        @NotBlank String resourceCode,
        @NotNull AgentImportResolutionAction action
) {
}
