package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AgentExecutionEventView(
        Long id,
        String spanId,
        String eventType,
        Integer sequenceNo,
        OffsetDateTime occurredAt,
        String payloadJson
) {
}
