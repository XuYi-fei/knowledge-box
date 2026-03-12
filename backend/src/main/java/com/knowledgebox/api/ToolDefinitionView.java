package com.knowledgebox.api;

public record ToolDefinitionView(
        Long id,
        String code,
        String name,
        String className,
        String beanName,
        String configJson,
        boolean enabled
) {
}
