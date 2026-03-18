package com.knowledgebox.api;

import java.util.Map;

public record ConfigBundleMcpServerView(
        String code,
        String transportType,
        String target,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Long timeoutMs,
        Long initializationTimeoutMs,
        boolean enabled
) {
}
