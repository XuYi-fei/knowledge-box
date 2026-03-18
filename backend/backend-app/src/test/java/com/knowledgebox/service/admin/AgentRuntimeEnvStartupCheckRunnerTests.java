package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionEnvVar;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource;
import com.knowledgebox.domain.agent.ProfileStatus;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionEnvVarRepository;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.chat.AgentRuntimeEnvironmentResolver;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeEnvStartupCheckRunnerTests {

    @Mock
    private AgentProfileVersionRepository agentProfileVersionRepository;
    @Mock
    private AgentProfileVersionEnvVarRepository envVarRepository;
    @Mock
    private AgentProfileVersionToolBindingRepository toolBindingRepository;
    @Mock
    private AgentProfileVersionSkillBindingRepository skillBindingRepository;
    @Mock
    private AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private SkillBindingRepository skillBindingRepositoryRef;
    @Mock
    private McpServerConfigRepository mcpServerConfigRepository;
    @Mock
    private AgentRuntimeEnvironmentResolver environmentResolver;
    @Mock
    private Environment environment;

    private KnowledgeBoxProperties properties;
    private AgentRuntimeEnvStartupCheckRunner runner;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeBoxProperties();
        runner = new AgentRuntimeEnvStartupCheckRunner(
                properties,
                agentProfileVersionRepository,
                envVarRepository,
                toolBindingRepository,
                skillBindingRepository,
                mcpBindingRepository,
                toolDefinitionRepository,
                skillBindingRepositoryRef,
                mcpServerConfigRepository,
                environmentResolver,
                new ObjectMapper(),
                environment
        );
    }

    @Test
    void shouldReportMissingProcessEnvSourceAndRequiredRequirement() {
        AgentProfileVersion version = version(11L, 1L, "web-search-agent", AgentProfileVersionType.ATOMIC, false);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(version));

        AgentProfileVersionEnvVar envVar = new AgentProfileVersionEnvVar();
        envVar.setProfileVersionId(11L);
        envVar.setEnvKey("TAVILY_API_KEY");
        envVar.setValueSource(AgentRuntimeEnvValueSource.PROCESS_ENV);
        envVar.setSourceRef("");
        when(envVarRepository.findByProfileVersionIdOrderByEnvKeyAsc(11L)).thenReturn(List.of(envVar));
        when(environmentResolver.resolveValue(envVar)).thenReturn(null);

        AgentProfileVersionToolBinding binding = new AgentProfileVersionToolBinding();
        binding.setProfileVersionId(11L);
        binding.setToolId(101L);
        when(toolBindingRepository.findByProfileVersionId(11L)).thenReturn(List.of(binding));
        when(skillBindingRepository.findByProfileVersionId(11L)).thenReturn(List.of());
        when(mcpBindingRepository.findByProfileVersionId(11L)).thenReturn(List.of());

        ToolDefinition tool = new ToolDefinition();
        tool.setCode("web-search");
        tool.setEnabled(Boolean.TRUE);
        tool.setRuntimeEnvRequirementsJson("""
                [{"key":"TAVILY_API_KEY","required":true,"secret":true,"description":"required for tavily"}]
                """);
        when(toolDefinitionRepository.findById(101L)).thenReturn(Optional.of(tool));

        KnowledgeBoxProperties.RuntimeEnvCheck check = properties.getAgent().getRuntimeEnvCheck();
        check.setEnabled(true);

        AgentRuntimeEnvStartupCheckRunner.StartupCheckReport report = runner.inspect(check);

        assertThat(report.checkedAgents()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(message -> message.contains("PROCESS_ENV") && message.contains("sourceRef"));
        assertThat(report.issues()).anyMatch(message -> message.contains("缺少必填运行时环境变量 TAVILY_API_KEY") && message.contains("Tool=web-search"));
    }

    @Test
    void shouldSkipUnpublishedAgentWhenConfigured() {
        AgentProfileVersion version = version(12L, 2L, "draft-agent", AgentProfileVersionType.ATOMIC, false);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(version));

        KnowledgeBoxProperties.RuntimeEnvCheck check = properties.getAgent().getRuntimeEnvCheck();
        check.setEnabled(true);
        check.setCheckUnpublished(false);

        AgentRuntimeEnvStartupCheckRunner.StartupCheckReport report = runner.inspect(check);

        assertThat(report.checkedAgents()).isEqualTo(0);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void shouldFailFastWhenRuntimeEnvIssuesExist() {
        AgentProfileVersion version = version(13L, 3L, "missing-env-agent", AgentProfileVersionType.ATOMIC, true);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(version));
        when(envVarRepository.findByProfileVersionIdOrderByEnvKeyAsc(13L)).thenReturn(List.of());
        when(toolBindingRepository.findByProfileVersionId(13L)).thenReturn(List.of());
        when(skillBindingRepository.findByProfileVersionId(13L)).thenReturn(List.of());
        when(mcpBindingRepository.findByProfileVersionId(13L)).thenReturn(List.of());

        KnowledgeBoxProperties.RuntimeEnvCheck check = properties.getAgent().getRuntimeEnvCheck();
        check.setEnabled(true);
        check.setFailFast(true);
        check.setRequiredProcessEnvKeys(List.of("KB_TEST_REQUIRED_STARTUP_ENV_MISSING"));

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent runtime env startup check failed");
    }

    private AgentProfileVersion version(
            Long versionId,
            Long profileId,
            String profileCode,
            AgentProfileVersionType agentType,
            boolean published
    ) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);
        profile.setName(profileCode);
        setId(profile, profileId);

        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(1);
        version.setAgentType(agentType);
        version.setPublished(published);
        version.setStatus(published ? ProfileStatus.PUBLISHED : ProfileStatus.DRAFT);
        setId(version, versionId);
        return version;
    }

    private void setId(Object entity, Long id) {
        try {
            Field idField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
