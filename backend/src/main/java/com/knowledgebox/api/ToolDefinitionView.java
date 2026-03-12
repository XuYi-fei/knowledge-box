package com.knowledgebox.api;

public record ToolDefinitionView(
        Long id,
        String code,
        String name,
        String endpoint,
        boolean enabled
) {
}

