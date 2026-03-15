package com.knowledgebox.service.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class ChatExchangeRuntime {

    private final String sessionCode;
    private final CopyOnWriteArrayList<String> toolCalls = new CopyOnWriteArrayList<>();
    private volatile List<RetrievedChunk> retrievals = List.of();

    ChatExchangeRuntime(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    String sessionCode() {
        return sessionCode;
    }

    void recordToolCall(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        toolCalls.add(toolName);
    }

    void recordRetrievals(List<RetrievedChunk> chunks) {
        retrievals = chunks == null ? List.of() : List.copyOf(chunks);
    }

    List<String> toolCalls() {
        return List.copyOf(toolCalls);
    }

    List<RetrievedChunk> retrievals() {
        return retrievals;
    }
}
