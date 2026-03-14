package com.knowledgebox.api;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        List<ChatCitationView> citations,
        List<String> toolCalls,
        String chatModel
) {
}
