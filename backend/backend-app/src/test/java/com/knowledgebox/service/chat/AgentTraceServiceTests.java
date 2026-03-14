package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.domain.chat.AgentTrace;
import com.knowledgebox.repository.AgentTraceRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentTraceServiceTests {

    @Test
    void shouldPersistTraceWhenSessionCodePresent() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        AgentTraceService service = new AgentTraceService(repository, new ObjectMapper());

        service.trace("session-1", "REQUEST_RECEIVED", Map.of("query", "hello"));

        ArgumentCaptor<AgentTrace> traceCaptor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(repository).save(traceCaptor.capture());
        AgentTrace trace = traceCaptor.getValue();
        assertThat(trace.getSessionCode()).isEqualTo("session-1");
        assertThat(trace.getStage()).isEqualTo("REQUEST_RECEIVED");
        assertThat(trace.getTraceCode()).startsWith("trace-");
        assertThat(trace.getPayloadJson()).contains("\"query\":\"hello\"");
    }

    @Test
    void shouldSkipPersistingWhenSessionCodeMissing() {
        AgentTraceRepository repository = mock(AgentTraceRepository.class);
        AgentTraceService service = new AgentTraceService(repository, new ObjectMapper());

        service.trace("  ", "RETRIEVAL_TOOL_EXECUTED", Map.of("query", "今天南京的温度"));
        service.trace(null, "RETRIEVAL_TOOL_EXECUTED", Map.of("query", "模型基座 版本号"));

        verify(repository, never()).save(any(AgentTrace.class));
    }
}
