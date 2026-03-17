package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record DocumentDuplicateCleanupRequest(
        DocumentVisibilityType visibilityType,
        DocumentStatus status,
        @Size(max = 16) String keepStrategy,
        @Min(0) Integer limit,
        boolean triggerIndexRebuild
) {
}
