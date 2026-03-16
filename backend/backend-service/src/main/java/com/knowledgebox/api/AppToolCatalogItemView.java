package com.knowledgebox.api;

import java.util.List;

public record AppToolCatalogItemView(
        String code,
        String name,
        String summary,
        String descriptionMarkdown,
        String categoryCode,
        String iconKey,
        List<String> tags,
        int displayOrder,
        String executionMode,
        String rendererCode,
        String handlerCode,
        String inputSchemaJson,
        String defaultValuesJson,
        String resultSchemaJson
) {
}
