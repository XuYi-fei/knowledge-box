package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record UserChatMessageView(
        String messageId,
        String clientMessageId,
        String role,
        String content,
        String status,
        List<String> reasoningSteps,
        List<ChatProcessDetailView> processDetails,
        List<ChatCitationView> citations,
        List<String> toolCalls,
        String chatModel,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
