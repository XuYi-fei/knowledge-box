package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseSearchToolTests {

    @Test
    void shouldTraceToolExecutionWhenSessionCodePresent() {
        KnowledgeBaseRetrievalService retrievalService = mock(KnowledgeBaseRetrievalService.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        KnowledgeBaseSearchTool tool = new KnowledgeBaseSearchTool(retrievalService, agentTraceService, mock(AgentExecutionTraceService.class));
        List<RetrievedChunk> chunks = List.of(new RetrievedChunk(1L, "doc", "h", "a", "snippet", 0.8, true));
        when(retrievalService.search(eq("query"), eq(3), eq(null), eq(null))).thenReturn(chunks);
        when(retrievalService.renderToolPayload(chunks)).thenReturn("payload");
        ChatExchangeRuntime exchangeRuntime = new ChatExchangeRuntime("session-1");

        String output = tool.searchKnowledgeBase("query", 3, null, exchangeRuntime);

        assertThat(output).isEqualTo("payload");
        verify(agentTraceService).trace(eq("session-1"), eq("RETRIEVAL_TOOL_EXECUTED"), anyMap());
    }

    @Test
    void shouldSkipTraceWhenSessionCodeMissing() {
        KnowledgeBaseRetrievalService retrievalService = mock(KnowledgeBaseRetrievalService.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        KnowledgeBaseSearchTool tool = new KnowledgeBaseSearchTool(retrievalService, agentTraceService, mock(AgentExecutionTraceService.class));
        List<RetrievedChunk> chunks = List.of(new RetrievedChunk(1L, "doc", "h", "a", "snippet", 0.8, true));
        when(retrievalService.search(eq("query"), eq(3), eq(null), eq(null))).thenReturn(chunks);
        when(retrievalService.renderToolPayload(chunks)).thenReturn("payload");
        ChatExchangeRuntime exchangeRuntime = new ChatExchangeRuntime(null);

        String output = tool.searchKnowledgeBase("query", 3, null, exchangeRuntime);

        assertThat(output).isEqualTo("payload");
        verifyNoInteractions(agentTraceService);
    }
}
