package com.knowledgebox.api;

import java.util.List;

public record BatchDocumentReviewActionResultView(
        int processedCount,
        List<DocumentReviewRequestSummaryView> items
) {
}
