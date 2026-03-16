package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AppToolExecutionLogView(
        String executionId,
        String toolCode,
        Long userId,
        String status,
        Long durationMs,
        String requestSummaryJson,
        String responseSummaryJson,
        String errorCode,
        String errorMessage,
        String clientIpMasked,
        OffsetDateTime createdAt
) {
}
