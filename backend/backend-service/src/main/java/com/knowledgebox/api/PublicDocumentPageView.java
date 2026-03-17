package com.knowledgebox.api;

import java.util.List;

public record PublicDocumentPageView(
        List<PublicDocumentSummaryView> items,
        long total,
        int page,
        int pageSize
) {
}
