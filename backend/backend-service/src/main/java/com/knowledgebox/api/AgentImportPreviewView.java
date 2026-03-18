package com.knowledgebox.api;

import java.util.List;

public record AgentImportPreviewView(
        String previewToken,
        String schemaVersion,
        int totalCount,
        int creatableCount,
        int codeConflictCount,
        int nameConflictCount,
        int validationErrorCount,
        List<String> globalMessages,
        List<AgentImportPreviewItemView> items
) {
}
