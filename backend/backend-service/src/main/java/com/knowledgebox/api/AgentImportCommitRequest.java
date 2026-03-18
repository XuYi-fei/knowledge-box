package com.knowledgebox.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AgentImportCommitRequest(
        @NotBlank String previewToken,
        @Valid List<AgentImportDecisionRequest> decisions
) {
}
