package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
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
        AgentProfileVersion publishedVersion = version(true, "default-qa", 1, "qwen-plus");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-plus", ModelType.CHAT))
                .thenReturn(Optional.of(new ModelCatalog()));

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenPublishedRoutingModelIsBlank() {
        AgentProfileVersion publishedVersion = version(true, "default-qa", 1, " ");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty routingModel");
        verifyNoInteractions(modelCatalogRepository);
    }

    @Test
    void shouldFailWhenPublishedRoutingModelIsNotEnabledChatModel() {
        AgentProfileVersion publishedVersion = version(true, "default-qa", 1, "qwen-unknown");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(publishedVersion));
        when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-unknown", ModelType.CHAT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("routingModel is invalid")
                .hasMessageContaining("qwen-unknown");
    }

    @Test
    void shouldIgnoreUnpublishedVersions() {
        AgentProfileVersion draftVersion = version(false, "default-qa", 2, "");
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(draftVersion));

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(modelCatalogRepository);
    }

    private AgentProfileVersion version(boolean published, String profileCode, int versionNumber, String routingModel) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);

        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(versionNumber);
        version.setPublished(published);
        version.setRoutingModel(routingModel);
        return version;
    }
}
