package com.knowledgebox.api;

import java.util.List;

public record AgentExecutionTracePageView(
        List<AgentExecutionTraceSummaryView> items,
        long total,
        int page,
        int pageSize
) {
}
