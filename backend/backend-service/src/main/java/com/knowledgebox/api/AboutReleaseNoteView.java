package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record AboutReleaseNoteView(
        Long id,
        String versionLabel,
        String title,
        String summary,
        String contentMarkdown,
        OffsetDateTime publishedAt,
        boolean highlighted
) {
}
