package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateAppToolDefinitionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 256) String summary,
        @NotBlank String descriptionMarkdown,
        @NotBlank @Size(max = 64) String categoryCode,
        @NotBlank @Size(max = 64) String iconKey,
        List<@Size(max = 64) String> tags,
        Integer displayOrder,
        boolean enabled,
        @NotBlank @Size(max = 16) String executionMode,
        @NotBlank @Size(max = 64) String rendererCode,
        @NotBlank @Size(max = 64) String handlerCode,
        @NotBlank String inputSchemaJson,
        String defaultValuesJson,
        String resultSchemaJson,
        String serverConfigJson,
        Integer timeoutMs,
        @NotBlank @Size(max = 16) String rateLimitScope,
        Integer rateLimitMaxRequests,
        Integer rateLimitWindowSeconds,
        boolean auditEnabled,
        Integer payloadLimitBytes
) {
}
