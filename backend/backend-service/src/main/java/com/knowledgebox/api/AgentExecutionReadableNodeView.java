package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentExecutionReadableNodeView(
        String nodeId,
        String nodeType,
        String title,
        String badge,
        String technicalLabel,
        String plainSummary,
        String inputExplanation,
        String outputExplanation,
        String status,
        Integer sequenceNo,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Long durationMs,
        String rawRefType,
        String rawRefId,
        List<AgentExecutionReadableNodeView> children
) {
}
