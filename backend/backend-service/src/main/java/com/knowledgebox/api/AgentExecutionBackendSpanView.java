package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AgentExecutionBackendSpanView(
        String callId,
        String parentCallId,
        String callName,
        String callType,
        String serviceClass,
        String methodName,
        String status,
        Integer sequenceNo,
        Integer attemptNo,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long durationMs,
        String inputJson,
        String outputJson,
        String errorJson,
        String relatedSpanId
) {
}
