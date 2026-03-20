package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CompatibleSubAgentToolTests {

    @Test
    void shouldAcceptQueryAliasForSubAgentMessage() {
        Agent agent = mockAgent("child-agent", "Child Agent", "child-description", "grounded result");
        CompatibleSubAgentTool tool = new CompatibleSubAgentTool(() -> agent, SubAgentConfig.builder()
                .toolName("agent_web_search_agent_v1")
                .description("child agent tool")
                .forwardEvents(false)
                .build());

        ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                .input(Map.of("query", "latest java news"))
                .build()).block();

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isNotEmpty();
        assertThat(result.getOutput().getFirst()).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) result.getOutput().getFirst()).getText()).contains("grounded result");
        Map<String, Object> parameters = tool.getParameters();
        assertThat(parameters).containsEntry("type", "object");
        assertThat(parameters.toString()).contains("message");
        assertThat(parameters.toString()).contains("query");
    }

    @Test
    void shouldReturnClearErrorWhenMessageAndQueryAreBothMissing() {
        Agent agent = mockAgent("child-agent", "Child Agent", "child-description", "grounded result");
        CompatibleSubAgentTool tool = new CompatibleSubAgentTool(() -> agent, SubAgentConfig.builder()
                .toolName("agent_web_search_agent_v1")
                .description("child agent tool")
                .forwardEvents(false)
                .build());

        ToolResultBlock result = tool.callAsync(ToolCallParam.builder()
                .input(Map.of("session_id", "session-1"))
                .build()).block();

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isNotEmpty();
        assertThat(result.getOutput().getFirst()).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) result.getOutput().getFirst()).getText()).contains("message").contains("query");
    }

    private Agent mockAgent(String agentId, String name, String description, String responseText) {
        Agent agent = mock(Agent.class);
        when(agent.getAgentId()).thenReturn(agentId);
        when(agent.getName()).thenReturn(name);
        when(agent.getDescription()).thenReturn(description);
        when(agent.call(anyList())).thenReturn(Mono.just(Msg.builder()
                .name(name)
                .role(MsgRole.ASSISTANT)
                .textContent(responseText)
                .build()));
        when(agent.call(anyList(), org.mockito.ArgumentMatchers.<Class<?>>any())).thenReturn(Mono.just(Msg.builder()
                .name(name)
                .role(MsgRole.ASSISTANT)
                .textContent(responseText)
                .build()));
        when(agent.call(anyList(), org.mockito.ArgumentMatchers.any(com.fasterxml.jackson.databind.JsonNode.class))).thenReturn(Mono.just(Msg.builder()
                .name(name)
                .role(MsgRole.ASSISTANT)
                .textContent(responseText)
                .build()));
        when(agent.stream(anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(Flux.empty());
        when(agent.stream(anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<Class<?>>any())).thenReturn(Flux.empty());
        when(agent.observe(org.mockito.ArgumentMatchers.any(Msg.class))).thenReturn(Mono.empty());
        when(agent.observe(anyList())).thenReturn(Mono.empty());
        return agent;
    }
}
