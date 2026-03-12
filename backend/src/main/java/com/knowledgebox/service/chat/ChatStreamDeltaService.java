package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatStreamEvent;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

final class ChatStreamDeltaService {

    private static final int STREAM_CHUNK_SIZE = 24;

    private final ConversationMemoryService conversationMemoryService;
    private final ChatStreamBroker chatStreamBroker;

    ChatStreamDeltaService(ConversationMemoryService conversationMemoryService, ChatStreamBroker chatStreamBroker) {
        this.conversationMemoryService = conversationMemoryService;
        this.chatStreamBroker = chatStreamBroker;
    }

    void pushAnswerDelta(
            StreamTask task,
            List<String> reasoningSteps,
            String fullContent,
            String delta
    ) {
        conversationMemoryService.markAssistantStreaming(
                task.userId(),
                task.sessionId(),
                task.assistantMessageId(),
                fullContent,
                reasoningSteps
        );
        chatStreamBroker.publish(
                task.assistantMessageId(),
                "message",
                new ChatStreamEvent(
                        "delta",
                        task.sessionId(),
                        task.assistantMessageId(),
                        delta,
                        fullContent,
                        List.copyOf(reasoningSteps),
                        List.of(),
                        List.of(),
                        ChatMessageStatus.STREAMING.name(),
                        task.chatModelCode(),
                        null
                )
        );
    }

    void streamTextDelta(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            String incomingText,
            Duration paceDelay,
            Runnable cancellationCheck
    ) {
        if (incomingText == null || incomingText.isBlank()) {
            return;
        }
        String delta = resolveDelta(answerBuilder.toString(), incomingText);
        if (delta.isBlank()) {
            return;
        }
        List<String> chunks = splitIntoStreamChunks(delta);
        for (String chunk : chunks) {
            if (cancellationCheck != null) {
                cancellationCheck.run();
            }
            answerBuilder.append(chunk);
            pushAnswerDelta(task, reasoningSteps, answerBuilder.toString(), chunk);
            if (paceDelay != null && chunks.size() > 1) {
                sleep(paceDelay);
            }
        }
    }

    private String resolveDelta(String currentFullContent, String incomingText) {
        String current = currentFullContent == null ? "" : currentFullContent;
        if (incomingText.startsWith(current)) {
            return incomingText.substring(current.length());
        }
        if (!current.isBlank() && current.startsWith(incomingText)) {
            return "";
        }
        return incomingText;
    }

    private List<String> splitIntoStreamChunks(String answer) {
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < answer.length()) {
            int next = Math.min(answer.length(), index + STREAM_CHUNK_SIZE);
            chunks.add(answer.substring(index, next));
            index = next;
        }
        return chunks;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while streaming text delta");
        }
    }
}
