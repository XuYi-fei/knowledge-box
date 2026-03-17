package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record DocumentColumnDocumentView(
        Long id,
        String title,
        OffsetDateTime createdAt
) {
}
