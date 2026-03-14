package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AgentExecutionSpanView(
        String spanId,
        String parentSpanId,
        String spanName,
        String spanType,
        String status,
        Integer sequenceNo,
        Integer attemptNo,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long durationMs,
        String inputJson,
        String outputJson,
        String tagsJson,
        String errorJson
) {
}
