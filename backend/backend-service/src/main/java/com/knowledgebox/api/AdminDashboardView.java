package com.knowledgebox.api;

public record AdminDashboardView(
        long profileCount,
        long documentCount,
        long activeHookCount,
        long recentTraceCount
) {
}

