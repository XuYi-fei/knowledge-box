package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishedProfileRoutingModelValidatorTests {

    private AgentProfileVersionRepository agentProfileVersionRepository;
    private ModelCatalogRepository modelCatalogRepository;
    private PublishedProfileRoutingModelValidator validator;

    @BeforeEach
    void setUp() {
        agentProfileVersionRepository = mock(AgentProfileVersionRepository.class);
        modelCatalogRepository = mock(ModelCatalogRepository.class);
        validator = new PublishedProfileRoutingModelValidator(agentProfileVersionRepository, modelCatalogRepository);
    }

    @Test
    void shouldPassWhenPublishedRoutingModelIsEnabledChatModel() {
        AgentProfileVersion publishedVersion = version(true, AgentProfileVersionType.MAIN, "default-qa", 1, "qwen-plus");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-plus", ModelType.CHAT))
                .thenReturn(Optional.of(new ModelCatalog()));

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenPublishedRoutingModelIsBlank() {
        AgentProfileVersion publishedVersion = version(true, AgentProfileVersionType.MAIN, "default-qa", 1, " ");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty routingModel");
        verifyNoInteractions(modelCatalogRepository);
    }

    @Test
    void shouldFailWhenPublishedRoutingModelIsNotEnabledChatModel() {
        AgentProfileVersion publishedVersion = version(true, AgentProfileVersionType.MAIN, "default-qa", 1, "qwen-unknown");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-unknown", ModelType.CHAT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("routingModel is invalid")
                .hasMessageContaining("qwen-unknown");
    }

    @Test
    void shouldFailWhenNoPublishedMainVersionExists() {
        AgentProfileVersion draftVersion = version(false, AgentProfileVersionType.ENTRY, "default-qa", 2, "");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(draftVersion));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published MAIN agent profile version is required");
        verifyNoInteractions(modelCatalogRepository);
    }

    @Test
    void shouldFailWhenPublishedVersionIsNotMain() {
        AgentProfileVersion publishedVersion = version(true, AgentProfileVersionType.ENTRY, "default-qa", 1, "qwen-plus");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be MAIN");
    }

    private AgentProfileVersion version(
            boolean published,
            AgentProfileVersionType agentType,
            String profileCode,
            int versionNumber,
            String routingModel
    ) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);

        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(versionNumber);
        version.setPublished(published);
        version.setAgentType(agentType);
        version.setRoutingModel(routingModel);
        return version;
    }
}
