package com.knowledgebox.api;

import java.util.List;

public record KnowledgeIngestionOptionsView(
        List<DocumentCategoryView> categories,
        List<DocumentColumnView> columns,
        List<DocumentTagView> tags
) {
}
