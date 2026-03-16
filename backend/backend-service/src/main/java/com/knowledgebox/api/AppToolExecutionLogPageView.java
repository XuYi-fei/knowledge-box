package com.knowledgebox.api;

import java.util.List;

public record AppToolExecutionLogPageView(
        List<AppToolExecutionLogView> items,
        long total,
        int page,
        int pageSize
) {
}
