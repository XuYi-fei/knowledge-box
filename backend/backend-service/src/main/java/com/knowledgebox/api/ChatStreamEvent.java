package com.knowledgebox.api;

import java.util.List;

public record ChatStreamEvent(
        String type,
        String sessionId,
        String messageId,
        String delta,
        String fullContent,
        List<String> reasoningSteps,
        List<ChatProcessDetailView> processDetails,
        List<ChatCitationView> citations,
        List<String> toolCalls,
        String status,
        String chatModel,
        String errorMessage
) {
}
