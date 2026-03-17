package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import java.util.List;

public record DocumentDuplicateCleanupPreviewView(
        List<DocumentDuplicateCleanupItemView> items,
        long previewCount,
        DocumentVisibilityType visibilityType,
        DocumentStatus status,
        String keepStrategy,
        int limit
) {
}
