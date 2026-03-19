package com.knowledgebox.service.chat;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import com.knowledgebox.domain.chat.ChatTurn;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

final class AssistantTurnAwaitService {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration);
    }

    private static final Logger log = LoggerFactory.getLogger(AssistantTurnAwaitService.class);
    private static final int MAX_PLACEHOLDER_CHECKS = 20;
    private static final Duration PLACEHOLDER_POLL_DELAY = Duration.ofMillis(50);
    private static final Duration STREAMING_POLL_DELAY = Duration.ofMillis(80);

    private final ConversationMemoryService conversationMemoryService;

    AssistantTurnAwaitService(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
    }

    ChatTurn awaitTerminalAssistantTurn(
            Long userId,
            String sessionId,
            String clientMessageId,
            Sleeper sleeper
    ) {
        ChatTurn assistantTurn = null;
        int placeholderChecks = 0;
        while (assistantTurn == null && placeholderChecks++ < MAX_PLACEHOLDER_CHECKS) {
            assistantTurn = conversationMemoryService.findAssistantByClientMessageId(userId, sessionId, clientMessageId);
            if (assistantTurn == null) {
                sleeper.sleep(PLACEHOLDER_POLL_DELAY);
            }
        }
        if (assistantTurn == null) {
            log.error(
                    "Assistant placeholder was not created in time. userId={}, sessionId={}, clientMessageId={}",
                    userId,
                    sessionId,
                    clientMessageId
            );
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHAT_PLACEHOLDER_MISSING",
                    "未找到当前问题对应的助手占位消息"
            );
        }

        while (assistantTurn.getStatus() == ChatMessageStatus.PENDING || assistantTurn.getStatus() == ChatMessageStatus.STREAMING) {
            sleeper.sleep(STREAMING_POLL_DELAY);
            assistantTurn = conversationMemoryService.loadMessage(userId, sessionId, assistantTurn.getMessageCode());
        }
        if (assistantTurn.getStatus() == ChatMessageStatus.FAILED) {
            log.error(
                    "Assistant message entered failed status in legacy answer flow. userId={}, sessionId={}, assistantMessageId={}, error={}",
                    userId,
                    sessionId,
                    assistantTurn.getMessageCode(),
                    assistantTurn.getErrorMessage()
            );
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_STREAM_FAILED", assistantTurn.getErrorMessage());
        }
        if (assistantTurn.getStatus() == ChatMessageStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_STREAM_CANCELLED", assistantTurn.getErrorMessage());
        }
        return assistantTurn;
    }
}
