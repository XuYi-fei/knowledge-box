package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentVisibilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentReviewRequest(
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 256) String sourceFilename,
        DocumentVisibilityType visibilityType,
        @NotBlank String sourceMarkdown,
        String extensionJson
) {
}
