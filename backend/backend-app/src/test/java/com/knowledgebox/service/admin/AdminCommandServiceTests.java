package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.api.CreateAgentProfileRequest;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.repository.AgentProfileRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import com.knowledgebox.repository.ChatSessionRepository;
import com.knowledgebox.repository.ChatTurnRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminCommandServiceTests {

    private AgentProfileRepository agentProfileRepository;
    private AgentProfileVersionRepository agentProfileVersionRepository;
    private ModelCatalogRepository modelCatalogRepository;
    private AdminSecurityService adminSecurityService;
    private AgentProfileVersionPolicyService policyService;
    private ChatSessionRepository chatSessionRepository;
    private ChatTurnRepository chatTurnRepository;
    private AgentExecutionTraceRepository agentExecutionTraceRepository;
    private AdminCommandService service;

    @BeforeEach
    void setUp() {
        agentProfileRepository = mock(AgentProfileRepository.class);
        agentProfileVersionRepository = mock(AgentProfileVersionRepository.class);
        modelCatalogRepository = mock(ModelCatalogRepository.class);
        adminSecurityService = mock(AdminSecurityService.class);
        policyService = mock(AgentProfileVersionPolicyService.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        chatTurnRepository = mock(ChatTurnRepository.class);
        agentExecutionTraceRepository = mock(AgentExecutionTraceRepository.class);
        service = new AdminCommandService(
                agentProfileRepository,
                agentProfileVersionRepository,
                modelCatalogRepository,
                adminSecurityService,
                policyService,
                chatSessionRepository,
                chatTurnRepository,
                agentExecutionTraceRepository
        );
    }

    @Test
    void shouldCreateAgentProfileWithFirstVersion() {
        when(agentProfileRepository.existsByCode("router-agent")).thenReturn(false);
        when(policyService.normalizeType(AgentProfileVersionType.ORCHESTRATOR)).thenReturn(AgentProfileVersionType.ORCHESTRATOR);
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-plus", ModelType.CHAT))
                .thenReturn(Optional.of(model("qwen-plus")));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("text-embedding-v3", ModelType.EMBEDDING))
                .thenReturn(Optional.of(model("text-embedding-v3")));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("gte-rerank", ModelType.RERANK))
                .thenReturn(Optional.of(model("gte-rerank")));
        when(agentProfileRepository.save(any(AgentProfile.class))).thenAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            setId(profile, 7L);
            return profile;
        });
        when(agentProfileVersionRepository.save(any(AgentProfileVersion.class))).thenAnswer(invocation -> {
            AgentProfileVersion version = invocation.getArgument(0);
            setId(version, 11L);
            return version;
        });

        var created = service.createProfile(new CreateAgentProfileRequest(
                "router-agent",
                "Router Agent",
                "route user queries",
                AgentProfileVersionType.ORCHESTRATOR,
                "qwen-plus",
                "qwen-plus",
                "text-embedding-v3",
                "gte-rerank",
                0.3,
                8,
                2,
                false
        ));

        assertThat(created.profileCode()).isEqualTo("router-agent");
        assertThat(created.profileName()).isEqualTo("Router Agent");
        assertThat(created.agentType()).isEqualTo(AgentProfileVersionType.ORCHESTRATOR);
        assertThat(created.versionNumber()).isEqualTo(1);
        assertThat(created.published()).isFalse();
    }

    @Test
    void shouldRejectDeletingMainProfile() {
        AgentProfile profile = profile(1L, "default-qa", "Default QA");
        AgentProfileVersion mainVersion = version(2L, profile, 1, AgentProfileVersionType.MAIN);
        when(agentProfileVersionRepository.findById(2L)).thenReturn(Optional.of(mainVersion));
        when(agentProfileVersionRepository.findByProfile_IdOrderByVersionNumberDesc(1L)).thenReturn(List.of(mainVersion));
        when(policyService.normalizeType(AgentProfileVersionType.MAIN)).thenReturn(AgentProfileVersionType.MAIN);

        assertThatThrownBy(() -> service.deleteProfileVersion(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAIN 主入口 Agent 不允许删除");

        verify(agentProfileRepository, never()).deleteById(any());
    }

    @Test
    void shouldDeleteNormalProfileAndAllVersions() {
        AgentProfile profile = profile(3L, "router-agent", "Router Agent");
        AgentProfileVersion orchestratorVersion = version(4L, profile, 1, AgentProfileVersionType.ORCHESTRATOR);
        when(agentProfileVersionRepository.findById(4L)).thenReturn(Optional.of(orchestratorVersion));
        when(agentProfileVersionRepository.findByProfile_IdOrderByVersionNumberDesc(3L)).thenReturn(List.of(orchestratorVersion));
        when(policyService.normalizeType(AgentProfileVersionType.ORCHESTRATOR)).thenReturn(AgentProfileVersionType.ORCHESTRATOR);
        when(agentExecutionTraceRepository.existsByProfileCodeAndStatus("router-agent", com.knowledgebox.domain.chat.AgentExecutionStatus.RUNNING)).thenReturn(false);
        when(chatSessionRepository.findAllByActiveProfileCode("router-agent")).thenReturn(List.of());

        service.deleteProfileVersion(4L);

        verify(agentProfileVersionRepository).deleteAllInBatch(List.of(orchestratorVersion));
        verify(agentProfileRepository).deleteById(3L);
        verify(agentExecutionTraceRepository).deleteByProfileCode("router-agent");
    }

    private ModelCatalog model(String code) {
        ModelCatalog modelCatalog = new ModelCatalog();
        modelCatalog.setCode(code);
        return modelCatalog;
    }

    private AgentProfile profile(Long id, String code, String name) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(code);
        profile.setName(name);
        setId(profile, id);
        return profile;
    }

    private AgentProfileVersion version(Long id, AgentProfile profile, int versionNumber, AgentProfileVersionType agentType) {
        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(versionNumber);
        version.setAgentType(agentType);
        setId(version, id);
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
