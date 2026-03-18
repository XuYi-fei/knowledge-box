package com.knowledgebox.service.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentProfileVersionPolicyServiceTests {

    @Mock
    private AgentProfileVersionRepository agentProfileVersionRepository;
    @Mock
    private AgentProfileVersionAgentBindingRepository agentBindingRepository;

    private AgentProfileVersionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new AgentProfileVersionPolicyService(agentProfileVersionRepository, agentBindingRepository);
    }

    @Test
    void shouldRejectChangingPublishedMainToAtomic() {
        AgentProfileVersion version = version(1L, "default-qa", "Default QA", 1, AgentProfileVersionType.MAIN, true);

        assertThatThrownBy(() -> service.validateTypeTransition(version, AgentProfileVersionType.ATOMIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only MAIN agent versions can stay published");
    }

    @Test
    void shouldRejectChildBindingWhenChildIsNotAtomic() {
        AgentProfileVersion parent = version(1L, "entry-agent", "Entry Agent", 1, AgentProfileVersionType.ENTRY, true);
        AgentProfileVersion child = version(2L, "orchestrator-agent", "Orchestrator Agent", 3, AgentProfileVersionType.ORCHESTRATOR, false);

        when(agentProfileVersionRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(child));

        assertThatThrownBy(() -> service.normalizeAndValidateChildBindings(parent, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Child agent version must be ATOMIC");
    }

    @Test
    void shouldRejectSwitchingReferencedAtomicToEntry() {
        AgentProfileVersion version = version(5L, "atomic-agent", "Atomic Agent", 2, AgentProfileVersionType.ATOMIC, false);
        when(agentBindingRepository.existsByChildProfileVersionId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.validateTypeTransition(version, AgentProfileVersionType.ENTRY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must stay ATOMIC");
    }

    @Test
    void shouldResolveRequiredVersion() {
        AgentProfileVersion version = version(9L, "atomic-agent", "Atomic Agent", 1, AgentProfileVersionType.ATOMIC, false);
        when(agentProfileVersionRepository.findById(9L)).thenReturn(Optional.of(version));
        service.requireVersion(9L);
    }

    @Test
    void shouldRejectSecondMainAgentVersion() {
        AgentProfileVersion version = version(12L, "router-agent", "Router Agent", 1, AgentProfileVersionType.ENTRY, false);
        when(agentProfileVersionRepository.existsByAgentTypeAndIdNot(AgentProfileVersionType.MAIN, 12L)).thenReturn(true);

        assertThatThrownBy(() -> service.validateTypeTransition(version, AgentProfileVersionType.MAIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one MAIN agent version is allowed");
    }

    private AgentProfileVersion version(
            Long id,
            String profileCode,
            String profileName,
            int versionNumber,
            AgentProfileVersionType agentType,
            boolean published
    ) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);
        profile.setName(profileName);
        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(versionNumber);
        version.setAgentType(agentType);
        version.setPublished(published);
        try {
            Field idField = com.knowledgebox.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(version, id);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return version;
    }
}
