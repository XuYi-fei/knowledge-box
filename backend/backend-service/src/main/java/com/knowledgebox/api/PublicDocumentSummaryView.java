package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record PublicDocumentSummaryView(
        Long id,
        String title,
        String categoryName,
        List<String> tags,
        String excerpt,
        OffsetDateTime updatedAt
) {
}
