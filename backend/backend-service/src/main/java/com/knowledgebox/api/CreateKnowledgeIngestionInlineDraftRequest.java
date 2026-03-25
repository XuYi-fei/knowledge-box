package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeIngestionInlineDraftRequest(
        @NotBlank String content,
        @Size(max = 256) String sourceFilename
) {
}
