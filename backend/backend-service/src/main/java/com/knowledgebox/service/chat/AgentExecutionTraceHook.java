package com.knowledgebox.service.chat;

import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.hook.PreSummaryEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

final class AgentExecutionTraceHook implements Hook {

    private final AgentExecutionTraceService traceService;
    private final AgentExecutionTraceContext traceContext;

    AgentExecutionTraceHook(AgentExecutionTraceService traceService, AgentExecutionTraceContext traceContext) {
        this.traceService = traceService;
        this.traceContext = traceContext;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        switch (event) {
            case PreCallEvent preCallEvent -> traceService.recordEvent(
                    traceContext,
                    traceContext.requestSpanId(),
                    "agent.call.start",
                    payload("inputMessages", serializeMessages(preCallEvent.getInputMessages()))
            );
            case PostCallEvent postCallEvent -> traceService.recordEvent(
                    traceContext,
                    traceContext.requestSpanId(),
                    "agent.call.end",
                    payload("finalMessage", serializeMessage(postCallEvent.getFinalMessage()))
            );
            case PreReasoningEvent preReasoningEvent -> traceService.recordEvent(
                    traceContext,
                    traceContext.answerStreamSpanId(),
                    "prompt.injected",
                    payload(
                            "phase", "reasoning",
                            "modelName", preReasoningEvent.getModelName(),
                            "generateOptions", preReasoningEvent.getGenerateOptions(),
                            "inputMessages", serializeMessages(preReasoningEvent.getInputMessages())
                    )
            );
            case PreSummaryEvent preSummaryEvent -> traceService.recordEvent(
                    traceContext,
                    traceContext.answerStreamSpanId(),
                    "prompt.injected",
                    payload(
                            "phase", "summary",
                            "modelName", preSummaryEvent.getModelName(),
                            "generateOptions", preSummaryEvent.getGenerateOptions(),
                            "inputMessages", serializeMessages(preSummaryEvent.getInputMessages()),
                            "currentIteration", preSummaryEvent.getCurrentIteration(),
                            "maxIterations", preSummaryEvent.getMaxIterations()
                    )
            );
            case PreActingEvent preActingEvent -> startToolSpan(preActingEvent);
            case ActingChunkEvent actingChunkEvent -> traceService.recordEvent(
                    traceContext,
                    resolveToolSpanId(actingChunkEvent.getToolUse()),
                    "tool.chunk",
                    payload("chunk", serializeToolResult(actingChunkEvent.getChunk()))
            );
            case PostActingEvent postActingEvent -> finishToolSpan(postActingEvent);
            case ErrorEvent errorEvent -> traceService.recordEvent(
                    traceContext,
                    traceContext.requestSpanId(),
                    "agent.error",
                    errorPayload(errorEvent)
            );
            default -> {
            }
        }
        return Mono.just(event);
    }

    private void startToolSpan(PreActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        String toolCallId = toolUse == null ? null : toolUse.getId();
        String spanId = traceService.nextSpanIdValue();
        int toolIndex = traceContext.nextToolCallIndex();
        traceContext.bindToolSpan(toolCallId, spanId);
        traceService.startSpan(
                traceContext,
                traceContext.answerStreamSpanId(),
                spanId,
                "tool.call[" + toolIndex + "]",
                com.knowledgebox.domain.chat.AgentExecutionSpanType.TOOL,
                toolStartPayload(toolUse),
                payload("toolIndex", toolIndex)
        );
        traceService.recordEvent(
                traceContext,
                spanId,
                "tool.start",
                toolStartPayload(toolUse)
        );
    }

    private void finishToolSpan(PostActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        String spanId = resolveToolSpanId(toolUse);
        ToolResultBlock result = event.getToolResult();
        traceService.recordEvent(
                traceContext,
                spanId,
                "tool.end",
                toolEndPayload(toolUse, result)
        );
        traceService.endSpan(
                traceContext,
                spanId,
                result != null && result.isSuspended()
                        ? com.knowledgebox.domain.chat.AgentExecutionStatus.CANCELLED
                        : com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                payload("toolResult", serializeToolResult(result)),
                Map.of(),
                null
        );
        if (toolUse != null) {
            traceContext.removeToolSpan(toolUse.getId());
        }
    }

    private String resolveToolSpanId(ToolUseBlock toolUse) {
        if (toolUse == null) {
            return traceContext.answerStreamSpanId();
        }
        String existing = traceContext.findToolSpan(toolUse.getId());
        return existing == null ? traceContext.answerStreamSpanId() : existing;
    }

    private List<Map<String, Object>> serializeMessages(List<Msg> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream().map(this::serializeMessage).toList();
    }

    private Map<String, Object> serializeMessage(Msg message) {
        if (message == null) {
            return Map.of();
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("name", message.getName());
        serialized.put("role", message.getRole() == null ? null : message.getRole().name());
        serialized.put("textContent", message.getTextContent());
        serialized.put("blocks", serializeBlocks(message.getContent()));
        return serialized;
    }

    private List<Map<String, Object>> serializeBlocks(List<ContentBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream().map(block -> {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("type", block.getClass().getSimpleName());
            if (block instanceof TextBlock textBlock) {
                serialized.put("text", textBlock.getText());
            }
            return serialized;
        }).toList();
    }

    private Map<String, Object> serializeToolResult(ToolResultBlock result) {
        if (result == null) {
            return Map.of();
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("id", result.getId());
        serialized.put("name", result.getName());
        serialized.put("suspended", result.isSuspended());
        serialized.put("metadata", result.getMetadata());
        serialized.put("output", result.getOutput() == null ? List.of() : result.getOutput().stream().map(block -> {
            if (block instanceof TextBlock textBlock) {
                return Map.<String, Object>of("type", "TextBlock", "text", textBlock.getText());
            }
            return Map.<String, Object>of("type", block.getClass().getSimpleName());
        }).toList());
        return serialized;
    }

    private Map<String, Object> errorPayload(ErrorEvent errorEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exceptionClass", errorEvent.getError() == null ? null : errorEvent.getError().getClass().getName());
        payload.put("message", errorEvent.getError() == null ? null : errorEvent.getError().getMessage());
        return payload;
    }

    private Map<String, Object> toolStartPayload(ToolUseBlock toolUse) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolUse == null ? null : toolUse.getName());
        payload.put("toolCallId", toolUse == null ? null : toolUse.getId());
        payload.put("toolInput", toolUse == null ? Map.of() : toolUse.getInput());
        return payload;
    }

    private Map<String, Object> toolEndPayload(ToolUseBlock toolUse, ToolResultBlock result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolUse == null ? null : toolUse.getName());
        payload.put("toolCallId", toolUse == null ? null : toolUse.getId());
        payload.put("toolResult", serializeToolResult(result));
        return payload;
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put((String) keyValues[index], keyValues[index + 1]);
        }
        return payload;
    }
}
