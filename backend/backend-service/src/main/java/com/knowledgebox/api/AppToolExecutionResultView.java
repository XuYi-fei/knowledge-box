package com.knowledgebox.api;

import com.fasterxml.jackson.databind.JsonNode;

public record AppToolExecutionResultView(
        String toolCode,
        String executionMode,
        String resultType,
        JsonNode result,
        String resultPreview,
        Long durationMs,
        String executionId
) {
}
