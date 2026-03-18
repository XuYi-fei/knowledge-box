package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateToolDefinitionRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 255) String className,
        @Size(max = 255) String beanName,
        String configJson,
        java.util.List<RuntimeEnvRequirementView> runtimeEnvRequirements,
        boolean enabled
) {
}
