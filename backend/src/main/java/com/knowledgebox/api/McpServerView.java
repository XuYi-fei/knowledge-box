package com.knowledgebox.api;

public record McpServerView(
        Long id,
        String code,
        String transportType,
        String target,
        boolean enabled
) {
}

