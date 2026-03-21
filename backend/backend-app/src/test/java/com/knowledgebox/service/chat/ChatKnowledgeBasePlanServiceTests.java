package com.knowledgebox.service.chat;

import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatKnowledgeBasePlanServiceTests {

    private AgentCapabilityAssemblyService agentCapabilityAssemblyService;
    private ChatKnowledgeBasePlanService chatKnowledgeBasePlanService;

    @BeforeEach
    void setUp() {
        agentCapabilityAssemblyService = mock(AgentCapabilityAssemblyService.class);
        chatKnowledgeBasePlanService = new ChatKnowledgeBasePlanService(
                agentCapabilityAssemblyService,
                mock(AgentExecutionTraceService.class),
                mock(KnowledgeBaseRetrievalService.class)
        );
    }

    @Test
    void shouldEnableKnowledgeBaseToolWhenAgentBindsKnowledgeBaseTool() throws Exception {
        AgentProfileVersion version = versionWithId(9L, "entry-agent");
        when(agentCapabilityAssemblyService.hasKnowledgeBaseToolBound(9L)).thenReturn(true);

        QueryExecutionPlan executionPlan = chatKnowledgeBasePlanService.prepareExecutionPlan(
                new StreamTask(1L, "session-1", "client-1", "mcp 是什么", "assistant-1", "entry-agent", version, "qwen-plus"),
                traceContext()
        );

        assertThat(executionPlan.knowledgeBaseToolBound()).isTrue();
        assertThat(executionPlan.enableKnowledgeBaseTool()).isTrue();
        assertThat(executionPlan.retrievalAttempted()).isFalse();
        assertThat(executionPlan.routingDecision().source()).isEqualTo("binding");
        assertThat(executionPlan.routingDecision().matchedRule()).isEqualTo("TOOL_BOUND");
    }

    @Test
    void shouldSkipKnowledgeBaseWhenAgentDoesNotBindKnowledgeBaseTool() throws Exception {
        AgentProfileVersion version = versionWithId(9L, "entry-agent");
        when(agentCapabilityAssemblyService.hasKnowledgeBaseToolBound(9L)).thenReturn(false);

        QueryExecutionPlan executionPlan = chatKnowledgeBasePlanService.prepareExecutionPlan(
                new StreamTask(1L, "session-1", "client-1", "mcp 是什么", "assistant-1", "entry-agent", version, "qwen-plus"),
                traceContext()
        );

        assertThat(executionPlan.knowledgeBaseToolBound()).isFalse();
        assertThat(executionPlan.enableKnowledgeBaseTool()).isFalse();
        assertThat(executionPlan.retrievalAttempted()).isFalse();
        assertThat(executionPlan.routingDecision().source()).isEqualTo("binding");
        assertThat(executionPlan.routingDecision().matchedRule()).isEqualTo("TOOL_NOT_BOUND");
    }

    @Test
    void shouldMergeCitationsByDocumentWhenMultipleChunksHitSameDocument() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(1L, "MCP 指南", "MCP 简介", "mcp-intro", "第一段摘要", 0.92, true),
                new RetrievedChunk(1L, "MCP 指南", "MCP 工作流", "mcp-flow", "第二段摘要", 0.88, true),
                new RetrievedChunk(2L, "Agent 总览", "Agent 基础", "agent-basic", "第三段摘要", 0.80, true)
        );

        List<com.knowledgebox.api.ChatCitationView> citations = chatKnowledgeBasePlanService.toCitations(chunks);

        assertThat(citations).hasSize(2);
        assertThat(citations.get(0).documentId()).isEqualTo(1L);
        assertThat(citations.get(0).documentTitle()).isEqualTo("MCP 指南");
        assertThat(citations.get(0).headingPath()).contains("MCP 简介").contains("MCP 工作流");
        assertThat(citations.get(0).snippet()).contains("第一段摘要").contains("第二段摘要");
        assertThat(citations.get(0).anchor()).isEqualTo("mcp-intro");
    }

    @Test
    void shouldPrefixFallbackAnswerWhenKnowledgeBaseReturnedNoEvidence() {
        QueryExecutionPlan executionPlan = new QueryExecutionPlan(
                new QueryRoutingDecision(true, "TOOL_BOUND", "searchKnowledgeBase tool is bound and enabled for this round", "binding"),
                List.of(),
                true,
                true,
                true
        );

        String finalAnswer = chatKnowledgeBasePlanService.finalizeAnswer("这是模型的通用回答。", executionPlan, List.of());

        assertThat(finalAnswer).startsWith("当前知识库未检索到足够相关的公开文档");
        assertThat(finalAnswer).contains("这是模型的通用回答。");
    }

    private AgentProfileVersion versionWithId(Long id, String profileCode) throws Exception {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);
        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        Field idField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(version, id);
        return version;
    }

    private AgentExecutionTraceContext traceContext() {
        AgentExecutionTraceContext traceContext = new AgentExecutionTraceContext("trace-test", "session-test", 1, 0, "span-request");
        traceContext.setAnswerStreamSpanId("span-stream");
        return traceContext;
    }
}
