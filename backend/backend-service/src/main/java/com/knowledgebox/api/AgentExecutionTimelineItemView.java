package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AgentExecutionTimelineItemView(
        String itemId,
        String itemType,
        String sourceType,
        String title,
        String status,
        Integer sequenceNo,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long durationMs,
        String inputJson,
        String outputJson,
        String payloadJson,
        String relatedSpanId,
        Long relatedEventId
) {
}
