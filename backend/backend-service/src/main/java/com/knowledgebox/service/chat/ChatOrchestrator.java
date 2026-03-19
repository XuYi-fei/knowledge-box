package com.knowledgebox.service.chat;

import com.knowledgebox.api.*;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.domain.agent.ProfileStatus;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import com.knowledgebox.domain.chat.ChatSession;
import com.knowledgebox.domain.chat.ChatTurn;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.service.admin.AgentExecutionTraceQueryService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final KnowledgeBoxProperties properties;
    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final ConversationMemoryService conversationMemoryService;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;
    private final AgentCapabilityAssemblyService agentCapabilityAssemblyService;
    private final ChatStreamBroker chatStreamBroker;
    private final ChatModelFactory chatModelFactory;
    private final KnowledgeBaseRoutingService routingService;
    private final ChatStreamDeltaService chatStreamDeltaService;
    private final AgentEventStreamService agentEventStreamService;
    private final AssistantTurnAwaitService assistantTurnAwaitService;
    private final ChatMessagePayloadService chatMessagePayloadService;
    private final AgentExecutionTraceQueryService agentExecutionTraceQueryService;
    private final Map<String, RunningTaskControl> runningTasks = new ConcurrentHashMap<>();

    public ChatOrchestrator(
            KnowledgeBoxProperties properties,
            AgentProfileVersionRepository agentProfileVersionRepository,
            ModelCatalogRepository modelCatalogRepository,
            ConversationMemoryService conversationMemoryService,
            AgentExecutionTraceService agentExecutionTraceService,
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
            AgentCapabilityAssemblyService agentCapabilityAssemblyService,
            ChatStreamBroker chatStreamBroker,
            AgentExecutionTraceQueryService agentExecutionTraceQueryService,
            @Value("${spring.ai.dashscope.api-key:${spring.ai.alibaba.dashscope.api-key:}}") String dashScopeApiKey,
            @Value("${spring.ai.dashscope.base-url:}") String dashScopeBaseUrl
    ) {
        this.properties = properties;
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.conversationMemoryService = conversationMemoryService;
        this.agentExecutionTraceService = agentExecutionTraceService;
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
        this.agentCapabilityAssemblyService = agentCapabilityAssemblyService;
        this.chatStreamBroker = chatStreamBroker;
        this.agentExecutionTraceQueryService = agentExecutionTraceQueryService;
        this.chatModelFactory = new ChatModelFactory(properties, dashScopeApiKey, dashScopeBaseUrl);
        this.routingService = new KnowledgeBaseRoutingService(properties, chatModelFactory);
        this.chatStreamDeltaService = new ChatStreamDeltaService(conversationMemoryService, chatStreamBroker);
        this.agentEventStreamService = new AgentEventStreamService(chatStreamDeltaService, agentExecutionTraceService);
        this.assistantTurnAwaitService = new AssistantTurnAwaitService(conversationMemoryService);
        this.chatMessagePayloadService = new ChatMessagePayloadService(conversationMemoryService);
    }

    public PublicChatOptionsView options() {
        AgentProfileVersion profile = publishedProfile();
        List<ModelCatalog> publicChatModels = modelCatalogRepository
                .findAllByModelTypeAndEnabledTrueAndPublicSelectableTrueOrderByDisplayNameAsc(ModelType.CHAT);
        String defaultChatModel = publicChatModels.stream()
                .filter(modelCatalog -> Boolean.TRUE.equals(modelCatalog.getDefaultForPublic()))
                .map(ModelCatalog::getCode)
                .findFirst()
                .orElse(null);

        return new PublicChatOptionsView(
                profile.getChatModel(),
                defaultChatModel,
                publicChatModels.stream()
                        .map(modelCatalog -> new PublicChatModelOptionView(
                                modelCatalog.getCode(),
                                modelCatalog.getDisplayName(),
                                modelCatalog.getProvider(),
                                modelCatalog.getDescription()
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<UserChatSessionSummaryView> sessions(Long userId) {
        return conversationMemoryService.listSessions(userId, publishedProfile().getProfile().getCode());
    }

    @Transactional(readOnly = true)
    public UserChatSessionDetailView sessionDetail(Long userId, String sessionId) {
        return conversationMemoryService.sessionDetail(userId, sessionId, publishedProfile().getProfile().getCode());
    }

    @Transactional
    public SseEmitter stream(Long userId, ChatMessageRequest request) {
        AgentProfileVersion profile = publishedProfile();
        String profileCode = profile.getProfile().getCode();
        String chatModelCode = resolveChatModel(request.chatModel(), profile);
        conversationMemoryService.ensureSession(
                userId,
                request.sessionId(),
                profileCode,
                chatModelCode,
                request.query()
        );
        conversationMemoryService.appendUserMessage(userId, request.sessionId(), request.clientMessageId(), request.query());

        ChatTurn assistantTurn = findAssistantTurn(userId, request.sessionId(), request.clientMessageId(), chatModelCode);
        SseEmitter emitter = subscribe(userId, request.sessionId(), assistantTurn.getMessageCode());
        startGenerationIfNeeded(new StreamTask(
                userId,
                request.sessionId(),
                request.clientMessageId(),
                request.query(),
                assistantTurn.getMessageCode(),
                profileCode,
                profile,
                chatModelCode
        ));
        return emitter;
    }

    public SseEmitter resume(Long userId, String sessionId, String messageId) {
        ChatTurn assistantTurn = conversationMemoryService.loadMessage(userId, sessionId, messageId);
        SseEmitter emitter = subscribe(sessionId, assistantTurn);
        restartGenerationIfNeeded(userId, assistantTurn);
        return emitter;
    }

    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        cancelSessionTasks(userId, sessionId);
        conversationMemoryService.deleteSession(userId, sessionId, publishedProfile().getProfile().getCode());
    }

    @Transactional
    public UserChatMessageView stop(Long userId, String sessionId, String messageId) {
        verifySessionProfile(userId, sessionId, publishedProfile().getProfile().getCode());
        return stopInternal(userId, sessionId, messageId);
    }

    private UserChatMessageView stopInternal(Long userId, String sessionId, String messageId) {
        ChatTurn assistantTurn = conversationMemoryService.loadMessage(userId, sessionId, messageId);
        if (!"assistant".equals(assistantTurn.getRole())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "CHAT_MESSAGE_NOT_ASSISTANT", "当前消息不是助手回答");
        }
        if (assistantTurn.getStatus() == ChatMessageStatus.COMPLETED
                || assistantTurn.getStatus() == ChatMessageStatus.FAILED
                || assistantTurn.getStatus() == ChatMessageStatus.CANCELLED) {
            return conversationMemoryService.messageView(userId, sessionId, messageId);
        }
        RunningTaskControl control = runningTasks.get(messageId);
        if (control != null) {
            control.cancelByUser();
            Thread worker = control.worker();
            if (worker != null) {
                worker.interrupt();
            }
        }
        ChatTurn cancelledTurn = conversationMemoryService.cancelAssistantMessage(
                userId,
                sessionId,
                messageId,
                assistantTurn.getContent(),
                chatMessagePayloadService.resolvePayload(assistantTurn).reasoningSteps(),
                "已停止回答"
        );
        chatStreamBroker.publish(
                messageId,
                "done",
                snapshotEvent("stopped", sessionId, cancelledTurn, "")
        );
        chatStreamBroker.complete(messageId);
        return conversationMemoryService.messageView(userId, sessionId, messageId);
    }

    public ChatResponse answerLegacy(Long userId, com.knowledgebox.api.ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : request.sessionId();
        ChatMessageRequest messageRequest = new ChatMessageRequest(
                sessionId,
                request.clientTraceId() == null || request.clientTraceId().isBlank() ? java.util.UUID.randomUUID().toString() : request.clientTraceId(),
                request.query(),
                request.chatModel()
        );
        stream(userId, messageRequest);
        ChatTurn assistantTurn = assistantTurnAwaitService.awaitTerminalAssistantTurn(
                userId,
                sessionId,
                messageRequest.clientMessageId(),
                this::sleep
        );
        return chatMessagePayloadService.toLegacyResponse(sessionId, assistantTurn);
    }

    public UserDebugChatOptionsView debugOptions(Long userId) {
        List<ModelCatalog> publicChatModels = modelCatalogRepository
                .findAllByModelTypeAndEnabledTrueAndPublicSelectableTrueOrderByDisplayNameAsc(ModelType.CHAT);
        String defaultChatModel = publicChatModels.stream()
                .filter(modelCatalog -> Boolean.TRUE.equals(modelCatalog.getDefaultForPublic()))
                .map(ModelCatalog::getCode)
                .findFirst()
                .orElse(null);
        Map<String, UserDebugChatEntryView> entries = new LinkedHashMap<>();
        for (AgentProfileVersion version : agentProfileVersionRepository
                .findAllByAgentTypeAndStatusAndPublicDebugTrueOrderByProfile_NameAscVersionNumberDesc(AgentProfileVersionType.ENTRY, ProfileStatus.PUBLISHED)) {
            entries.putIfAbsent(version.getProfile().getCode(), new UserDebugChatEntryView(
                    version.getProfile().getCode(),
                    version.getProfile().getName(),
                    version.getProfile().getDescription(),
                    true,
                    true,
                    false
            ));
        }
        for (String profileCode : conversationMemoryService.listActiveProfileCodes(userId)) {
            AgentProfileVersion version = latestProfileVersion(profileCode);
            if (version != null && policyAgentType(version) == AgentProfileVersionType.MAIN) {
                continue;
            }
            if (entries.containsKey(profileCode)) {
                UserDebugChatEntryView existing = entries.get(profileCode);
                entries.put(profileCode, new UserDebugChatEntryView(
                        existing.profileCode(),
                        existing.profileName(),
                        existing.description(),
                        existing.available(),
                        existing.canStartNewConversation(),
                    true
                ));
                continue;
            }
            entries.put(profileCode, new UserDebugChatEntryView(
                    profileCode,
                    version == null ? profileCode : version.getProfile().getName(),
                    version == null ? null : version.getProfile().getDescription(),
                    false,
                    false,
                    true
            ));
        }
        return new UserDebugChatOptionsView(
                defaultChatModel,
                publicChatModels.stream()
                        .map(modelCatalog -> new PublicChatModelOptionView(
                                modelCatalog.getCode(),
                                modelCatalog.getDisplayName(),
                                modelCatalog.getProvider(),
                                modelCatalog.getDescription()
                        ))
                        .toList(),
                List.copyOf(entries.values())
        );
    }

    @Transactional(readOnly = true)
    public List<UserChatSessionSummaryView> debugSessions(Long userId, String profileCode) {
        requireDebugEntryAccessible(userId, profileCode);
        return conversationMemoryService.listSessions(userId, profileCode);
    }

    @Transactional(readOnly = true)
    public UserChatSessionDetailView debugSessionDetail(Long userId, String profileCode, String sessionId) {
        requireDebugEntryAccessible(userId, profileCode);
        return conversationMemoryService.sessionDetail(userId, sessionId, profileCode);
    }

    @Transactional(readOnly = true)
    public AgentExecutionTracePageView debugTraces(Long userId, String profileCode, String sessionId, int page, int pageSize) {
        requireDebugEntryAccessible(userId, profileCode);
        return agentExecutionTraceQueryService.traces(null, null, profileCode, sessionId, userId, null, page, pageSize);
    }

    @Transactional
    public SseEmitter debugStream(Long userId, DebugChatMessageRequest request) {
        AgentProfileVersion profile = requireAvailableDebugProfile(request.profileCode());
        String chatModelCode = resolveChatModel(request.chatModel(), profile);
        conversationMemoryService.ensureSession(
                userId,
                request.sessionId(),
                request.profileCode(),
                chatModelCode,
                request.query()
        );
        conversationMemoryService.appendUserMessage(userId, request.sessionId(), request.clientMessageId(), request.query());

        ChatTurn assistantTurn = findAssistantTurn(userId, request.sessionId(), request.clientMessageId(), chatModelCode);
        SseEmitter emitter = subscribe(userId, request.sessionId(), assistantTurn.getMessageCode());
        startGenerationIfNeeded(new StreamTask(
                userId,
                request.sessionId(),
                request.clientMessageId(),
                request.query(),
                assistantTurn.getMessageCode(),
                request.profileCode(),
                profile,
                chatModelCode
        ));
        return emitter;
    }

    @Transactional
    public void debugDeleteSession(Long userId, String profileCode, String sessionId) {
        verifySessionProfile(userId, sessionId, profileCode);
        cancelSessionTasks(userId, sessionId);
        conversationMemoryService.deleteSession(userId, sessionId, profileCode);
    }

    public SseEmitter debugResume(Long userId, String profileCode, String sessionId, String messageId) {
        verifySessionProfile(userId, sessionId, profileCode);
        ChatTurn assistantTurn = conversationMemoryService.loadMessage(userId, sessionId, messageId);
        SseEmitter emitter = subscribe(sessionId, assistantTurn);
        restartGenerationIfNeeded(userId, assistantTurn);
        return emitter;
    }

    @Transactional
    public UserChatMessageView debugStop(Long userId, String profileCode, String sessionId, String messageId) {
        verifySessionProfile(userId, sessionId, profileCode);
        return stopInternal(userId, sessionId, messageId);
    }

    private SseEmitter subscribe(Long userId, String sessionId, String messageId) {
        ChatTurn assistantTurn = conversationMemoryService.loadMessage(userId, sessionId, messageId);
        return subscribe(sessionId, assistantTurn);
    }

    private SseEmitter subscribe(String sessionId, ChatTurn assistantTurn) {
        SseEmitter emitter = chatStreamBroker.subscribe(assistantTurn.getMessageCode());
        if (shouldSendSnapshot(assistantTurn)) {
            sendDirect(emitter, "snapshot", snapshotEvent("snapshot", sessionId, assistantTurn, ""));
        }
        if (assistantTurn.getStatus() == ChatMessageStatus.COMPLETED) {
            sendDirect(emitter, "done", snapshotEvent("done", sessionId, assistantTurn, ""));
            emitter.complete();
        } else if (assistantTurn.getStatus() == ChatMessageStatus.CANCELLED) {
            sendDirect(emitter, "done", snapshotEvent("stopped", sessionId, assistantTurn, ""));
            emitter.complete();
        } else if (assistantTurn.getStatus() == ChatMessageStatus.FAILED) {
            sendDirect(emitter, "error", snapshotEvent("error", sessionId, assistantTurn, ""));
            emitter.complete();
        }
        return emitter;
    }

    private ChatTurn findAssistantTurn(Long userId, String sessionId, String clientMessageId, String chatModelCode) {
        ChatTurn existingAssistantTurn = conversationMemoryService.findAssistantByClientMessageId(userId, sessionId, clientMessageId);
        return existingAssistantTurn != null
                ? existingAssistantTurn
                : conversationMemoryService.createAssistantPlaceholder(userId, sessionId, chatModelCode);
    }

    private void startGenerationIfNeeded(StreamTask task) {
        ChatTurn turn = conversationMemoryService.loadMessage(task.userId(), task.sessionId(), task.assistantMessageId());
        if (turn.getStatus() == ChatMessageStatus.COMPLETED
                || turn.getStatus() == ChatMessageStatus.FAILED
                || turn.getStatus() == ChatMessageStatus.CANCELLED) {
            return;
        }
        RunningTaskControl control = new RunningTaskControl(task.userId(), task.sessionId());
        RunningTaskControl existing = runningTasks.putIfAbsent(task.assistantMessageId(), control);
        if (existing != null) {
            return;
        }
        Thread worker = Thread.ofVirtual()
                .name("kb-chat-" + task.assistantMessageId())
                .unstarted(() -> generate(task, control));
        control.bind(worker);
        worker.start();
    }

    private void generate(StreamTask task, RunningTaskControl control) {
        List<String> reasoningSteps = new ArrayList<>();
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
            reasoningSteps.add("已接收用户问题，正在分析检索意图");
            updateProgress(task, reasoningSteps, answerBuilder.toString(), "thinking");

            String historyCallId = startBackendCall(
                    traceContext,
                    rootBackendCallId,
                    "ConversationMemoryService.history",
                    "SERVICE",
                    "history",
                    Map.of("sessionCode", task.sessionId(), "historyTurns", properties.getChat().getHistoryTurns()),
                    null
            );
            List<ChatTurn> history = conversationMemoryService.history(task.userId(), task.sessionId(), properties.getChat().getHistoryTurns());
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
                ChatResponse stubbed = stubbedAnswer(task.sessionId(), task.profile(), task.query(), task.chatModelCode());
                completeBackendCall(traceContext, stubbedAnswerCallId, Map.of("answerLength", stubbed.answer().length(), "toolCalls", stubbed.toolCalls()));
                reasoningSteps.add("测试桩模式已完成检索，正在按流式方式输出答案");
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
                        stubbed.citations(),
                        stubbed.toolCalls(),
                        new java.util.EnumMap<>(EventType.class),
                        new QueryRoutingDecision(
                                true,
                                "stub-mode",
                                "stub-responses-enabled",
                                "stub",
                                resolveRoutingModel(task.profile()),
                                null,
                                resolveRetrievalTriggerMode()
                        ),
                        rootBackendCallId
                );
                return;
            }

            reasoningSteps.add("已装载历史上下文，准备执行 ReAct Agent");
            updateProgress(task, reasoningSteps, answerBuilder.toString(), "thinking");

            QueryExecutionPlan executionPlan = prepareExecutionPlan(task, traceContext, rootBackendCallId);
            QueryRoutingDecision routingDecision = executionPlan.routingDecision();
            if (executionPlan.retrievalAttempted()) {
                reasoningSteps.add(executionPlan.retrievedChunks().isEmpty()
                        ? "已执行前置知识库检索，但未命中足够相关的公开文档"
                        : "已执行前置知识库检索，命中 " + executionPlan.retrievedChunks().size() + " 条公开知识片段");
            } else {
                reasoningSteps.add("查询路由[" + routingDecision.source() + "]："
                        + (routingDecision.enableKnowledgeBase() ? "启用知识库工具" : "跳过知识库工具"));
            }
            updateProgress(task, reasoningSteps, answerBuilder.toString(), "thinking");

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
                            "enableKnowledgeBase", executionPlan.enableKnowledgeBaseTool(),
                            "historyTurns", history.size(),
                            "retrievalTriggerMode", resolveRetrievalTriggerMode().name(),
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
                    !executionPlan.retrievedChunks().isEmpty(),
                    executionPlan.retrievalAttempted() && executionPlan.retrievedChunks().isEmpty(),
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
                            "preRetrievedHits", executionPlan.retrievedChunks().size()
                    ),
                    answerStreamSpanId
            );
            agent.stream(toAgentScopeHistory(history, executionPlan), buildStreamOptions())
                    .doOnNext(event -> {
                        throwIfCancelled(control);
                        agentExecutionTraceService.bindMdc(activeTraceContext, activeTraceContext.answerStreamSpanId());
                        agentEventStreamService.consumeAgentEvent(
                                task,
                                activeTraceContext,
                                reasoningSteps,
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
            List<RetrievedChunk> retrievedChunks = mergeRetrievedChunks(
                    executionPlan.retrievedChunks(),
                    exchangeRuntime.retrievals()
            );
            if (shouldRunFallbackRetrieval(executionPlan, exchangeRuntime.retrievals())) {
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
            List<String> completedReasoningSteps = buildReasoningSteps(streamState.finalMessage, toolCalls, retrievedChunks);
            reasoningSteps.clear();
            reasoningSteps.addAll(completedReasoningSteps);
            throwIfCancelled(control);
            String finalAnswer = finalizeAnswer(answerBuilder.toString(), executionPlan, retrievedChunks);
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
                    toCitations(retrievedChunks),
                    toolCalls,
                    streamState.eventTypeCounts,
                    routingDecision,
                    rootBackendCallId
            );
        } catch (ChatGenerationCancelledException exception) {
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
            runningTasks.remove(task.assistantMessageId());
            agentExecutionTraceService.clearMdc();
        }
    }

    private void finishSuccessfully(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String answer,
            List<String> reasoningSteps,
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
                        "thinking",
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

    private ChatStreamEvent snapshotEvent(String type, String sessionId, ChatTurn assistantTurn, String delta) {
        ChatMessagePayload payload = chatMessagePayloadService.resolvePayload(assistantTurn);
        return new ChatStreamEvent(
                type,
                sessionId,
                assistantTurn.getMessageCode(),
                delta,
                assistantTurn.getContent(),
                payload.reasoningSteps(),
                payload.citations(),
                payload.toolCalls(),
                assistantTurn.getStatus().name(),
                assistantTurn.getModelCode(),
                assistantTurn.getErrorMessage()
        );
    }

    private void sendDirect(SseEmitter emitter, String eventName, ChatStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
        } catch (IOException exception) {
            emitter.complete();
        }
    }

    private boolean shouldSendSnapshot(ChatTurn assistantTurn) {
        return assistantTurn.getStatus() != ChatMessageStatus.PENDING
                || (assistantTurn.getContent() != null && !assistantTurn.getContent().isBlank())
                || (assistantTurn.getReasoningStepsJson() != null && !assistantTurn.getReasoningStepsJson().isBlank())
                || (assistantTurn.getErrorMessage() != null && !assistantTurn.getErrorMessage().isBlank());
    }

    private void restartGenerationIfNeeded(Long userId, ChatTurn assistantTurn) {
        if (assistantTurn.getStatus() != ChatMessageStatus.PENDING && assistantTurn.getStatus() != ChatMessageStatus.STREAMING) {
            return;
        }
        ChatSession session = conversationMemoryService.loadSession(userId, assistantTurn.getSessionCode());
        AgentProfileVersion profile = latestProfileVersion(session.getActiveProfileCode());
        if (profile == null) {
            log.warn(
                    "Skip restarting assistant stream because profile no longer exists. userId={}, sessionId={}, assistantMessageId={}, profileCode={}",
                    userId,
                    assistantTurn.getSessionCode(),
                    assistantTurn.getMessageCode(),
                    session.getActiveProfileCode()
            );
            return;
        }
        String query = conversationMemoryService.findUserQueryForAssistantMessage(
                userId,
                assistantTurn.getSessionCode(),
                assistantTurn.getMessageCode()
        );
        if (query == null || query.isBlank()) {
            log.warn(
                    "Skip restarting assistant stream because user query cannot be found. userId={}, sessionId={}, assistantMessageId={}",
                    userId,
                    assistantTurn.getSessionCode(),
                    assistantTurn.getMessageCode()
            );
            return;
        }
        String chatModelCode = assistantTurn.getModelCode() == null || assistantTurn.getModelCode().isBlank()
                ? profile.getChatModel()
                : assistantTurn.getModelCode();
        startGenerationIfNeeded(new StreamTask(
                userId,
                assistantTurn.getSessionCode(),
                "resume-" + assistantTurn.getMessageCode(),
                query,
                assistantTurn.getMessageCode(),
                session.getActiveProfileCode(),
                profile,
                chatModelCode
        ));
    }

    private void cancelSessionTasks(Long userId, String sessionId) {
        conversationMemoryService.listActiveAssistantMessageCodes(userId, sessionId)
                .forEach(this::cancelMessageTask);
        runningTasks.entrySet().stream()
                .filter(entry -> userId.equals(entry.getValue().userId()) && sessionId.equals(entry.getValue().sessionId()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::cancelMessageTask);
    }

    private void cancelMessageTask(String messageId) {
        RunningTaskControl control = runningTasks.get(messageId);
        if (control == null) {
            chatStreamBroker.complete(messageId);
            return;
        }
        control.cancel();
        Thread worker = control.worker();
        if (worker != null) {
            worker.interrupt();
        }
        chatStreamBroker.complete(messageId);
    }

    private void throwIfCancelled(RunningTaskControl control) {
        if (control.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new ChatGenerationCancelledException();
        }
    }

    private boolean isMessageMissing(Throwable throwable) {
        if (!(throwable instanceof ApiException apiException)) {
            return false;
        }
        return "MESSAGE_NOT_FOUND".equals(apiException.getCode()) || "SESSION_NOT_FOUND".equals(apiException.getCode());
    }

    private boolean isCancellationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ChatGenerationCancelledException
                    || current instanceof InterruptedException
                    || current instanceof CancellationException) {
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

    private List<String> buildReasoningSteps(Msg response, List<String> toolCalls, List<RetrievedChunk> retrievedChunks) {
        List<String> reasoningSteps = new ArrayList<>();
        reasoningSteps.add("已分析问题并构建回答计划");
        String thinkingSummary = extractThinkingSummary(response);
        if (!thinkingSummary.isBlank()) {
            reasoningSteps.add("模型思考摘要：" + thinkingSummary);
        }
        if (!toolCalls.isEmpty()) {
            reasoningSteps.add("已调用工具：" + String.join("、", toolCalls));
        }
        if (!retrievedChunks.isEmpty()) {
            reasoningSteps.add("已检索到 " + retrievedChunks.size() + " 条知识片段并用于生成答案");
        } else {
            reasoningSteps.add("未命中知识片段，答案基于当前可用上下文生成");
        }
        return reasoningSteps;
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

    private String extractThinkingSummary(Msg response) {
        if (response == null) {
            return "";
        }
        List<ThinkingBlock> thinkingBlocks = response.getContentBlocks(ThinkingBlock.class);
        if (thinkingBlocks.isEmpty()) {
            return "";
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (ThinkingBlock block : thinkingBlocks) {
            String thinking = block.getThinking();
            if (thinking == null || thinking.isBlank()) {
                continue;
            }
            if (summaryBuilder.length() > 0) {
                summaryBuilder.append(' ');
            }
            summaryBuilder.append(thinking.strip());
            if (summaryBuilder.length() >= 120) {
                break;
            }
        }
        String summary = summaryBuilder.toString().trim();
        if (summary.length() <= 120) {
            return summary;
        }
        return summary.substring(0, 120) + "...";
    }


    private AgentProfileVersion publishedProfile() {
        return agentProfileVersionRepository.findFirstByPublishedTrueAndAgentTypeOrderByUpdatedAtDesc(AgentProfileVersionType.MAIN)
                .orElseThrow(() -> new IllegalStateException("No published agent profile version found"));
    }

    private AgentProfileVersion latestProfileVersion(String profileCode) {
        if (profileCode == null || profileCode.isBlank()) {
            return null;
        }
        return agentProfileVersionRepository.findByProfile_CodeOrderByVersionNumberDesc(profileCode.trim()).stream()
                .findFirst()
                .orElse(null);
    }

    private AgentProfileVersion requireAvailableDebugProfile(String profileCode) {
        AgentProfileVersion profile = latestProfileVersion(profileCode);
        if (!isAvailableDebugEntry(profile)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "DEBUG_AGENT_UNAVAILABLE", "当前 Agent 调试入口不可用，无法发起新对话");
        }
        return profile;
    }

    private void requireDebugEntryAccessible(Long userId, String profileCode) {
        AgentProfileVersion profile = latestProfileVersion(profileCode);
        if (profile != null && policyAgentType(profile) == AgentProfileVersionType.MAIN) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "DEBUG_AGENT_NOT_FOUND", "调试入口不存在");
        }
        if (isAvailableDebugEntry(profile)) {
            return;
        }
        boolean hasHistory = conversationMemoryService.listActiveProfileCodes(userId).stream()
                .anyMatch(profileCode::equals);
        if (!hasHistory) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "DEBUG_AGENT_NOT_FOUND", "调试入口不存在");
        }
    }

    private boolean isAvailableDebugEntry(AgentProfileVersion profile) {
        return profile != null
                && policyAgentType(profile) == AgentProfileVersionType.ENTRY
                && profile.getStatus() == ProfileStatus.PUBLISHED
                && Boolean.TRUE.equals(profile.getPublicDebug());
    }

    private AgentProfileVersionType policyAgentType(AgentProfileVersion profile) {
        return profile == null ? null : profile.getAgentType();
    }

    private void verifySessionProfile(Long userId, String sessionId, String profileCode) {
        ChatSession session = conversationMemoryService.loadSession(userId, sessionId);
        if (!profileCode.equals(session.getActiveProfileCode())) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "对话会话不存在");
        }
    }

    private String resolveChatModel(String requestedChatModel, AgentProfileVersion profile) {
        if (requestedChatModel == null || requestedChatModel.isBlank()) {
            return profile.getChatModel();
        }
        return modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrueAndPublicSelectableTrue(
                        requestedChatModel.trim(),
                        ModelType.CHAT
                )
                .map(ModelCatalog::getCode)
                .orElseThrow(() -> new IllegalArgumentException("Publicly selectable chat model not found: " + requestedChatModel));
    }

    private ReActAgent createReActAgent(
            AgentProfileVersion profile,
            String chatModelCode,
            boolean enableKnowledgeBaseTool,
            boolean hasInjectedKnowledgeContext,
            boolean retrievalAttemptedWithoutEvidence,
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
                enableKnowledgeBaseTool,
                hasInjectedKnowledgeContext,
                retrievalAttemptedWithoutEvidence,
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

    private List<Msg> toAgentScopeHistory(List<ChatTurn> history, QueryExecutionPlan executionPlan) {
        List<Msg> messages = new ArrayList<>();
        if (executionPlan.retrievalAttempted()) {
            messages.add(Msg.builder()
                    .name("knowledge-base-context")
                    .role(MsgRole.SYSTEM)
                    .textContent(renderInjectedKnowledgeContext(executionPlan))
                    .build());
        }
        for (ChatTurn turn : history) {
            MsgRole role = switch (turn.getRole()) {
                case "user" -> MsgRole.USER;
                case "assistant" -> MsgRole.ASSISTANT;
                default -> null;
            };
            if (role == null) {
                continue;
            }
            messages.add(Msg.builder()
                    .role(role)
                    .textContent(turn.getContent() == null ? "" : turn.getContent())
                    .build());
        }
        return messages;
    }

    private ChatResponse stubbedAnswer(String sessionId, AgentProfileVersion profile, String query, String chatModelCode) {
        List<RetrievedChunk> retrievedChunks = knowledgeBaseRetrievalService.search(query, profile.getRetrievalTopK());
        List<String> toolCalls = retrievedChunks.isEmpty() ? List.of() : List.of("searchKnowledgeBase");
        String answer;
        if (retrievedChunks.isEmpty()) {
            answer = "当前测试模式未检索到匹配知识片段，因此无法给出基于知识库的正式回答。";
        } else {
            StringBuilder builder = new StringBuilder("当前运行在测试桩模式，但检索链路已生效。命中的知识片段如下：\n");
            for (RetrievedChunk chunk : retrievedChunks) {
                builder.append("- ")
                        .append(chunk.documentTitle())
                        .append(" / ")
                        .append(chunk.headingPath())
                        .append("：")
                        .append(chunk.snippet())
                        .append('\n');
            }
            answer = builder.toString().trim();
        }
        return new ChatResponse(sessionId, answer, toCitations(retrievedChunks), toolCalls, chatModelCode);
    }

    private List<ChatCitationView> toCitations(List<RetrievedChunk> chunks) {
        record CitationAggregate(
                Long documentId,
                String documentTitle,
                LinkedHashSet<String> headingPaths,
                String firstAnchor,
                LinkedHashSet<String> snippets
        ) {
        }

        Map<Long, CitationAggregate> aggregates = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (!chunk.publicVisible()) {
                continue;
            }
            CitationAggregate aggregate = aggregates.computeIfAbsent(
                    chunk.documentId(),
                    ignored -> new CitationAggregate(
                            chunk.documentId(),
                            chunk.documentTitle(),
                            new LinkedHashSet<>(),
                            chunk.anchor(),
                            new LinkedHashSet<>()
                    )
            );
            if (hasText(chunk.headingPath())) {
                aggregate.headingPaths().add(chunk.headingPath().trim());
            }
            if (hasText(chunk.snippet())) {
                aggregate.snippets().add(chunk.snippet().trim());
            }
        }

        return aggregates.values().stream()
                .map(aggregate -> new ChatCitationView(
                        aggregate.documentId(),
                        aggregate.documentTitle(),
                        summarizeCitationText(aggregate.headingPaths(), "未分节"),
                        aggregate.firstAnchor(),
                        summarizeCitationText(aggregate.snippets(), "")
                ))
                .toList();
    }

    private String summarizeCitationText(LinkedHashSet<String> values, String fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        List<String> orderedValues = new ArrayList<>(values);
        if (orderedValues.size() == 1) {
            return orderedValues.getFirst();
        }
        StringBuilder builder = new StringBuilder(orderedValues.getFirst());
        int previewCount = Math.min(orderedValues.size(), 2);
        for (int index = 1; index < previewCount; index++) {
            builder.append(" / ").append(orderedValues.get(index));
        }
        if (orderedValues.size() > previewCount) {
            builder.append(" 等").append(orderedValues.size()).append("处");
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private QueryRoutingDecision routeQuery(String query, AgentProfileVersion profile) {
        return routingService.routeQuery(query, profile, this::invokeRoutingModel);
    }

    private String resolveRoutingModel(AgentProfileVersion profile) {
        return routingService.resolveRoutingModel(profile);
    }

    protected String invokeRoutingModel(String query, String routingModelCode) {
        return routingService.invokeRoutingModel(query, routingModelCode);
    }

    private GenerateOptions buildRoutingClassifierGenerateOptions(String routingModelCode) {
        return routingService.buildRoutingClassifierGenerateOptions(routingModelCode);
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
        payload.put("routingModel", routingDecision.routingModel());
        payload.put("routingModelOutput", routingDecision.routingModelOutput());
        payload.put(
                "retrievalTriggerMode",
                routingDecision.retrievalTriggerMode() == null ? null : routingDecision.retrievalTriggerMode().name()
        );
        return payload;
    }

    private QueryExecutionPlan prepareExecutionPlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String rootBackendCallId
    ) {
        if (resolveRetrievalTriggerMode() == KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE) {
            return prepareAlwaysPreRetrievePlan(task, traceContext, rootBackendCallId);
        }
        return prepareModelRoutedPlan(task, traceContext, rootBackendCallId);
    }

    private QueryExecutionPlan prepareAlwaysPreRetrievePlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String rootBackendCallId
    ) {
        String routingSpanId = agentExecutionTraceService.nextSpanIdValue();
        agentExecutionTraceService.startSpan(
                traceContext,
                traceContext.requestSpanId(),
                routingSpanId,
                "query.route",
                com.knowledgebox.domain.chat.AgentExecutionSpanType.ROUTING,
                Map.of(
                        "query", task.query(),
                        "retrievalTriggerMode", KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE.name()
                ),
                Map.of()
        );
        String preRetrieveCallId = startBackendCall(
                traceContext,
                rootBackendCallId,
                "KnowledgeBaseRetrievalService.search",
                "SERVICE",
                "search",
                Map.of(
                        "query", task.query(),
                        "topK", task.profile().getRetrievalTopK(),
                        "mode", "pre-retrieval"
                ),
                routingSpanId
        );
        List<RetrievedChunk> retrievedChunks = knowledgeBaseRetrievalService.search(
                task.query(),
                task.profile().getRetrievalTopK(),
                traceContext,
                preRetrieveCallId
        );
        completeBackendCall(traceContext, preRetrieveCallId, Map.of("hits", retrievedChunks.size(), "mode", "pre-retrieval"));
        QueryRoutingDecision routingDecision = new QueryRoutingDecision(
                !retrievedChunks.isEmpty(),
                KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE.name(),
                "chat.retrieval-trigger-mode=ALWAYS_PRE_RETRIEVE",
                "pre-retrieval",
                null,
                retrievedChunks.isEmpty() ? "NO_HIT" : "HIT",
                KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE
        );
        Map<String, Object> routePayload = routingPayload(routingDecision);
        routePayload.put("retrievalTriggerMode", KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE.name());
        routePayload.put("preRetrievedHits", retrievedChunks.size());
        agentExecutionTraceService.endSpan(
                traceContext,
                routingSpanId,
                com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                routePayload,
                Map.of(),
                null
        );
        agentExecutionTraceService.recordEvent(traceContext, traceContext.requestSpanId(), "query.routed", routePayload);
        return new QueryExecutionPlan(routingDecision, retrievedChunks, false, true);
    }

    private QueryExecutionPlan prepareModelRoutedPlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String rootBackendCallId
    ) {
        String routingSpanId = agentExecutionTraceService.nextSpanIdValue();
        agentExecutionTraceService.startSpan(
                traceContext,
                traceContext.requestSpanId(),
                routingSpanId,
                "query.route",
                com.knowledgebox.domain.chat.AgentExecutionSpanType.ROUTING,
                Map.of("query", task.query()),
                Map.of()
        );
        String routeCallId = startBackendCall(
                traceContext,
                rootBackendCallId,
                "KnowledgeBaseRoutingService.routeQuery",
                "SERVICE",
                "routeQuery",
                Map.of("query", task.query()),
                routingSpanId
        );
        QueryRoutingDecision routingDecision = routeQuery(task.query(), task.profile());
        completeBackendCall(traceContext, routeCallId, routingPayload(routingDecision));
        agentExecutionTraceService.endSpan(
                traceContext,
                routingSpanId,
                com.knowledgebox.domain.chat.AgentExecutionStatus.COMPLETED,
                routingPayload(routingDecision),
                Map.of(),
                null
        );
        Map<String, Object> routePayload = routingPayload(routingDecision);
        routePayload.put("query", task.query());
        agentExecutionTraceService.recordEvent(traceContext, traceContext.requestSpanId(), "query.routed", routePayload);
        return new QueryExecutionPlan(routingDecision, List.of(), routingDecision.enableKnowledgeBase(), false);
    }

    private KnowledgeBoxProperties.RetrievalTriggerMode resolveRetrievalTriggerMode() {
        KnowledgeBoxProperties.RetrievalTriggerMode mode = properties.getChat().getRetrievalTriggerMode();
        return mode == null ? KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE : mode;
    }

    private String renderInjectedKnowledgeContext(QueryExecutionPlan executionPlan) {
        if (!executionPlan.retrievalAttempted()) {
            return "";
        }
        if (executionPlan.retrievedChunks().isEmpty()) {
            return """
                    A knowledge-base retrieval was executed for the current user request before model generation.
                    Result: no sufficiently relevant public snippets were found.
                    If you answer from general knowledge, explicitly mention that the current knowledge base did not provide supporting evidence in this round.
                    """;
        }
        StringBuilder builder = new StringBuilder("""
                The following knowledge-base snippets were retrieved before model generation.
                Use them as the primary evidence for repository-specific facts.

                """);
        for (int index = 0; index < executionPlan.retrievedChunks().size(); index++) {
            RetrievedChunk chunk = executionPlan.retrievedChunks().get(index);
            builder.append(index + 1)
                    .append(". [")
                    .append(chunk.documentTitle())
                    .append("] ")
                    .append(chunk.headingPath())
                    .append(" #")
                    .append(chunk.anchor())
                    .append('\n')
                    .append(chunk.snippet())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private List<RetrievedChunk> mergeRetrievedChunks(List<RetrievedChunk> preRetrieved, List<RetrievedChunk> runtimeRetrieved) {
        LinkedHashMap<String, RetrievedChunk> merged = new LinkedHashMap<>();
        if (preRetrieved != null) {
            for (RetrievedChunk chunk : preRetrieved) {
                merged.put(chunk.documentId() + "::" + chunk.anchor(), chunk);
            }
        }
        if (runtimeRetrieved != null) {
            for (RetrievedChunk chunk : runtimeRetrieved) {
                merged.put(chunk.documentId() + "::" + chunk.anchor(), chunk);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private boolean shouldRunFallbackRetrieval(QueryExecutionPlan executionPlan, List<RetrievedChunk> runtimeRetrievedChunks) {
        return resolveRetrievalTriggerMode() == KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED
                && executionPlan.enableKnowledgeBaseTool()
                && (runtimeRetrievedChunks == null || runtimeRetrievedChunks.isEmpty());
    }

    private boolean shouldRunFallbackRetrieval(boolean enableKnowledgeBase, List<RetrievedChunk> retrievedChunks) {
        return resolveRetrievalTriggerMode() == KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED
                && enableKnowledgeBase
                && (retrievedChunks == null || retrievedChunks.isEmpty());
    }

    private String finalizeAnswer(String answer, QueryExecutionPlan executionPlan, List<RetrievedChunk> retrievedChunks) {
        if (answer == null) {
            return "";
        }
        if (!executionPlan.retrievalAttempted() || (retrievedChunks != null && !retrievedChunks.isEmpty())) {
            return answer;
        }
        String normalized = answer.replaceAll("\\s+", "");
        if (normalized.contains("知识库") && (normalized.contains("未检索到") || normalized.contains("证据不足") || normalized.contains("未提供支持"))) {
            return answer;
        }
        return "当前知识库未检索到足够相关的公开文档，以下回答基于通用知识给出，仅供参考。\n\n" + answer;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatGenerationCancelledException();
        }
    }

    private static final class ChatGenerationCancelledException extends RuntimeException {
    }

    private static final class RunningTaskControl {
        private final Long userId;
        private final String sessionId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean cancelledByUser = new AtomicBoolean(false);
        private volatile Thread worker;

        private RunningTaskControl(Long userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }

        private void bind(Thread worker) {
            this.worker = worker;
        }

        private void cancel() {
            this.cancelled.set(true);
        }

        private void cancelByUser() {
            this.cancelled.set(true);
            this.cancelledByUser.set(true);
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private boolean cancelledByUser() {
            return cancelledByUser.get();
        }

        private Long userId() {
            return userId;
        }

        private String sessionId() {
            return sessionId;
        }

        private Thread worker() {
            return worker;
        }
    }

    private record QueryExecutionPlan(
            QueryRoutingDecision routingDecision,
            List<RetrievedChunk> retrievedChunks,
            boolean enableKnowledgeBaseTool,
            boolean retrievalAttempted
    ) {
    }

}
