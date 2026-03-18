package com.knowledgebox.api;

import java.util.List;

public record ConfigBundleImportCommitResultView(
        int createdCount,
        int overwrittenCount,
        int skippedCount,
        List<String> messages
) {
}
