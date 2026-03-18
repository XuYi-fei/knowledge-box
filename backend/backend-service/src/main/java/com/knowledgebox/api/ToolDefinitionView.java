package com.knowledgebox.api;

public record ToolDefinitionView(
        Long id,
        String code,
        String name,
        String className,
        String beanName,
        String configJson,
        java.util.List<RuntimeEnvRequirementView> runtimeEnvRequirements,
        boolean enabled
) {
}
