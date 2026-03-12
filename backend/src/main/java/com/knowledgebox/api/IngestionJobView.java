package com.knowledgebox.api;

import com.knowledgebox.domain.document.IngestionJobStatus;

public record IngestionJobView(
        Long id,
        Long documentId,
        String documentTitle,
        IngestionJobStatus status,
        String jobType,
        String detail
) {
}

