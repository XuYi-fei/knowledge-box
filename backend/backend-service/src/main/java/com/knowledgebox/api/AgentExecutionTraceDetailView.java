package com.knowledgebox.api;

import java.util.List;

public record AgentExecutionTraceDetailView(
        AgentExecutionTraceSummaryView trace,
        List<AgentExecutionTimelineItemView> agentTimeline,
        List<AgentExecutionBackendSpanView> backendSpans,
        List<AgentExecutionSpanView> spans,
        List<AgentExecutionEventView> events
) {
}
