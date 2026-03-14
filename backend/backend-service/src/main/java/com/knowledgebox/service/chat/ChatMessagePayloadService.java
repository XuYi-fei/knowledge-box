package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatCitationView;
import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.api.UserChatMessageView;
import com.knowledgebox.domain.chat.ChatTurn;
import java.util.List;

final class ChatMessagePayloadService {

    private final ConversationMemoryService conversationMemoryService;

    ChatMessagePayloadService(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
    }

    ChatMessagePayload resolvePayload(ChatTurn assistantTurn) {
        UserChatMessageView messageView = findMessageView(assistantTurn);
        if (messageView == null) {
            return new ChatMessagePayload(List.of(), List.of(), List.of());
        }
        List<String> reasoningSteps = messageView.reasoningSteps() == null ? List.of() : List.copyOf(messageView.reasoningSteps());
        List<ChatCitationView> citations = messageView.citations() == null ? List.of() : List.copyOf(messageView.citations());
        List<String> toolCalls = messageView.toolCalls() == null ? List.of() : List.copyOf(messageView.toolCalls());
        return new ChatMessagePayload(reasoningSteps, citations, toolCalls);
    }

    ChatResponse toLegacyResponse(String sessionId, ChatTurn assistantTurn) {
        ChatMessagePayload payload = resolvePayload(assistantTurn);
        return new ChatResponse(
                sessionId,
                assistantTurn.getContent(),
                payload.citations(),
                payload.toolCalls(),
                assistantTurn.getModelCode()
        );
    }

    private UserChatMessageView findMessageView(ChatTurn assistantTurn) {
        return conversationMemoryService.sessionDetail(assistantTurn.getUserId(), assistantTurn.getSessionCode())
                .messages().stream()
                .filter(message -> message.messageId().equals(assistantTurn.getMessageCode()))
                .findFirst()
                .orElse(null);
    }
}
