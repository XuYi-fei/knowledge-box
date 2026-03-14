package com.knowledgebox.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentProfileBindingServiceTests {

    @Mock
    private AgentProfileVersionRepository agentProfileVersionRepository;
    @Mock
    private AgentProfileVersionToolBindingRepository toolBindingRepository;
    @Mock
    private AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    @Mock
    private AgentProfileVersionSkillBindingRepository skillBindingRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private McpServerConfigRepository mcpServerConfigRepository;
    @Mock
    private SkillBindingRepository skillCatalogRepository;

    private AgentProfileBindingService service;

    @BeforeEach
    void setUp() {
        service = new AgentProfileBindingService(
                agentProfileVersionRepository,
                toolBindingRepository,
                mcpBindingRepository,
                skillBindingRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillCatalogRepository,
                new ObjectMapper()
        );
    }

    @Test
    void shouldNormalizeAndReplaceBindingsWithoutDuplicates() {
        when(agentProfileVersionRepository.existsById(1L)).thenReturn(true);

        ToolDefinition tool = new ToolDefinition();
        tool.setCode("http-search");
        java.lang.reflect.Field toolIdField;
        try {
            toolIdField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            toolIdField.setAccessible(true);
            toolIdField.set(tool, 1L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(toolDefinitionRepository.findByCode("http-search")).thenReturn(Optional.of(tool));

        SkillBinding skill = new SkillBinding();
        skill.setCode("retrieval-critic");
        try {
            java.lang.reflect.Field skillIdField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            skillIdField.setAccessible(true);
            skillIdField.set(skill, 2L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(skillCatalogRepository.findByCode("retrieval-critic")).thenReturn(Optional.of(skill));

        McpServerConfig mcp = new McpServerConfig();
        mcp.setCode("local-mcp");
        try {
            java.lang.reflect.Field mcpIdField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            mcpIdField.setAccessible(true);
            mcpIdField.set(mcp, 3L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(mcpServerConfigRepository.findByCode("local-mcp")).thenReturn(Optional.of(mcp));

        when(toolBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(skillBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(mcpBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());

        when(toolBindingRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<com.knowledgebox.domain.integration.AgentProfileVersionToolBinding> bindings = invocation.getArgument(0);
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).getProfileVersionId()).isEqualTo(1L);
            assertThat(bindings.get(0).getToolId()).isEqualTo(1L);
            return bindings;
        });
        when(skillBindingRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding> bindings = invocation.getArgument(0);
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).getProfileVersionId()).isEqualTo(1L);
            assertThat(bindings.get(0).getSkillId()).isEqualTo(2L);
            return bindings;
        });
        when(mcpBindingRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding> bindings = invocation.getArgument(0);
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).getProfileVersionId()).isEqualTo(1L);
            assertThat(bindings.get(0).getMcpId()).isEqualTo(3L);
            assertThat(bindings.get(0).getEnableToolsJson()).isEqualTo("[\"http-search\"]");
            assertThat(bindings.get(0).getDisableToolsJson()).isEqualTo("[]");
            return bindings;
        });

        UpdateAgentProfileVersionBindingsRequest request = new UpdateAgentProfileVersionBindingsRequest(
                List.of("http-search", "HTTP-SEARCH", " "),
                List.of("retrieval-critic", "RETRIEVAL-CRITIC"),
                List.of(
                        new AgentProfileVersionMcpBindingView("local-mcp", List.of("http-search", "HTTP-SEARCH"), List.of()),
                        new AgentProfileVersionMcpBindingView("LOCAL-MCP", List.of("http-search"), List.of("http-search")),
                        new AgentProfileVersionMcpBindingView(" ", List.of(), List.of())
                )
        );

        var response = service.updateBindings(1L, request);

        assertThat(response.profileVersionId()).isEqualTo(1L);
        assertThat(response.toolCodes()).isEmpty();
        assertThat(response.skillCodes()).isEmpty();
        assertThat(response.mcpBindings()).isEmpty();
        verify(toolBindingRepository).deleteByProfileVersionId(1L);
        verify(skillBindingRepository).deleteByProfileVersionId(1L);
        verify(mcpBindingRepository).deleteByProfileVersionId(1L);
    }
}
