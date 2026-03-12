package com.knowledgebox.api;

import java.util.List;

public record DocumentReviewRequestPageView(
        List<DocumentReviewRequestSummaryView> items,
        long total,
        int page,
        int pageSize
) {
}
