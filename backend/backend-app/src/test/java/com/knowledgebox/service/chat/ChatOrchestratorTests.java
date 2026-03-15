package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.api.ChatStreamEvent;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatOrchestratorTests {

    private static final String SESSION_ID = "session-1";
    private static final String ASSISTANT_MESSAGE_ID = "assistant-message-1";
    private static final String NEED_KB = "NEED_KB";
    private static final String NO_KB = "NO_KB";
    private static final String FORCE_DISABLE_MODEL_QUERY_REGEX =
            "(?i).*(你是什么模型|你是(什么|哪个)|你用的什么模型|what model are you|which model are you|who are you|what are you).*";

    private KnowledgeBoxProperties properties;
    private ConversationMemoryService conversationMemoryService;
    private ChatStreamBroker chatStreamBroker;
    private AgentEventStreamService agentEventStreamService;
    private ChatOrchestrator orchestrator;
    private ProbeChatOrchestrator probeChatOrchestrator;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeBoxProperties();
        properties.getChat().setRetrievalTriggerMode(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED);
        properties.getChat().getKnowledgeBaseRouting().setForceDisableRegexes(List.of(FORCE_DISABLE_MODEL_QUERY_REGEX));
        conversationMemoryService = mock(ConversationMemoryService.class);
        chatStreamBroker = mock(ChatStreamBroker.class);
        probeChatOrchestrator = new ProbeChatOrchestrator(
                properties,
                mock(AgentProfileVersionRepository.class),
                mock(ModelCatalogRepository.class),
                conversationMemoryService,
                mock(AgentExecutionTraceService.class),
                mock(KnowledgeBaseRetrievalService.class),
                emptyCapabilitiesAssembler(),
                chatStreamBroker,
                "fake-api-key",
                ""
        );
        agentEventStreamService = new AgentEventStreamService(
                new ChatStreamDeltaService(conversationMemoryService, chatStreamBroker),
                mock(AgentExecutionTraceService.class)
        );
        orchestrator = probeChatOrchestrator;
    }

    @Test
    void shouldSkipRoutingModelWhenRegexRuleMatched() throws Exception {
        AgentProfileVersion profile = new AgentProfileVersion();
        profile.setRoutingModel("qwen-plus");
        Object routingDecision = invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "你是什么模型",
                profile
        );
        boolean enableKnowledgeBase = (boolean) invokeNoArg(routingDecision, "enableKnowledgeBase");
        String source = (String) invokeNoArg(routingDecision, "source");
        String routingModel = (String) invokeNoArg(routingDecision, "routingModel");
        String retrievalTriggerMode = requiredRoutingTriggerMode(routingDecision);
        boolean shouldFallback = shouldRunFallbackRetrieval(routingDecision, List.of());

        assertThat(enableKnowledgeBase).isFalse();
        assertThat(source).isEqualTo("rule");
        assertThat(routingModel).isEqualTo("qwen-plus");
        assertThat(retrievalTriggerMode).isEqualTo(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED.name());
        assertThat(shouldFallback).isFalse();
        assertThat(probeChatOrchestrator.getInvokeRoutingModelCalls()).isZero();
    }

    @Test
    void shouldPreRetrieveButSkipFallbackWhenRoutingModelReturnsNoKb() throws Exception {
        probeChatOrchestrator.setRoutingModelOutput(NO_KB);
        AgentProfileVersion profile = new AgentProfileVersion();
        profile.setRoutingModel("qwen-plus");
        Object routingDecision = invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "解释一下 TCP 三次握手",
                profile
        );

        boolean enableKnowledgeBase = (boolean) invokeNoArg(routingDecision, "enableKnowledgeBase");
        String source = (String) invokeNoArg(routingDecision, "source");
        String reason = (String) invokeNoArg(routingDecision, "reason");
        String routingModel = (String) invokeNoArg(routingDecision, "routingModel");
        String routingModelOutput = (String) invokeNoArg(routingDecision, "routingModelOutput");
        String retrievalTriggerMode = requiredRoutingTriggerMode(routingDecision);
        Object ruleDecision = invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "你是什么模型",
                profile
        );
        String disabledTriggerMode = requiredRoutingTriggerMode(ruleDecision);
        boolean shouldFallback = shouldRunFallbackRetrieval(routingDecision, List.of());

        assertThat(enableKnowledgeBase).isFalse();
        assertThat(source).isEqualTo("model");
        assertThat(reason).isEqualTo("routing-model-classifier");
        assertThat(routingModel).isEqualTo("qwen-plus");
        assertThat(routingModelOutput).isEqualTo(NO_KB);
        assertThat(retrievalTriggerMode).isEqualTo(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED.name());
        assertThat(disabledTriggerMode).isEqualTo(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED.name());
        assertThat(shouldFallback).isFalse();
        assertThat(probeChatOrchestrator.getInvokeRoutingModelCalls()).isEqualTo(1);
    }

    @Test
    void shouldFallbackToNeedKbWhenRoutingModelOutputIsInvalid() throws Exception {
        probeChatOrchestrator.setRoutingModelOutput("UNKNOWN");
        AgentProfileVersion profile = new AgentProfileVersion();
        profile.setRoutingModel("qwen-plus");
        Object routingDecision = invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "如何设计幂等接口？",
                profile
        );

        boolean enableKnowledgeBase = (boolean) invokeNoArg(routingDecision, "enableKnowledgeBase");
        String source = (String) invokeNoArg(routingDecision, "source");
        String reason = (String) invokeNoArg(routingDecision, "reason");
        String routingModelOutput = (String) invokeNoArg(routingDecision, "routingModelOutput");
        String retrievalTriggerMode = requiredRoutingTriggerMode(routingDecision);
        probeChatOrchestrator.setRoutingModelOutput(NO_KB);
        Object noKbDecision = invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "如何设计幂等接口？",
                profile
        );
        String noKbTriggerMode = requiredRoutingTriggerMode(noKbDecision);
        boolean shouldFallback = shouldRunFallbackRetrieval(routingDecision, List.of());

        assertThat(enableKnowledgeBase).isTrue();
        assertThat(source).isEqualTo("model-fallback");
        assertThat(reason).isEqualTo("routing-model-invalid-output");
        assertThat(routingModelOutput).isEqualTo("UNKNOWN");
        assertThat(retrievalTriggerMode).isEqualTo(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED.name());
        assertThat(noKbTriggerMode).isEqualTo(KnowledgeBoxProperties.RetrievalTriggerMode.MODEL_ROUTED.name());
        assertThat(shouldFallback).isTrue();
        assertThat(probeChatOrchestrator.getInvokeRoutingModelCalls()).isEqualTo(2);
    }

    @Test
    void shouldSkipFallbackWhenAlwaysPreRetrieveModeIsEnabled() throws Exception {
        properties.getChat().setRetrievalTriggerMode(KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE);

        boolean shouldFallback = shouldRunFallbackRetrieval(
                new QueryRoutingDecision(
                        true,
                        "ALWAYS_PRE_RETRIEVE",
                        "chat.retrieval-trigger-mode=ALWAYS_PRE_RETRIEVE",
                        "pre-retrieval",
                        null,
                        "NO_HIT",
                        KnowledgeBoxProperties.RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE
                ),
                List.of()
        );

        assertThat(shouldFallback).isFalse();
    }

    @Test
    void shouldNotSetThinkingBudgetForDashScopeRoutingClassifierOptions() throws Exception {
        GenerateOptions options = (GenerateOptions) invokePrivateMethod(
                "buildRoutingClassifierGenerateOptions",
                new Class<?>[]{String.class},
                "qwen-plus"
        );
        Map<String, Object> bodyParams = options.getAdditionalBodyParams() == null
                ? Map.of()
                : options.getAdditionalBodyParams();

        assertThat(options.getThinkingBudget()).isNull();
        assertThat(bodyParams).doesNotContainKey("enable_thinking");
    }

    @Test
    void shouldKeepCompatibleRoutingClassifierThinkingDisabledWithoutBudget() throws Exception {
        GenerateOptions options = (GenerateOptions) invokePrivateMethod(
                "buildRoutingClassifierGenerateOptions",
                new Class<?>[]{String.class},
                "qwen3.5-plus"
        );
        Map<String, Object> bodyParams = options.getAdditionalBodyParams() == null
                ? Map.of()
                : options.getAdditionalBodyParams();

        assertThat(options.getThinkingBudget()).isNull();
        assertThat(bodyParams).containsEntry("enable_thinking", false);
    }

    @Test
    void shouldRequireRoutingModelFromProfileVersion() {
        AgentProfileVersion profile = new AgentProfileVersion();
        profile.setRoutingModel(" ");

        assertThatThrownBy(() -> invokePrivateMethod(
                "routeQuery",
                new Class<?>[]{String.class, AgentProfileVersion.class},
                "解释一下 TCP 三次握手",
                profile
        ))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Agent profile routingModel is blank: profile=unknown, version=null");
    }

    @Test
    void shouldConsumeAllAgentScopeEventTypesWithoutError() throws Exception {
        StreamTask streamTask = new StreamTask(
                1L,
                SESSION_ID,
                "client-message-1",
                "请解释事件流",
                ASSISTANT_MESSAGE_ID,
                "profile-1",
                new AgentProfileVersion(),
                "qwen-max"
        );
        AgentStreamState streamState = new AgentStreamState();
        List<String> reasoningSteps = new ArrayList<>();
        StringBuilder answerBuilder = new StringBuilder();
        AgentExecutionTraceContext traceContext = traceContext();

        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, reasoningEvent("先分析问题"), (task, steps, full, delta) -> {
        });
        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, toolResultEvent("searchKnowledgeBase"), (task, steps, full, delta) -> {
        });
        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, hintEvent("优先参考已有上下文"), (task, steps, full, delta) -> {
        });
        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, summaryEvent("这是总结输出"), (task, steps, full, delta) -> {
        });
        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, agentResultEvent("这是最终答案"), (task, steps, full, delta) -> {
        });
        agentEventStreamService.consumeAgentEvent(streamTask, traceContext, reasoningSteps, answerBuilder, streamState, allEvent("all marker"), (task, steps, full, delta) -> {
        });

        assertThat(reasoningSteps).anyMatch(step -> step.startsWith("思考中："));
        assertThat(reasoningSteps).anyMatch(step -> step.startsWith("上下文提示："));
        assertThat(answerBuilder).hasToString("这是总结输出");
        assertThat(streamState.toolCalls).contains("searchKnowledgeBase");
        assertThat(streamState.finalMessage).isNotNull();
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.REASONING, 0)).isEqualTo(1);
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.TOOL_RESULT, 0)).isEqualTo(1);
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.HINT, 0)).isEqualTo(1);
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.SUMMARY, 0)).isEqualTo(1);
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.AGENT_RESULT, 0)).isEqualTo(1);
        assertThat(streamState.eventTypeCounts.getOrDefault(EventType.ALL, 0)).isEqualTo(1);

        verify(conversationMemoryService, atLeastOnce()).markAssistantStreaming(
                eq(1L),
                eq(SESSION_ID),
                eq(ASSISTANT_MESSAGE_ID),
                anyString(),
                anyList()
        );
        verify(chatStreamBroker, atLeastOnce()).publish(
                eq(ASSISTANT_MESSAGE_ID),
                eq("message"),
                any(ChatStreamEvent.class)
        );
    }

    @Test
    void shouldSubscribeRequiredAgentScopeStreamEventTypes() throws Exception {
        StreamOptions options = (StreamOptions) invokePrivateMethod(
                "buildStreamOptions",
                new Class<?>[0]
        );

        assertThat(options.getEventTypes()).containsExactlyInAnyOrder(
                EventType.REASONING,
                EventType.TOOL_RESULT,
                EventType.HINT,
                EventType.SUMMARY,
                EventType.AGENT_RESULT,
                EventType.ALL
        );
        assertThat(options.isIncremental()).isTrue();
        assertThat(options.isIncludeReasoningChunk()).isTrue();
        assertThat(options.isIncludeSummaryChunk()).isTrue();
    }

    @Test
    void shouldNotTreatPlainAgentResultTextAsThinkingStep() {
        StreamTask streamTask = new StreamTask(
                1L,
                SESSION_ID,
                "client-message-1",
                "请解释事件流",
                ASSISTANT_MESSAGE_ID,
                "profile-1",
                new AgentProfileVersion(),
                "qwen-max"
        );
        AgentStreamState streamState = new AgentStreamState();
        List<String> reasoningSteps = new ArrayList<>();
        StringBuilder answerBuilder = new StringBuilder();
        AgentExecutionTraceContext traceContext = traceContext();

        agentEventStreamService.consumeAgentEvent(
                streamTask,
                traceContext,
                reasoningSteps,
                answerBuilder,
                streamState,
                agentResultEvent("这是最终答案"),
                (task, steps, full, delta) -> {
                }
        );

        assertThat(reasoningSteps).isEmpty();
        assertThat(answerBuilder).isEmpty();
        assertThat(streamState.finalMessage).isNotNull();
    }

    private Object invokePrivateMethod(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ChatOrchestrator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(orchestrator, args);
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private String requiredRoutingTriggerMode(Object routingDecision) throws Exception {
        try {
            Object value = invokeNoArg(routingDecision, "retrievalTriggerMode");
            assertThat(value).as("routingDecision.retrievalTriggerMode").isNotNull();
            return String.valueOf(value);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("QueryRoutingDecision should expose retrievalTriggerMode for pre-retrieval semantics", exception);
        }
    }

    private boolean shouldRunFallbackRetrieval(Object routingDecision, List<RetrievedChunk> retrievedChunks) throws Exception {
        for (Method method : ChatOrchestrator.class.getDeclaredMethods()) {
            if (!"shouldRunFallbackRetrieval".equals(method.getName())) {
                continue;
            }
            method.setAccessible(true);
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 2 || !List.class.isAssignableFrom(parameterTypes[1])) {
                continue;
            }
            if (parameterTypes[0] == boolean.class || parameterTypes[0] == Boolean.class) {
                return (boolean) method.invoke(orchestrator, invokeNoArg(routingDecision, "enableKnowledgeBase"), retrievedChunks);
            }
            if (parameterTypes[0] == String.class) {
                return (boolean) method.invoke(orchestrator, requiredRoutingTriggerMode(routingDecision), retrievedChunks);
            }
            if (parameterTypes[0].isInstance(routingDecision)) {
                return (boolean) method.invoke(orchestrator, routingDecision, retrievedChunks);
            }
        }
        throw new AssertionError("Unable to invoke shouldRunFallbackRetrieval with current ChatOrchestrator signature");
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Event reasoningEvent(String thinking) {
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ThinkingBlock.builder().thinking(thinking).build())
                .build();
        return new Event(EventType.REASONING, message, false);
    }

    private Event toolResultEvent(String toolName) {
        ToolResultBlock toolResultBlock = ToolResultBlock.of(
                "tool-result-1",
                toolName,
                TextBlock.builder().text("hit").build()
        );
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(toolResultBlock)
                .build();
        return new Event(EventType.TOOL_RESULT, message, false);
    }

    private Event hintEvent(String hintText) {
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(hintText).build())
                .build();
        return new Event(EventType.HINT, message, false);
    }

    private Event summaryEvent(String summaryText) {
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent(summaryText)
                .build();
        return new Event(EventType.SUMMARY, message, false);
    }

    private Event agentResultEvent(String answer) {
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent(answer)
                .build();
        return new Event(EventType.AGENT_RESULT, message, true);
    }

    private Event allEvent(String text) {
        Msg message = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent(text)
                .build();
        return new Event(EventType.ALL, message, false);
    }

    private AgentCapabilityAssemblyService emptyCapabilitiesAssembler() {
        AgentCapabilityAssemblyService assemblyService = mock(AgentCapabilityAssemblyService.class);
        when(assemblyService.assemble(nullable(Long.class), anyBoolean()))
                .thenReturn(new AgentCapabilityAssemblyService.AgentRuntimeCapabilities(new Toolkit(), null, List.of()));
        return assemblyService;
    }

    private AgentExecutionTraceContext traceContext() {
        AgentExecutionTraceContext traceContext = new AgentExecutionTraceContext("trace-test", "session-test", 1, 0, "span-request");
        traceContext.setAnswerStreamSpanId("span-stream");
        return traceContext;
    }

    private static final class ProbeChatOrchestrator extends ChatOrchestrator {
        private String routingModelOutput = NEED_KB;
        private int invokeRoutingModelCalls = 0;

        private ProbeChatOrchestrator(
                KnowledgeBoxProperties properties,
                AgentProfileVersionRepository agentProfileVersionRepository,
                ModelCatalogRepository modelCatalogRepository,
                ConversationMemoryService conversationMemoryService,
                AgentExecutionTraceService agentExecutionTraceService,
                KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
                AgentCapabilityAssemblyService agentCapabilityAssemblyService,
                ChatStreamBroker chatStreamBroker,
                String dashScopeApiKey,
                String dashScopeBaseUrl
        ) {
            super(
                    properties,
                    agentProfileVersionRepository,
                    modelCatalogRepository,
                    conversationMemoryService,
                    agentExecutionTraceService,
                    knowledgeBaseRetrievalService,
                    agentCapabilityAssemblyService,
                    chatStreamBroker,
                    dashScopeApiKey,
                    dashScopeBaseUrl
            );
        }

        @Override
        protected String invokeRoutingModel(String query, String routingModelCode) {
            invokeRoutingModelCalls++;
            return routingModelOutput;
        }

        private void setRoutingModelOutput(String routingModelOutput) {
            this.routingModelOutput = routingModelOutput;
        }

        private int getInvokeRoutingModelCalls() {
            return invokeRoutingModelCalls;
        }
    }
}
