package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import com.knowledgebox.domain.chat.ChatTurn;
import org.junit.jupiter.api.Test;

class AssistantTurnAwaitServiceTests {

    @Test
    void shouldThrowConflictWhenAssistantMessageWasCancelled() {
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        AssistantTurnAwaitService awaitService = new AssistantTurnAwaitService(conversationMemoryService);

        ChatTurn cancelledTurn = new ChatTurn();
        cancelledTurn.setUserId(1L);
        cancelledTurn.setSessionCode("session-1");
        cancelledTurn.setMessageCode("assistant-1");
        cancelledTurn.setStatus(ChatMessageStatus.CANCELLED);
        cancelledTurn.setErrorMessage("已停止回答");

        when(conversationMemoryService.findAssistantByClientMessageId(1L, "session-1", "client-1"))
                .thenReturn(cancelledTurn);

        assertThatThrownBy(() -> awaitService.awaitTerminalAssistantTurn(1L, "session-1", "client-1", duration -> {
        }))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已停止回答");
    }
}
