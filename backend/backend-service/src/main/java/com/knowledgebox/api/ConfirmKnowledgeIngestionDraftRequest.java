package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConfirmKnowledgeIngestionDraftRequest(
        @Size(max = 128) String title,
        @Size(max = 128) String categoryName,
        @Size(max = 128) String columnName,
        List<@NotBlank @Size(max = 128) String> tags
) {
}
