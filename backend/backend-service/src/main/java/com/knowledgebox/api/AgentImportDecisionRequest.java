package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentImportDecisionRequest(
        @NotBlank String profileCode,
        @NotNull AgentImportResolutionAction action
) {
}
