package com.knowledgebox.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionEnvVar;
import com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionEnvVarRepository;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.chat.AgentRuntimeEnvironmentResolver;
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
    private AgentProfileVersionAgentBindingRepository agentBindingRepository;
    @Mock
    private AgentProfileVersionToolBindingRepository toolBindingRepository;
    @Mock
    private AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    @Mock
    private AgentProfileVersionSkillBindingRepository skillBindingRepository;
    @Mock
    private AgentProfileVersionEnvVarRepository envVarRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private McpServerConfigRepository mcpServerConfigRepository;
    @Mock
    private SkillBindingRepository skillCatalogRepository;
    @Mock
    private AgentProfileVersionPolicyService policyService;
    @Mock
    private AgentRuntimeEnvironmentResolver environmentResolver;
    @Mock
    private IntegrationSecretCipherService secretCipherService;

    private AgentProfileBindingService service;

    @BeforeEach
    void setUp() {
        service = new AgentProfileBindingService(
                agentProfileVersionRepository,
                agentBindingRepository,
                toolBindingRepository,
                mcpBindingRepository,
                skillBindingRepository,
                envVarRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillCatalogRepository,
                new ObjectMapper(),
                policyService,
                environmentResolver,
                secretCipherService
        );
    }

    @Test
    void shouldNormalizeAndReplaceBindingsWithoutDuplicates() {
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
        when(policyService.normalizeAndValidateChildBindings(eq(version), anyList())).thenReturn(List.of());

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
        when(agentBindingRepository.findByParentProfileVersionId(1L)).thenReturn(List.of());

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
                ),
                List.of(),
                List.of()
        );

        var response = service.updateBindings(1L, request);

        assertThat(response.profileVersionId()).isEqualTo(1L);
        assertThat(response.toolCodes()).isEmpty();
        assertThat(response.skillCodes()).isEmpty();
        assertThat(response.mcpBindings()).isEmpty();
        verify(toolBindingRepository).deleteByProfileVersionId(1L);
        verify(skillBindingRepository).deleteByProfileVersionId(1L);
        verify(mcpBindingRepository).deleteByProfileVersionId(1L);
        verify(agentBindingRepository).deleteByParentProfileVersionId(1L);
        verify(envVarRepository).deleteByProfileVersionId(1L);
    }

    @Test
    void shouldExposePlainInlineEnvValueAndMaskSecretInlineValue() {
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
        when(toolBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(skillBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(mcpBindingRepository.findByProfileVersionId(1L)).thenReturn(List.of());
        when(agentBindingRepository.findByParentProfileVersionId(1L)).thenReturn(List.of());

        AgentProfileVersionEnvVar nonSecret = new AgentProfileVersionEnvVar();
        nonSecret.setProfileVersionId(1L);
        nonSecret.setEnvKey("KB_SEARCH_REGION");
        nonSecret.setDescription("Search region");
        nonSecret.setSecret(Boolean.FALSE);
        nonSecret.setValueSource(AgentRuntimeEnvValueSource.INLINE);
        nonSecret.setValueEncrypted("enc-region");

        AgentProfileVersionEnvVar secret = new AgentProfileVersionEnvVar();
        secret.setProfileVersionId(1L);
        secret.setEnvKey("TAVILY_API_KEY");
        secret.setDescription("Tavily");
        secret.setSecret(Boolean.TRUE);
        secret.setValueSource(AgentRuntimeEnvValueSource.INLINE);
        secret.setValueEncrypted("enc-secret");

        when(envVarRepository.findByProfileVersionIdOrderByEnvKeyAsc(1L)).thenReturn(List.of(nonSecret, secret));
        when(environmentResolver.resolveValue(nonSecret)).thenReturn("cn");
        when(environmentResolver.resolveValue(secret)).thenReturn("real-secret");

        var bindings = service.bindings(1L);

        assertThat(bindings.envVars()).hasSize(2);
        assertThat(bindings.envVars().get(0).key()).isEqualTo("KB_SEARCH_REGION");
        assertThat(bindings.envVars().get(0).value()).isEqualTo("cn");
        assertThat(bindings.envVars().get(0).hasValue()).isTrue();
        assertThat(bindings.envVars().get(1).key()).isEqualTo("TAVILY_API_KEY");
        assertThat(bindings.envVars().get(1).value()).isEqualTo("********");
        assertThat(bindings.envVars().get(1).hasValue()).isTrue();
    }
}
