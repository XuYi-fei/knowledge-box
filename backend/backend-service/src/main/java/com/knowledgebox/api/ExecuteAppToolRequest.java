package com.knowledgebox.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ExecuteAppToolRequest(
        @NotNull JsonNode input
) {
}
