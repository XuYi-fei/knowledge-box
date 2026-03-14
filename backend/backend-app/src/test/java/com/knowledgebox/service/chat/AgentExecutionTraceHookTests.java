package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.hook.PreSummaryEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentExecutionTraceHookTests {

    private AgentExecutionTraceService traceService;
    private AgentExecutionTraceContext traceContext;
    private AgentExecutionTraceHook hook;

    @BeforeEach
    void setUp() {
        traceService = mock(AgentExecutionTraceService.class);
        traceContext = new AgentExecutionTraceContext("trace-1", 1, 0, "span-request");
        traceContext.setAnswerStreamSpanId("span-answer");
        hook = new AgentExecutionTraceHook(traceService, traceContext);
    }

    @Test
    void shouldRecordPreReasoningEventWhenGenerateOptionsIsNull() {
        PreReasoningEvent event = new PreReasoningEvent(mock(Agent.class), "qwen-max", null, List.of());

        hook.onEvent(event).block();

        ArgumentCaptor<Map<String, ?>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(traceService).recordEvent(eq(traceContext), eq("span-answer"), eq("prompt.injected"), payloadCaptor.capture());
        Map<String, ?> payload = payloadCaptor.getValue();
        assertThat(payload.get("phase")).isEqualTo("reasoning");
        assertThat(payload.get("modelName")).isEqualTo("qwen-max");
        assertThat(payload.get("generateOptions")).isNull();
        assertThat(payload.get("inputMessages")).isEqualTo(List.of());
    }

    @Test
    void shouldRecordPreSummaryEventWhenGenerateOptionsIsNull() {
        PreSummaryEvent event = new PreSummaryEvent(mock(Agent.class), "qwen-max", null, List.of(), 8, 1);

        hook.onEvent(event).block();

        ArgumentCaptor<Map<String, ?>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(traceService).recordEvent(eq(traceContext), eq("span-answer"), eq("prompt.injected"), payloadCaptor.capture());
        Map<String, ?> payload = payloadCaptor.getValue();
        assertThat(payload.get("phase")).isEqualTo("summary");
        assertThat(payload.get("modelName")).isEqualTo("qwen-max");
        assertThat(payload.get("generateOptions")).isNull();
        assertThat(payload.get("inputMessages")).isEqualTo(List.of());
        assertThat(payload.get("currentIteration")).isEqualTo(1);
        assertThat(payload.get("maxIterations")).isEqualTo(8);
    }
}
