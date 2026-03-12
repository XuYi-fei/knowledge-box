package com.knowledgebox.api;

import java.time.OffsetDateTime;

public record UserChatSessionSummaryView(
        String sessionId,
        String title,
        String selectedChatModel,
        int messageCount,
        String lastMessagePreview,
        boolean pending,
        OffsetDateTime updatedAt
) {
}
