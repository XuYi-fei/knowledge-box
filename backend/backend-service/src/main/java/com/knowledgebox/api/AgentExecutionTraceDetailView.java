package com.knowledgebox.api;

import java.util.List;

public record AgentExecutionTraceDetailView(
        AgentExecutionTraceSummaryView trace,
        List<AgentExecutionTimelineItemView> agentTimeline,
        List<AgentExecutionReadableNodeView> readableAgentTimeline,
        List<AgentExecutionReadableNodeView> readableBackendTimeline,
        List<AgentExecutionBackendSpanView> backendSpans,
        List<AgentExecutionSpanView> spans,
        List<AgentExecutionEventView> events
) {
}
