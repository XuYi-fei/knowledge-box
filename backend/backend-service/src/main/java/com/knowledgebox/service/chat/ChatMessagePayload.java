package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatCitationView;
import java.util.List;

record ChatMessagePayload(
        List<String> reasoningSteps,
        List<ChatCitationView> citations,
        List<String> toolCalls
) {
}
