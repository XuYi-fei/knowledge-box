package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatCitationView;
import com.knowledgebox.api.ChatProcessDetailView;
import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.api.ChatStreamEvent;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

final class ChatGenerationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChatGenerationExecutor.class);

    private final KnowledgeBoxProperties properties;
    private final ConversationMemoryService conversationMemoryService;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;
    private final AgentCapabilityAssemblyService agentCapabilityAssemblyService;
    private final ChatStreamBroker chatStreamBroker;
    private final ChatModelFactory chatModelFactory;
    private final ChatStreamDeltaService chatStreamDeltaService;
    private final AgentEventStreamService agentEventStreamService;
    private final ChatProcessDetailFormatter chatProcessDetailFormatter;
    private final ChatKnowledgeBasePlanService chatKnowledgeBasePlanService;

    ChatGenerationExecutor(
            KnowledgeBoxProperties properties,
            ConversationMemoryService conversationMemoryService,
            AgentExecutionTraceService agentExecutionTraceService,
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
            AgentCapabilityAssemblyService agentCapabilityAssemblyService,
            ChatStreamBroker chatStreamBroker,
            ChatModelFactory chatModelFactory,
            ChatStreamDeltaService chatStreamDeltaService,
            AgentEventStreamService agentEventStreamService,
            ChatProcessDetailFormatter chatProcessDetailFormatter,
            ChatKnowledgeBasePlanService chatKnowledgeBasePlanService
    ) {
        this.properties = properties;
        this.conversationMemoryService = conversationMemoryService;
        this.agentExecutionTraceService = agentExecutionTraceService;
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
        this.agentCapabilityAssemblyService = agentCapabilityAssemblyService;
        this.chatStreamBroker = chatStreamBroker;
        this.chatModelFactory = chatModelFactory;
        this.chatStreamDeltaService = chatStreamDeltaService;
        this.agentEventStreamService = agentEventStreamService;
        this.chatProcessDetailFormatter = chatProcessDetailFormatter;
        this.chatKnowledgeBasePlanService = chatKnowledgeBasePlanService;
    }

    void generate(StreamTask task, RunningChatTask control) {
        List<String> reasoningSteps = new ArrayList<>();
        List<ChatProcessDetailView> processDetails = new ArrayList<>();
        StringBuilder answerBuilder = new StringBuilder();
        AgentExecutionTraceContext traceContext = null;
        ChatExchangeRuntime exchangeRuntime = new ChatExchangeRuntime(task.sessionId());
        String rootBackendCallId = null;
        try {
            throwIfCancelled(control);
            traceContext = agentExecutionTraceService.startOrResumeTrace(task);
            agentExecutionTraceService.bindMdc(traceContext, traceContext.requestSpanId());
            rootBackendCallId = startBackendCall(
                    traceContext,
                    null,
                    "ChatOrchestrator.generate",
                    "ORCHESTRATOR",
                    "generate",
                    requestInput(task),
                    traceContext.requestSpanId()
            );
            String historyCallId = startBackendCall(
                    traceContext,
                    rootBackendCallId,
                    "ConversationMemoryService.history",
                    "SERVICE",
                    "history",
                    Map.of("sessionCode", task.sessionId(), "historyTurns", properties.getChat().getHistoryTurns()),
                    null
            );
            List<com.knowledgebox.domain.chat.ChatTurn> history = conversationMemoryService.history(
                    task.userId(),
                    task.sessionId(),
                    properties.getChat().getHistoryTurns()
            );
            completeBackendCall(traceContext, historyCallId, Map.of("turnCount", history.size()));
            agentExecutionTraceService.recordEvent(traceContext, traceContext.requestSpanId(), "request.received", Map.of(
                    "query", task.query(),
                    "historyTurns", history.size(),
                    "profile", task.profileCode(),
                    "chatModel", task.chatModelCode()
            ));

            if (properties.getChat().isStubResponses()) {
                String stubbedAnswerCallId = startBackendCall(
                        traceContext,
                        rootBackendCallId,
                        "ChatOrchestrator.stubbedAnswer",
                        "SERVICE",
                        "stubbedAnswer",
                        Map.of("query", task.query(), "chatModel", task.chatModelCode()),
                        null
                );
                boolean knowledgeBaseToolBound = agentCapabilityAssemblyService.hasKnowledgeBaseToolBound(task.profile().getId());
                ChatResponse stubbed = chatKnowledgeBasePlanService.stubbedAnswer(
                        task.sessionId(),
                        task.profile(),
                        task.query(),
                        task.chatModelCode(),
                        knowledgeBaseToolBound
                );
                completeBackendCall(traceContext, stubbedAnswerCallId, Map.of("answerLength", stubbed.answer().length(), "toolCalls", stubbed.toolCalls()));
                String answerStreamSpanId = agentExecutionTraceService.nextSpanIdValue();
                traceContext.setAnswerStreamSpanId(answerStreamSpanId);
                agentExecutionTraceService.startSpan(
                        traceContext,
                        traceContext.requestSpanId(),
                        answerStreamSpanId,
                        "answer.stream",
                        com.knowledgebox.domain.chat.AgentExecutionSpanType.STREAM,
                        Map.of("mode", "stub"),
                        Map.of("streamMode", "stub")
                );
                chatStreamDeltaService.streamTextDelta(
                        task,
                        reasoningSteps,
                        processDetails,
                        answerBuilder,
                        stubbed.answer(),
                        properties.getChat().getStreamDelay(),
                        () -> throwIfCancelled(control)
                );
                throwIfCancelled(control);
                finishSuccessfully(
                        task,
                        traceContext,
                        answerBuilder.toString(),
                        reasoningSteps,
                        processDetails,
                        stubbed.citations(),
                        stubbed.toolCalls(),
                        new EnumMap<>(EventType.class),
                        new QueryRoutingDecision(
                                true,
                                "STUB_MODE",
                                "stub-responses-enabled",
                                "stub"
                        ),
                        rootBackendCallId
                );
                return;
            }

            QueryExecutionPlan executionPlan = chatKnowledgeBasePlanService.prepareExecutionPlan(task, traceContext);
            QueryRoutingDecision routingDecision = executionPlan.routingDecision();

            String answerStreamSpanId = agentExecutionTraceService.nextSpanIdValue();
            traceContext.setAnswerStreamSpanId(answerStreamSpanId);
            agentExecutionTraceService.startSpan(
                    traceContext,
                    traceContext.requestSpanId(),
                    answerStreamSpanId,
                    "answer.stream",
                    com.knowledgebox.domain.chat.AgentExecutionSpanType.STREAM,
                    Map.of(
                            "chatModel", task.chatModelCode(),
                            "knowledgeBaseToolBound", executionPlan.knowledgeBaseToolBound(),
                            "enableKnowledgeBase", executionPlan.enableKnowledgeBaseTool(),
                            "historyTurns", history.size(),
                            "preRetrievedHits", executionPlan.retrievedChunks().size(),
                            "retrievalAttempted", executionPlan.retrievalAttempted()
                    ),
                    Map.of()
            );

            String createAgentCallId = startBackendCall(
                    traceContext,
                    rootBackendCallId,
                    "ChatOrchestrator.createReActAgent",
                    "SERVICE",
                    "createReActAgent",
                    Map.of(
                            "chatModel", task.chatModelCode(),
                            "knowledgeBaseToolBound", executionPlan.knowledgeBaseToolBound(),
                            "enableKnowledgeBase", executionPlan.enableKnowledgeBaseTool(),
                            "preRetrievedHits", executionPlan.retrievedChunks().size(),
                            "retrievalAttempted", executionPlan.retrievalAttempted()
                    ),
                    answerStreamSpanId
            );
            ReActAgent agent = createReActAgent(
                    task.profile(),
                    task.chatModelCode(),
                    executionPlan.enableKnowledgeBaseTool(),
                    traceContext,
                    exchangeRuntime
            );
            completeBackendCall(traceContext, createAgentCallId, Map.of("agentType", "ReActAgent"));
            AgentStreamState streamState = new AgentStreamState();
            final AgentExecutionTraceContext activeTraceContext = traceContext;
            String agentStreamCallId = startBackendCall(
                    traceContext,
                    rootBackendCallId,
                    "ReActAgent.stream",
                    "STREAM",
                    "stream",
                    Map.of(
                            "historyTurns", history.size(),
                            "chatModel", task.chatModelCode(),
                            "knowledgeBaseToolBound", executionPlan.knowledgeBaseToolBound(),
                            "preRetrievedHits", executionPlan.retrievedChunks().size()
                    ),
                    answerStreamSpanId
            );
            agent.stream(chatKnowledgeBasePlanService.toAgentScopeHistory(history, executionPlan), buildStreamOptions())
                    .doOnNext(event -> {
                        throwIfCancelled(control);
                        agentExecutionTraceService.bindMdc(activeTraceContext, activeTraceContext.answerStreamSpanId());
                        agentEventStreamService.consumeAgentEvent(
                                task,
                                activeTraceContext,
                                reasoningSteps,
                                processDetails,
                                answerBuilder,
                                streamState,
                                event,
                                this::updateProgress
                        );
                    })
                    .blockLast();
            completeBackendCall(traceContext, agentStreamCallId, Map.of("eventTypeCounts", toEventTypeCountPayload(streamState.eventTypeCounts)));
            throwIfCancelled(control);

            if (answerBuilder.isEmpty() && streamState.finalMessage != null) {
                String fallbackAnswer = streamState.finalMessage.getTextContent();
                if (fallbackAnswer != null && !fallbackAnswer.isBlank()) {
                    chatStreamDeltaService.streamTextDelta(
                            task,
                            reasoningSteps,
                            processDetails,
                            answerBuilder,
                            fallbackAnswer,
                            Duration.ofMillis(12),
                            () -> throwIfCancelled(control)
                    );
                }
            }

            List<String> toolCalls = exchangeRuntime.toolCalls();
            if (toolCalls.isEmpty()) {
                toolCalls = !streamState.toolCalls.isEmpty()
                        ? new ArrayList<>(streamState.toolCalls)
                        : extractToolCalls(streamState.finalMessage);
            }
            List<RetrievedChunk> retrievedChunks = chatKnowledgeBasePlanService.mergeRetrievedChunks(
                    executionPlan.retrievedChunks(),
                    exchangeRuntime.retrievals()
            );
            if (chatKnowledgeBasePlanService.shouldRunFallbackRetrieval(executionPlan, exchangeRuntime.retrievals())) {
                String fallbackRetrievalCallId = startBackendCall(
                        traceContext,
                        rootBackendCallId,
                        "KnowledgeBaseRetrievalService.search",
                        "SERVICE",
                        "search",
                        Map.of("query", task.query(), "topK", task.profile().getRetrievalTopK()),
                        null
                );
                retrievedChunks = knowledgeBaseRetrievalService.search(
                        task.query(),
                        task.profile().getRetrievalTopK(),
                        traceContext,
                        fallbackRetrievalCallId
                );
                completeBackendCall(traceContext, fallbackRetrievalCallId, Map.of("hits", retrievedChunks.size(), "mode", "fallback"));
            }
            chatProcessDetailFormatter.completeOpenReasoning(processDetails);
            throwIfCancelled(control);
            String finalAnswer = chatKnowledgeBasePlanService.finalizeAnswer(answerBuilder.toString(), executionPlan, retrievedChunks);
            if (!finalAnswer.equals(answerBuilder.toString())) {
                answerBuilder.setLength(0);
                answerBuilder.append(finalAnswer);
            }
            agentExecutionTraceService.endSpan(
                    traceContext,
                    answerStreamSpanId,
                    com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                    Map.of(
                            "answerLength", answerBuilder.length(),
                            "toolCalls", toolCalls,
                            "reasoningStepCount", reasoningSteps.size()
                    ),
                    Map.of(),
                    null
            );
            finishSuccessfully(
                    task,
                    traceContext,
                    finalAnswer,
                    reasoningSteps,
                    processDetails,
                    chatKnowledgeBasePlanService.toCitations(retrievedChunks),
                    toolCalls,
                    streamState.eventTypeCounts,
                    routingDecision,
                    rootBackendCallId
            );
        } catch (CancellationException exception) {
            log.info(
                    "Chat generation was cancelled. userId={}, sessionId={}, assistantMessageId={}, cancelledByUser={}",
                    task.userId(),
                    task.sessionId(),
                    task.assistantMessageId(),
                    control.cancelledByUser()
            );
            chatStreamBroker.complete(task.assistantMessageId());
            agentExecutionTraceService.cancelTrace(
                    traceContext,
                    control.cancelledByUser() ? "CHAT_CANCELLED_BY_USER" : "CHAT_CANCELLED",
                    control.cancelledByUser() ? "generation cancelled by user" : "generation cancelled"
            );
        } catch (Exception exception) {
            if (isCancellationException(exception)) {
                log.info(
                        "Chat generation interrupted by cancellation. userId={}, sessionId={}, assistantMessageId={}, cancelledByUser={}",
                        task.userId(),
                        task.sessionId(),
                        task.assistantMessageId(),
                        control.cancelledByUser()
                );
                chatStreamBroker.complete(task.assistantMessageId());
                agentExecutionTraceService.cancelTrace(
                        traceContext,
                        control.cancelledByUser() ? "CHAT_CANCELLED_BY_USER" : "CHAT_CANCELLED",
                        control.cancelledByUser() ? "generation cancelled by user" : "generation cancelled"
                );
                return;
            }
            if (isMessageMissing(exception)) {
                log.info(
                        "Chat generation stopped because target message/session no longer exists. userId={}, sessionId={}, assistantMessageId={}",
                        task.userId(),
                        task.sessionId(),
                        task.assistantMessageId()
                );
                chatStreamBroker.complete(task.assistantMessageId());
                agentExecutionTraceService.cancelTrace(traceContext);
                return;
            }
            log.error(
                    "Chat generation failed. userId={}, sessionId={}, assistantMessageId={}, chatModel={}",
                    task.userId(),
                    task.sessionId(),
                    task.assistantMessageId(),
                    task.chatModelCode(),
                    exception
            );
            try {
                chatProcessDetailFormatter.completeOpenReasoning(processDetails);
                String failPersistCallId = startBackendCall(
                        traceContext,
                        rootBackendCallId,
                        "ConversationMemoryService.failAssistantMessage",
                        "PERSISTENCE",
                        "failAssistantMessage",
                        Map.of("assistantMessageId", task.assistantMessageId()),
                        null
                );
                conversationMemoryService.failAssistantMessage(
                        task.userId(),
                        task.sessionId(),
                        task.assistantMessageId(),
                        answerBuilder.toString(),
                        reasoningSteps,
                        processDetails,
                        "回答生成失败，请稍后重试"
                );
                completeBackendCall(traceContext, failPersistCallId, Map.of("status", ChatMessageStatus.FAILED.name()));
                String publishErrorCallId = startBackendCall(
                        traceContext,
                        rootBackendCallId,
                        "ChatStreamBroker.publish",
                        "DELIVERY",
                        "publish",
                        Map.of("eventName", "error"),
                        null
                );
                chatStreamBroker.publish(
                        task.assistantMessageId(),
                        "error",
                        new ChatStreamEvent(
                                "error",
                                task.sessionId(),
                                task.assistantMessageId(),
                                "",
                                answerBuilder.toString(),
                                List.copyOf(reasoningSteps),
                                List.copyOf(processDetails),
                                List.of(),
                                List.of(),
                                ChatMessageStatus.FAILED.name(),
                                task.chatModelCode(),
                                "回答生成失败，请稍后重试"
                        )
                );
                completeBackendCall(traceContext, publishErrorCallId, Map.of("eventName", "error"));
            } catch (Exception updateException) {
                if (!isMessageMissing(updateException) && !isCancellationException(updateException)) {
                    throw updateException;
                }
            }
            String completeErrorCallId = startBackendCall(
                    traceContext,
                    rootBackendCallId,
                    "ChatStreamBroker.complete",
                    "DELIVERY",
                    "complete",
                    Map.of("assistantMessageId", task.assistantMessageId()),
                    null
            );
            chatStreamBroker.complete(task.assistantMessageId());
            completeBackendCall(traceContext, completeErrorCallId, Map.of("completed", true));
            agentExecutionTraceService.failTrace(traceContext, exception);
        } finally {
            agentExecutionTraceService.clearMdc();
        }
    }

    private void finishSuccessfully(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String answer,
            List<String> reasoningSteps,
            List<ChatProcessDetailView> processDetails,
            List<ChatCitationView> citations,
            List<String> toolCalls,
            Map<EventType, Integer> eventTypeCounts,
            QueryRoutingDecision routingDecision,
            String rootBackendCallId
    ) {
        String persistCallId = startBackendCall(
                traceContext,
                rootBackendCallId,
                "ConversationMemoryService.completeAssistantMessage",
                "PERSISTENCE",
                "completeAssistantMessage",
                Map.of("assistantMessageId", task.assistantMessageId()),
                null
        );
        conversationMemoryService.completeAssistantMessage(
                task.userId(),
                task.sessionId(),
                task.assistantMessageId(),
                answer,
                reasoningSteps,
                processDetails,
                citations,
                toolCalls
        );
        completeBackendCall(traceContext, persistCallId, Map.of("status", ChatMessageStatus.COMPLETED.name(), "answerLength", answer.length()));
        Map<String, Object> answerTracePayload = new LinkedHashMap<>();
        answerTracePayload.put("toolCalls", toolCalls);
        answerTracePayload.put("citations", citations.size());
        answerTracePayload.put("routing", routingPayload(routingDecision));
        answerTracePayload.put("eventTypeCounts", toEventTypeCountPayload(eventTypeCounts));
        String finalizeSpanId = agentExecutionTraceService.nextSpanIdValue();
        agentExecutionTraceService.startSpan(
                traceContext,
                traceContext.requestSpanId(),
                finalizeSpanId,
                "answer.finalize",
                com.knowledgebox.domain.chat.AgentExecutionSpanType.FINALIZE,
                Map.of(
                        "toolCalls", toolCalls,
                        "citationCount", citations.size(),
                        "reasoningStepCount", reasoningSteps.size()
                ),
                Map.of()
        );
        agentExecutionTraceService.recordEvent(traceContext, finalizeSpanId, "final.response", Map.of(
                "answer", answer,
                "toolCalls", toolCalls,
                "citations", citations,
                "reasoningSteps", reasoningSteps
        ));
        agentExecutionTraceService.endSpan(
                traceContext,
                finalizeSpanId,
                com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                Map.of(
                        "answer", answer,
                        "toolCalls", toolCalls,
                        "citations", citations,
                        "eventTypeCounts", toEventTypeCountPayload(eventTypeCounts)
                ),
                Map.of(),
                null
        );
        String publishDoneCallId = startBackendCall(
                traceContext,
                rootBackendCallId,
                "ChatStreamBroker.publish",
                "DELIVERY",
                "publish",
                Map.of("eventName", "done"),
                null
        );
        chatStreamBroker.publish(
                task.assistantMessageId(),
                "done",
                new ChatStreamEvent(
                        "done",
                        task.sessionId(),
                        task.assistantMessageId(),
                        "",
                        answer,
                        reasoningSteps,
                        processDetails,
                        citations,
                        toolCalls,
                        ChatMessageStatus.COMPLETED.name(),
                        task.chatModelCode(),
                        null
                )
        );
        completeBackendCall(traceContext, publishDoneCallId, Map.of("eventName", "done"));
        String completeStreamCallId = startBackendCall(
                traceContext,
                rootBackendCallId,
                "ChatStreamBroker.complete",
                "DELIVERY",
                "complete",
                Map.of("assistantMessageId", task.assistantMessageId()),
                null
        );
        chatStreamBroker.complete(task.assistantMessageId());
        completeBackendCall(traceContext, completeStreamCallId, Map.of("completed", true));
        agentExecutionTraceService.completeTrace(traceContext, answerTracePayload);
    }

    private String startBackendCall(
            AgentExecutionTraceContext traceContext,
            String parentCallId,
            String callName,
            String callType,
            String methodName,
            Map<String, ?> input,
            String relatedSpanId
    ) {
        if (traceContext == null) {
            return null;
        }
        String callId = agentExecutionTraceService.nextBackendSpanIdValue();
        agentExecutionTraceService.startBackendSpan(
                traceContext,
                parentCallId,
                callId,
                callName,
                callType,
                getServiceClass(callName),
                methodName,
                input,
                relatedSpanId
        );
        return callId;
    }

    private void completeBackendCall(AgentExecutionTraceContext traceContext, String callId, Map<String, ?> output) {
        if (traceContext == null || callId == null) {
            return;
        }
        agentExecutionTraceService.endBackendSpan(
                traceContext,
                callId,
                com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                output,
                null
        );
    }

    private String getServiceClass(String callName) {
        int separator = callName.indexOf('.');
        if (separator <= 0) {
            return callName;
        }
        return callName.substring(0, separator);
    }

    private Map<String, Object> requestInput(StreamTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionCode", task.sessionId());
        payload.put("assistantMessageId", task.assistantMessageId());
        payload.put("clientMessageId", task.clientMessageId());
        payload.put("query", task.query());
        payload.put("profileCode", task.profileCode());
        payload.put("chatModel", task.chatModelCode());
        return payload;
    }

    private void updateProgress(
            StreamTask task,
            List<String> reasoningSteps,
            List<ChatProcessDetailView> processDetails,
            String fullContent,
            String delta
    ) {
        conversationMemoryService.markAssistantStreaming(
                task.userId(),
                task.sessionId(),
                task.assistantMessageId(),
                fullContent,
                reasoningSteps,
                processDetails
        );
        chatStreamBroker.publish(
                task.assistantMessageId(),
                "message",
                new ChatStreamEvent(
                        "thinking",
                        task.sessionId(),
                        task.assistantMessageId(),
                        delta,
                        fullContent,
                        List.copyOf(reasoningSteps),
                        List.copyOf(processDetails),
                        List.of(),
                        List.of(),
                        ChatMessageStatus.STREAMING.name(),
                        task.chatModelCode(),
                        null
                )
        );
    }

    private StreamOptions buildStreamOptions() {
        return StreamOptions.builder()
                .eventTypes(
                        EventType.REASONING,
                        EventType.TOOL_RESULT,
                        EventType.HINT,
                        EventType.SUMMARY,
                        EventType.AGENT_RESULT,
                        EventType.ALL
                )
                .incremental(true)
                .includeReasoningChunk(true)
                .includeReasoningResult(false)
                .includeActingChunk(true)
                .includeSummaryChunk(true)
                .includeSummaryResult(false)
                .build();
    }

    private List<String> extractToolCalls(Msg response) {
        if (response == null) {
            return List.of();
        }
        List<ToolUseBlock> toolUseBlocks = response.getContentBlocks(ToolUseBlock.class);
        if (toolUseBlocks.isEmpty()) {
            return List.of();
        }
        return toolUseBlocks.stream()
                .map(ToolUseBlock::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private ReActAgent createReActAgent(
            com.knowledgebox.domain.agent.AgentProfileVersion profile,
            String chatModelCode,
            boolean enableKnowledgeBaseTool,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        AgentCapabilityAssemblyService.AgentRuntimeCapabilities capabilities = agentCapabilityAssemblyService.assemble(
                profile.getId(),
                enableKnowledgeBaseTool,
                traceContext,
                exchangeRuntime
        );
        List<io.agentscope.core.hook.Hook> hooks = new ArrayList<>();
        if (capabilities.hooks() != null && !capabilities.hooks().isEmpty()) {
            hooks.addAll(capabilities.hooks());
        }
        hooks.add(new AgentExecutionTraceHook(
                agentExecutionTraceService,
                traceContext,
                traceContext.requestSpanId(),
                traceContext::answerStreamSpanId,
                null,
                capabilities.subAgentToolMetadataByName()
        ));
        return chatModelFactory.createReActAgent(
                profile,
                chatModelCode,
                capabilities.toolkit(),
                capabilities.skillBox(),
                hooks,
                ToolExecutionContext.builder()
                        .register(AgentExecutionTraceContext.class, traceContext)
                        .register(ChatExchangeRuntime.class, exchangeRuntime)
                        .register(AgentRuntimeEnvironment.class, capabilities.runtimeEnvironment())
                        .build()
        );
    }

    private void throwIfCancelled(RunningChatTask control) {
        if (control.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("chat generation cancelled");
        }
    }

    private boolean isMessageMissing(Throwable throwable) {
        if (!(throwable instanceof com.knowledgebox.common.ApiException apiException)) {
            return false;
        }
        return "MESSAGE_NOT_FOUND".equals(apiException.getCode()) || "SESSION_NOT_FOUND".equals(apiException.getCode());
    }

    private boolean isCancellationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException || current instanceof CancellationException) {
                return true;
            }
            if (current instanceof SocketException socketException) {
                String message = socketException.getMessage();
                if (message != null && message.toLowerCase().contains("interrupt")) {
                    return true;
                }
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, Integer> toEventTypeCountPayload(Map<EventType, Integer> eventTypeCounts) {
        Map<String, Integer> payload = new LinkedHashMap<>();
        for (EventType eventType : EventType.values()) {
            payload.put(eventType.name(), eventTypeCounts.getOrDefault(eventType, 0));
        }
        return payload;
    }

    private Map<String, Object> routingPayload(QueryRoutingDecision routingDecision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enableKnowledgeBase", routingDecision.enableKnowledgeBase());
        payload.put("matchedRule", routingDecision.matchedRule());
        payload.put("reason", routingDecision.reason());
        payload.put("source", routingDecision.source());
        return payload;
    }
}
