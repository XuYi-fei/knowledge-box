package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
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
    private AgentProfileVersionRepository agentProfileVersionRepository;
    @Mock
    private AgentProfileVersionAgentBindingRepository agentBindingRepository;
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
    @Mock
    private AgentProfileVersionPolicyService policyService;
    @Mock
    private AgentExecutionTraceService agentExecutionTraceService;

    private AgentCapabilityAssemblyService service;

    @BeforeEach
    void setUp() {
        KnowledgeBaseSearchTool knowledgeBaseSearchTool = new KnowledgeBaseSearchTool(
                mock(KnowledgeBaseRetrievalService.class),
                mock(AgentTraceService.class),
                mock(AgentExecutionTraceService.class)
        );
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        service = new AgentCapabilityAssemblyService(
                properties,
                agentProfileVersionRepository,
                agentBindingRepository,
                toolBindingRepository,
                mcpBindingRepository,
                skillBindingRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillCatalogRepository,
                toolRuntimeFactoryService,
                skillPackageStorageService,
                secretCipherService,
                policyService,
                agentExecutionTraceService,
                knowledgeBaseSearchTool,
                new ObjectMapper(),
                "fake-api-key",
                ""
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
        when(agentBindingRepository.findByParentProfileVersionId(1L)).thenReturn(List.of());

        AgentProfile profile = new AgentProfile();
        profile.setCode("entry-agent");
        profile.setName("Entry Agent");
        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(1);
        try {
            java.lang.reflect.Field versionIdField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            versionIdField.setAccessible(true);
            versionIdField.set(version, 1L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(policyService.requireVersion(1L)).thenReturn(version);
        when(policyService.normalizeType(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

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
