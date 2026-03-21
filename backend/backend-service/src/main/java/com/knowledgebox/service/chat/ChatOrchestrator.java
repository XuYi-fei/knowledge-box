package com.knowledgebox.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentExecutionTracePageView;
import com.knowledgebox.api.ChatMessageRequest;
import com.knowledgebox.api.ChatProcessDetailView;
import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.api.ChatStreamEvent;
import com.knowledgebox.api.DebugChatMessageRequest;
import com.knowledgebox.api.PublicChatModelOptionView;
import com.knowledgebox.api.PublicChatOptionsView;
import com.knowledgebox.api.UserChatMessageView;
import com.knowledgebox.api.UserChatSessionDetailView;
import com.knowledgebox.api.UserChatSessionSummaryView;
import com.knowledgebox.api.UserDebugChatEntryView;
import com.knowledgebox.api.UserDebugChatOptionsView;
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
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final ConversationMemoryService conversationMemoryService;
    private final AgentCapabilityAssemblyService agentCapabilityAssemblyService;
    private final ChatStreamBroker chatStreamBroker;
    private final AssistantTurnAwaitService assistantTurnAwaitService;
    private final ChatMessagePayloadService chatMessagePayloadService;
    private final AgentExecutionTraceQueryService agentExecutionTraceQueryService;
    private final ChatProcessDetailFormatter chatProcessDetailFormatter;
    private final ChatKnowledgeBasePlanService chatKnowledgeBasePlanService;
    private final ChatGenerationExecutor chatGenerationExecutor;
    private final Map<String, RunningChatTask> runningTasks = new ConcurrentHashMap<>();

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
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:${spring.ai.alibaba.dashscope.api-key:}}") String dashScopeApiKey,
            @Value("${spring.ai.dashscope.base-url:}") String dashScopeBaseUrl
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.conversationMemoryService = conversationMemoryService;
        this.agentCapabilityAssemblyService = agentCapabilityAssemblyService;
        this.chatStreamBroker = chatStreamBroker;
        this.agentExecutionTraceQueryService = agentExecutionTraceQueryService;
        ChatModelFactory chatModelFactory = new ChatModelFactory(properties, dashScopeApiKey, dashScopeBaseUrl);
        ChatStreamDeltaService chatStreamDeltaService = new ChatStreamDeltaService(conversationMemoryService, chatStreamBroker);
        this.chatProcessDetailFormatter = new ChatProcessDetailFormatter(objectMapper);
        AgentEventStreamService agentEventStreamService = new AgentEventStreamService(
                chatStreamDeltaService,
                agentExecutionTraceService,
                chatProcessDetailFormatter
        );
        this.assistantTurnAwaitService = new AssistantTurnAwaitService(conversationMemoryService);
        this.chatMessagePayloadService = new ChatMessagePayloadService(conversationMemoryService);
        this.chatKnowledgeBasePlanService = new ChatKnowledgeBasePlanService(
                agentCapabilityAssemblyService,
                agentExecutionTraceService,
                knowledgeBaseRetrievalService
        );
        this.chatGenerationExecutor = new ChatGenerationExecutor(
                properties,
                conversationMemoryService,
                agentExecutionTraceService,
                knowledgeBaseRetrievalService,
                agentCapabilityAssemblyService,
                chatStreamBroker,
                chatModelFactory,
                chatStreamDeltaService,
                agentEventStreamService,
                chatProcessDetailFormatter,
                chatKnowledgeBasePlanService
        );
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
        RunningChatTask control = runningTasks.get(messageId);
        if (control != null) {
            control.cancelByUser();
            Thread worker = control.worker();
            if (worker != null) {
                worker.interrupt();
            }
        }
        ChatMessagePayload existingPayload = chatMessagePayloadService.resolvePayload(assistantTurn);
        List<ChatProcessDetailView> processDetails = new ArrayList<>(existingPayload.processDetails());
        chatProcessDetailFormatter.completeOpenReasoning(processDetails);
        ChatTurn cancelledTurn = conversationMemoryService.cancelAssistantMessage(
                userId,
                sessionId,
                messageId,
                assistantTurn.getContent(),
                existingPayload.reasoningSteps(),
                processDetails,
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
        RunningChatTask control = new RunningChatTask(task.userId(), task.sessionId());
        RunningChatTask existing = runningTasks.putIfAbsent(task.assistantMessageId(), control);
        if (existing != null) {
            return;
        }
        Thread worker = Thread.ofVirtual()
                .name("kb-chat-" + task.assistantMessageId())
                .unstarted(() -> {
                    try {
                        chatGenerationExecutor.generate(task, control);
                    } finally {
                        runningTasks.remove(task.assistantMessageId());
                    }
                });
        control.bind(worker);
        worker.start();
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
                payload.processDetails(),
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
                || (assistantTurn.getProcessDetailsJson() != null && !assistantTurn.getProcessDetailsJson().isBlank())
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
        RunningChatTask control = runningTasks.get(messageId);
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

    private QueryExecutionPlan prepareExecutionPlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            String rootBackendCallId
    ) {
        return chatKnowledgeBasePlanService.prepareExecutionPlan(task, traceContext);
    }

    private boolean shouldRunFallbackRetrieval(QueryExecutionPlan executionPlan, List<RetrievedChunk> runtimeRetrievedChunks) {
        return chatKnowledgeBasePlanService.shouldRunFallbackRetrieval(executionPlan, runtimeRetrievedChunks);
    }

    private boolean shouldRunFallbackRetrieval(boolean enableKnowledgeBase, List<RetrievedChunk> retrievedChunks) {
        return false;
    }

    private List<com.knowledgebox.api.ChatCitationView> toCitations(List<RetrievedChunk> chunks) {
        return chatKnowledgeBasePlanService.toCitations(chunks);
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

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("legacy wait interrupted");
        }
    }
}
