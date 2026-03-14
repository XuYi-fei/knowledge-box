package com.knowledgebox.service.document;

import java.util.List;

public record DocumentUploadResult(
        String title,
        String sourceFilename,
        String normalizedMarkdownPath,
        List<String> rewrittenAssets,
        Long reviewRequestId,
        String reviewRequestCode
) {
}
