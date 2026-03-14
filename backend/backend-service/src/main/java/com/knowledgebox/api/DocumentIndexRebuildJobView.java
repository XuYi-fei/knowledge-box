package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentIndexRebuildStatus;
import java.time.OffsetDateTime;

public record DocumentIndexRebuildJobView(
        Long id,
        String jobCode,
        DocumentIndexRebuildStatus status,
        Long triggeredByUserId,
        String sourceVectorTable,
        String targetVectorTable,
        String detailJson,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
