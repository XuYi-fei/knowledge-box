package com.knowledgebox.api;

public record UserDebugChatEntryView(
        String profileCode,
        String profileName,
        String description,
        boolean available,
        boolean canStartNewConversation,
        boolean hasHistory
) {
}
