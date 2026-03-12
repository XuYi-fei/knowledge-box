package com.knowledgebox.service.chat;

import java.util.ArrayList;
import java.util.List;

public final class ChatExchangeContext {

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private ChatExchangeContext() {
    }

    public static void clear() {
        STATE.remove();
    }

    public static void sessionCode(String sessionCode) {
        STATE.get().sessionCode = sessionCode;
    }

    public static String sessionCode() {
        return STATE.get().sessionCode;
    }

    public static void recordToolCall(String toolName) {
        STATE.get().toolCalls.add(toolName);
    }

    public static void recordRetrievals(List<RetrievedChunk> chunks) {
        STATE.get().retrievals = List.copyOf(chunks);
    }

    public static List<String> toolCalls() {
        return List.copyOf(STATE.get().toolCalls);
    }

    public static List<RetrievedChunk> retrievals() {
        return List.copyOf(STATE.get().retrievals);
    }

    private static final class State {
        private String sessionCode;
        private final List<String> toolCalls = new ArrayList<>();
        private List<RetrievedChunk> retrievals = List.of();
    }
}
