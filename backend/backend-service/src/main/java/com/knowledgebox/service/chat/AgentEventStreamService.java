package com.knowledgebox.service.chat;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AgentEventStreamService {

    @FunctionalInterface
    interface ProgressUpdater {
        void update(StreamTask task, List<String> reasoningSteps, String fullContent, String delta);
    }

    private static final Logger log = LoggerFactory.getLogger(AgentEventStreamService.class);
    private static final long REASONING_EVENT_MIN_INTERVAL_MS = 450L;
    private static final Duration SYNTHETIC_STREAM_DELAY = Duration.ofMillis(12);

    private final ChatStreamDeltaService chatStreamDeltaService;

    AgentEventStreamService(ChatStreamDeltaService chatStreamDeltaService) {
        this.chatStreamDeltaService = chatStreamDeltaService;
    }

    void consumeAgentEvent(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            AgentStreamState streamState,
            Event event,
            ProgressUpdater progressUpdater
    ) {
        EventType eventType = event.getType();
        streamState.recordEvent(eventType);
        switch (eventType) {
            case REASONING -> {
                String reasoningChunk = extractThinkingSummary(event.getMessage());
                if (!reasoningChunk.isBlank()) {
                    updateReasoningProgress(task, reasoningSteps, answerBuilder, streamState, reasoningChunk, false, progressUpdater);
                }
            }
            case TOOL_RESULT -> updateToolProgress(task, reasoningSteps, answerBuilder, streamState, event.getMessage(), progressUpdater);
            case HINT -> updateHintProgress(task, reasoningSteps, answerBuilder, event.getMessage(), progressUpdater);
            case SUMMARY -> {
                String incomingText = event.getMessage() == null ? "" : event.getMessage().getTextContent();
                chatStreamDeltaService.streamTextDelta(
                        task,
                        reasoningSteps,
                        answerBuilder,
                        incomingText,
                        SYNTHETIC_STREAM_DELAY,
                        null
                );
            }
            case AGENT_RESULT -> {
                streamState.finalMessage = event.getMessage();
                if (event.getMessage() != null) {
                    streamState.toolCalls.addAll(extractToolCalls(event.getMessage()));
                }
                updateReasoningProgress(
                        task,
                        reasoningSteps,
                        answerBuilder,
                        streamState,
                        extractThinkingSummary(event.getMessage()),
                        true,
                        progressUpdater
                );
            }
            case ALL -> {
                if (event.getMessage() != null) {
                    streamState.toolCalls.addAll(extractToolCalls(event.getMessage()));
                }
            }
            default -> log.debug("Ignore unsupported agent event type for streaming: {}", eventType);
        }
    }

    private String maybeSelectReasoningChunk(AgentStreamState streamState, String reasoningChunk, boolean forcePush) {
        if (reasoningChunk == null || reasoningChunk.isBlank()) {
            return "";
        }
        String normalized = reasoningChunk.strip();
        if (normalized.equals(streamState.lastReasoningChunk)) {
            return "";
        }
        long now = System.currentTimeMillis();
        if (!forcePush && now - streamState.lastReasoningEmittedAt < REASONING_EVENT_MIN_INTERVAL_MS) {
            streamState.pendingReasoningChunk = normalized;
            return "";
        }
        streamState.lastReasoningChunk = normalized;
        streamState.lastReasoningEmittedAt = now;
        streamState.pendingReasoningChunk = "";
        return normalized;
    }

    private void flushPendingReasoningChunk(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            AgentStreamState streamState,
            ProgressUpdater progressUpdater
    ) {
        String pending = streamState.pendingReasoningChunk;
        if (pending == null || pending.isBlank()) {
            return;
        }
        if (pending.equals(streamState.lastReasoningChunk)) {
            streamState.pendingReasoningChunk = "";
            return;
        }
        streamState.lastReasoningChunk = pending;
        streamState.lastReasoningEmittedAt = System.currentTimeMillis();
        streamState.pendingReasoningChunk = "";
        reasoningSteps.add("思考中：" + pending);
        progressUpdater.update(task, reasoningSteps, answerBuilder.toString(), "thinking");
    }

    private void updateReasoningProgress(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            AgentStreamState streamState,
            String reasoningChunk,
            boolean forcePush,
            ProgressUpdater progressUpdater
    ) {
        String selected = maybeSelectReasoningChunk(streamState, reasoningChunk, forcePush);
        if (!selected.isBlank()) {
            reasoningSteps.add("思考中：" + selected);
            progressUpdater.update(task, reasoningSteps, answerBuilder.toString(), "thinking");
        } else if (forcePush) {
            flushPendingReasoningChunk(task, reasoningSteps, answerBuilder, streamState, progressUpdater);
        }
    }

    private void updateHintProgress(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            Msg response,
            ProgressUpdater progressUpdater
    ) {
        String hintText = extractHintText(response);
        if (!hintText.isBlank()) {
            reasoningSteps.add("上下文提示：" + hintText);
            progressUpdater.update(task, reasoningSteps, answerBuilder.toString(), "thinking");
        }
    }

    private void updateToolProgress(
            StreamTask task,
            List<String> reasoningSteps,
            StringBuilder answerBuilder,
            AgentStreamState streamState,
            Msg response,
            ProgressUpdater progressUpdater
    ) {
        List<String> toolNames = extractToolCallsFromResult(response);
        if (toolNames.isEmpty()) {
            toolNames = extractToolCalls(response);
        }
        boolean changed = false;
        for (String toolName : toolNames) {
            if (streamState.toolCalls.add(toolName)) {
                reasoningSteps.add("工具执行：" + toolName);
                changed = true;
            }
        }
        if (changed) {
            progressUpdater.update(task, reasoningSteps, answerBuilder.toString(), "thinking");
        }
    }

    private String extractHintText(Msg response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof TextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isBlank()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(text.strip());
                }
            }
        }
        return builder.toString().trim();
    }

    private List<String> extractToolCallsFromResult(Msg message) {
        if (message == null || message.getContent() == null) {
            return List.of();
        }
        List<String> toolNames = new ArrayList<>();
        for (var block : message.getContent()) {
            if (block instanceof ToolResultBlock resultBlock) {
                String toolName = resultBlock.getName();
                if (toolName != null && !toolName.isBlank()) {
                    toolNames.add(toolName.strip());
                }
            }
        }
        return toolNames;
    }

    private List<String> extractToolCalls(Msg response) {
        if (response == null || response.getContent() == null) {
            return List.of();
        }
        List<String> calls = new ArrayList<>();
        for (var block : response.getContent()) {
            if (block instanceof ToolUseBlock toolUseBlock) {
                String toolName = toolUseBlock.getName();
                if (toolName != null && !toolName.isBlank()) {
                    calls.add(toolName);
                }
            }
        }
        return calls;
    }

    private String extractThinkingSummary(Msg response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        StringBuilder thinking = new StringBuilder();
        for (var block : response.getContent()) {
            if (block instanceof ThinkingBlock thinkingBlock) {
                String content = thinkingBlock.getThinking();
                if (content != null && !content.isBlank()) {
                    if (thinking.length() > 0) {
                        thinking.append('\n');
                    }
                    thinking.append(content.strip());
                }
            }
        }
        if (thinking.length() > 0) {
            return thinking.toString();
        }
        String text = response.getTextContent();
        return text == null ? "" : text;
    }
}
