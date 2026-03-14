package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateMcpServerRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 32) String transportType,
        @NotBlank @Size(max = 256) String target,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Long timeoutMs,
        Long initializationTimeoutMs,
        boolean enabled
) {
}
