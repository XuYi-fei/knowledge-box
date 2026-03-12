package com.knowledgebox.api;

public record McpServerView(
        Long id,
        String code,
        String transportType,
        String target,
        String headersMaskedJson,
        String queryParamsJson,
        Long timeoutMs,
        Long initializationTimeoutMs,
        boolean enabled
) {
}
