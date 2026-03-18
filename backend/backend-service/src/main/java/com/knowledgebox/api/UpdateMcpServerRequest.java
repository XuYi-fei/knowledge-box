package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateMcpServerRequest(
        @NotBlank @Size(max = 32) String transportType,
        @NotBlank @Size(max = 256) String target,
        Map<String, String> headers,
        Map<String, String> queryParams,
        java.util.List<RuntimeEnvRequirementView> runtimeEnvRequirements,
        Long timeoutMs,
        Long initializationTimeoutMs,
        boolean enabled
) {
}
