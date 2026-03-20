package com.knowledgebox.service.chat;

import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
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
    final Map<String, PendingToolCall> pendingToolCallsById = new LinkedHashMap<>();
    final Map<String, ArrayDeque<PendingToolCall>> pendingToolCallsByName = new LinkedHashMap<>();

    void recordEvent(EventType eventType) {
        eventTypeCounts.merge(eventType, 1, Integer::sum);
    }

    void rememberToolUse(ToolUseBlock toolUse) {
        if (toolUse == null || toolUse.getName() == null || toolUse.getName().isBlank()) {
            return;
        }
        String toolName = toolUse.getName().trim();
        String toolCallId = toolUse.getId();
        if (toolCallId != null && !toolCallId.isBlank() && pendingToolCallsById.containsKey(toolCallId)) {
            return;
        }
        PendingToolCall pendingToolCall = new PendingToolCall(
                toolCallId,
                toolName,
                toolUse.getInput() == null ? Map.of() : Map.copyOf(toolUse.getInput())
        );
        if (toolCallId != null && !toolCallId.isBlank()) {
            pendingToolCallsById.put(toolCallId, pendingToolCall);
        }
        pendingToolCallsByName.computeIfAbsent(toolName, ignored -> new ArrayDeque<>()).addLast(pendingToolCall);
    }

    PendingToolCall consumeToolCall(ToolResultBlock result) {
        if (result == null) {
            return null;
        }
        String toolCallId = result.getId();
        if (toolCallId != null && !toolCallId.isBlank()) {
            PendingToolCall byId = pendingToolCallsById.remove(toolCallId);
            if (byId != null) {
                removeFromNameQueue(byId);
                return byId;
            }
        }
        String toolName = result.getName();
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        ArrayDeque<PendingToolCall> queue = pendingToolCallsByName.get(toolName.trim());
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        PendingToolCall pendingToolCall = queue.removeFirst();
        if (queue.isEmpty()) {
            pendingToolCallsByName.remove(toolName.trim());
        }
        if (pendingToolCall.toolCallId() != null && !pendingToolCall.toolCallId().isBlank()) {
            pendingToolCallsById.remove(pendingToolCall.toolCallId());
        }
        return pendingToolCall;
    }

    private void removeFromNameQueue(PendingToolCall pendingToolCall) {
        ArrayDeque<PendingToolCall> queue = pendingToolCallsByName.get(pendingToolCall.toolName());
        if (queue == null) {
            return;
        }
        queue.remove(pendingToolCall);
        if (queue.isEmpty()) {
            pendingToolCallsByName.remove(pendingToolCall.toolName());
        }
    }

    record PendingToolCall(
            String toolCallId,
            String toolName,
            Map<String, Object> toolInput
    ) {
    }
}
