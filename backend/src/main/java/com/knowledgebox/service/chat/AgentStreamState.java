package com.knowledgebox.service.chat;

import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class AgentStreamState {

    Msg finalMessage;
    String lastReasoningChunk = "";
    String pendingReasoningChunk = "";
    long lastReasoningEmittedAt;
    final Set<String> toolCalls = new LinkedHashSet<>();
    final Map<EventType, Integer> eventTypeCounts = new java.util.EnumMap<>(EventType.class);

    void recordEvent(EventType eventType) {
        eventTypeCounts.merge(eventType, 1, Integer::sum);
    }
}
