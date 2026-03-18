package com.knowledgebox.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ConfigBundleImportCommitRequest(
        @NotBlank String previewToken,
        List<@Valid ConfigBundleImportDecisionRequest> decisions
) {
}
