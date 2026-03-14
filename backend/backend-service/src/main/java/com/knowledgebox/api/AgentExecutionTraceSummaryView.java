package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AgentExecutionTraceSummaryView(
        String traceId,
        Long userId,
        String sessionCode,
        String assistantMessageCode,
        String clientMessageId,
        String profileCode,
        String chatModelCode,
        String requestQueryMasked,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long durationMs,
        Integer attemptCount,
        String errorCode,
        String errorMessage
) {
}
