package com.knowledgebox.api;

import java.util.List;

public record AgentImportCommitResultView(
        int createdCount,
        int overwrittenCount,
        int skippedCount,
        List<String> messages
) {
}
