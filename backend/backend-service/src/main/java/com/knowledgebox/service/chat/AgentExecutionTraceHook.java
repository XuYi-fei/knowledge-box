package com.knowledgebox.service.chat;

import com.knowledgebox.domain.chat.AgentExecutionSpanType;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
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
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

final class AgentExecutionTraceHook implements Hook {

    private final AgentExecutionTraceService traceService;
    private final AgentExecutionTraceContext traceContext;
    private final String requestSpanId;
    private final Supplier<String> answerSpanIdSupplier;
    private final InvocationSpanDescriptor invocationSpanDescriptor;
    private final Map<String, Map<String, Object>> subAgentToolMetadataByName;
    private volatile boolean invocationSpanStarted;

    AgentExecutionTraceHook(AgentExecutionTraceService traceService, AgentExecutionTraceContext traceContext) {
        this(traceService, traceContext, traceContext.requestSpanId(), traceContext::answerStreamSpanId, null, Map.of());
    }

    AgentExecutionTraceHook(
            AgentExecutionTraceService traceService,
            AgentExecutionTraceContext traceContext,
            String requestSpanId,
            Supplier<String> answerSpanIdSupplier,
            InvocationSpanDescriptor invocationSpanDescriptor,
            Map<String, Map<String, Object>> subAgentToolMetadataByName
    ) {
        this.traceService = traceService;
        this.traceContext = traceContext;
        this.requestSpanId = requestSpanId;
        this.answerSpanIdSupplier = answerSpanIdSupplier;
        this.invocationSpanDescriptor = invocationSpanDescriptor;
        this.subAgentToolMetadataByName = subAgentToolMetadataByName == null ? Map.of() : Map.copyOf(subAgentToolMetadataByName);
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        switch (event) {
            case PreCallEvent preCallEvent -> {
                ensureInvocationSpanStarted(preCallEvent);
                traceService.recordEvent(
                        traceContext,
                        requestSpanId,
                        "agent.call.start",
                        payload("inputMessages", serializeMessages(preCallEvent.getInputMessages()))
                );
            }
            case PostCallEvent postCallEvent -> {
                ensureInvocationSpanStarted(null);
                Map<String, Object> finalPayload = payload("finalMessage", serializeMessage(postCallEvent.getFinalMessage()));
                traceService.recordEvent(traceContext, requestSpanId, "agent.call.end", finalPayload);
                closeInvocationSpan(AgentExecutionStatus.COMPLETED, finalPayload, Map.of(), null);
            }
            case PreReasoningEvent preReasoningEvent -> {
                ensureInvocationSpanStarted(null);
                traceService.recordEvent(
                        traceContext,
                        resolveAnswerSpanId(),
                        "prompt.injected",
                        payload(
                                "phase", "reasoning",
                                "modelName", preReasoningEvent.getModelName(),
                                "generateOptions", preReasoningEvent.getGenerateOptions(),
                                "inputMessages", serializeMessages(preReasoningEvent.getInputMessages())
                        )
                );
            }
            case PreSummaryEvent preSummaryEvent -> {
                ensureInvocationSpanStarted(null);
                traceService.recordEvent(
                        traceContext,
                        resolveAnswerSpanId(),
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
            }
            case PreActingEvent preActingEvent -> {
                ensureInvocationSpanStarted(null);
                startToolSpan(preActingEvent);
            }
            case ActingChunkEvent actingChunkEvent -> traceService.recordEvent(
                    traceContext,
                    resolveToolSpanId(actingChunkEvent.getToolUse()),
                    "tool.chunk",
                    payload("chunk", serializeToolResult(actingChunkEvent.getChunk()))
            );
            case PostActingEvent postActingEvent -> finishToolSpan(postActingEvent);
            case ErrorEvent errorEvent -> {
                ensureInvocationSpanStarted(null);
                Map<String, Object> errorPayload = errorPayload(errorEvent);
                traceService.recordEvent(traceContext, requestSpanId, "agent.error", errorPayload);
                closeInvocationSpan(AgentExecutionStatus.FAILED, Map.of(), Map.of(), errorPayload);
            }
            default -> {
            }
        }
        return Mono.just(event);
    }

    private void ensureInvocationSpanStarted(PreCallEvent preCallEvent) {
        if (invocationSpanDescriptor == null || invocationSpanStarted) {
            return;
        }
        String parentSpanId = traceContext.currentActiveSpanId();
        if (parentSpanId != null && parentSpanId.equals(invocationSpanDescriptor.spanId())) {
            parentSpanId = invocationSpanDescriptor.parentSpanId();
        }
        traceService.startSpan(
                traceContext,
                parentSpanId,
                invocationSpanDescriptor.spanId(),
                invocationSpanDescriptor.spanName(),
                invocationSpanDescriptor.spanType(),
                preCallEvent == null ? Map.of() : payload("inputMessages", serializeMessages(preCallEvent.getInputMessages())),
                invocationSpanDescriptor.tags()
        );
        invocationSpanStarted = true;
    }

    private void closeInvocationSpan(
            AgentExecutionStatus status,
            Map<String, ?> output,
            Map<String, ?> tags,
            Map<String, ?> error
    ) {
        if (invocationSpanDescriptor == null || !invocationSpanStarted) {
            return;
        }
        traceService.endSpan(
                traceContext,
                invocationSpanDescriptor.spanId(),
                status,
                output,
                tags,
                error
        );
    }

    private void startToolSpan(PreActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        String toolCallId = toolUse == null ? null : toolUse.getId();
        String spanId = traceService.nextSpanIdValue();
        int toolIndex = traceContext.nextToolCallIndex();
        traceContext.bindToolSpan(toolCallId, spanId);
        traceService.startSpan(
                traceContext,
                resolveAnswerSpanId(),
                spanId,
                "tool.call[" + toolIndex + "]",
                AgentExecutionSpanType.TOOL,
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
                result != null && result.isSuspended() ? AgentExecutionStatus.CANCELLED : AgentExecutionStatus.COMPLETED,
                payload("toolResult", serializeToolResult(result)),
                Map.of(),
                null
        );
        if (toolUse != null) {
            traceContext.removeToolSpan(toolUse.getId());
        }
    }

    private String resolveAnswerSpanId() {
        String answerSpanId = answerSpanIdSupplier == null ? null : answerSpanIdSupplier.get();
        if (answerSpanId == null || answerSpanId.isBlank()) {
            return requestSpanId;
        }
        return answerSpanId;
    }

    private String resolveToolSpanId(ToolUseBlock toolUse) {
        if (toolUse == null) {
            return resolveAnswerSpanId();
        }
        String existing = traceContext.findToolSpan(toolUse.getId());
        return existing == null ? resolveAnswerSpanId() : existing;
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
        appendSubAgentToolMetadata(payload, toolUse);
        return payload;
    }

    private Map<String, Object> toolEndPayload(ToolUseBlock toolUse, ToolResultBlock result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolUse == null ? null : toolUse.getName());
        payload.put("toolCallId", toolUse == null ? null : toolUse.getId());
        payload.put("toolResult", serializeToolResult(result));
        appendSubAgentToolMetadata(payload, toolUse);
        return payload;
    }

    private void appendSubAgentToolMetadata(Map<String, Object> payload, ToolUseBlock toolUse) {
        if (toolUse == null || toolUse.getName() == null) {
            return;
        }
        Map<String, Object> metadata = subAgentToolMetadataByName.get(toolUse.getName());
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        payload.putAll(metadata);
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            payload.put((String) keyValues[index], keyValues[index + 1]);
        }
        return payload;
    }

    record InvocationSpanDescriptor(
            String spanId,
            String parentSpanId,
            String spanName,
            AgentExecutionSpanType spanType,
            Map<String, Object> tags
    ) {
    }
}
