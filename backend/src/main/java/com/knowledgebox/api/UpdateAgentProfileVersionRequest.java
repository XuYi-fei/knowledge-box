package com.knowledgebox.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAgentProfileVersionRequest(
        @NotBlank @Size(max = 64) String chatModel,
        @NotBlank @Size(max = 64) String routingModel,
        @NotBlank @Size(max = 64) String embeddingModel,
        @Size(max = 64) String rerankModel,
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @NotNull @Min(1) Integer retrievalTopK,
        @NotNull @Min(0) Integer reasoningBudget
) {
}
