package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.IntegrationSecretCipherService;
import com.knowledgebox.service.integration.SkillPackageStorageService;
import com.knowledgebox.service.integration.ToolRuntimeFactoryService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentCapabilityAssemblyServiceTests {

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
    @Mock
    private ToolRuntimeFactoryService toolRuntimeFactoryService;
    @Mock
    private SkillPackageStorageService skillPackageStorageService;
    @Mock
    private IntegrationSecretCipherService secretCipherService;

    private AgentCapabilityAssemblyService service;

    @BeforeEach
    void setUp() {
        KnowledgeBaseSearchTool knowledgeBaseSearchTool = new KnowledgeBaseSearchTool(
                mock(KnowledgeBaseRetrievalService.class),
                mock(AgentTraceService.class)
        );
        service = new AgentCapabilityAssemblyService(
                toolBindingRepository,
                mcpBindingRepository,
                skillBindingRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillCatalogRepository,
                toolRuntimeFactoryService,
                skillPackageStorageService,
                secretCipherService,
                knowledgeBaseSearchTool,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCreateBuiltinGroupBeforeRegisteringKnowledgeBaseTool() {
        AgentCapabilityAssemblyService.AgentRuntimeCapabilities capabilities = service.assemble(null, true);

        assertThat(capabilities.toolkit().getToolGroup("builtin-kb")).isNotNull();
        assertThat(capabilities.toolkit().getToolNames()).contains("searchKnowledgeBase");
    }

    @Test
    void shouldCreateDynamicToolGroupBeforeRegisteringTool() {
        when(mcpBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(skillBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());

        AgentProfileVersionToolBinding binding = new AgentProfileVersionToolBinding();
        binding.setProfileVersionId(1L);
        binding.setToolId(101L);
        when(toolBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of(binding));

        ToolDefinition definition = new ToolDefinition();
        definition.setCode("http-search");
        definition.setEnabled(true);
        definition.setClassName("com.example.DynamicRuntimeTool");
        definition.setConfigJson("{}");
        when(toolDefinitionRepository.findById(101L)).thenReturn(Optional.of(definition));
        when(toolRuntimeFactoryService.createToolObject(anyString(), eq(null))).thenReturn(new DynamicRuntimeTool());

        AgentCapabilityAssemblyService.AgentRuntimeCapabilities capabilities = service.assemble(1L, false);

        assertThat(capabilities.toolkit().getToolGroup("tool-http-search")).isNotNull();
        assertThat(capabilities.toolkit().getToolNames()).contains("dynamicRuntimeEcho");
    }

    static class DynamicRuntimeTool {

        @Tool(name = "dynamicRuntimeEcho", description = "Echo input for runtime tool registration testing")
        public String echo(@ToolParam(name = "query", description = "Echo input") String query) {
            return query;
        }
    }
}
