package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record AppToolDefinitionView(
        Long id,
        String code,
        String name,
        String summary,
        String descriptionMarkdown,
        String categoryCode,
        String iconKey,
        List<String> tags,
        int displayOrder,
        boolean enabled,
        String executionMode,
        String rendererCode,
        String handlerCode,
        String inputSchemaJson,
        String defaultValuesJson,
        String resultSchemaJson,
        String serverConfigJson,
        Integer timeoutMs,
        String rateLimitScope,
        Integer rateLimitMaxRequests,
        Integer rateLimitWindowSeconds,
        boolean auditEnabled,
        Integer payloadLimitBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
