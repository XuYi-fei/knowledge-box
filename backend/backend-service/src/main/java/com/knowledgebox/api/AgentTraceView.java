package com.knowledgebox.api;

public record AgentTraceView(
        Long id,
        String traceCode,
        String sessionCode,
        String stage,
        String payloadJson
) {
}

