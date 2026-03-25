package com.knowledgebox.api;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageCode;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageStatus;
import java.time.OffsetDateTime;

public record KnowledgeIngestionTaskStageView(
        Long id,
        KnowledgeIngestionTaskStageCode stageCode,
        KnowledgeIngestionTaskStageStatus status,
        Integer sortOrder,
        Integer progressPercent,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
