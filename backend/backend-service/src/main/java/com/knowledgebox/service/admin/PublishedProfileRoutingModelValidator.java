package com.knowledgebox.service.admin;

import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PublishedProfileRoutingModelValidator implements ApplicationRunner {

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;

    public PublishedProfileRoutingModelValidator(
            AgentProfileVersionRepository agentProfileVersionRepository,
            ModelCatalogRepository modelCatalogRepository
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<AgentProfileVersion> publishedVersions = agentProfileVersionRepository.findAllForAdmin().stream()
                .filter(version -> Boolean.TRUE.equals(version.getPublished()))
                .toList();
        for (AgentProfileVersion version : publishedVersions) {
            validateRoutingModel(version);
        }
    }

    private void validateRoutingModel(AgentProfileVersion version) {
        String routingModel = version.getRoutingModel() == null ? "" : version.getRoutingModel().trim();
        if (routingModel.isBlank()) {
            throw new IllegalStateException(
                    "Published agent profile version has empty routingModel: profile="
                            + version.getProfile().getCode()
                            + ", version="
                            + version.getVersionNumber()
            );
        }
        boolean exists = modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue(routingModel, ModelType.CHAT).isPresent();
        if (!exists) {
            throw new IllegalStateException(
                    "Published agent profile version routingModel is invalid: profile="
                            + version.getProfile().getCode()
                            + ", version="
                            + version.getVersionNumber()
                            + ", routingModel="
                            + routingModel
                            + ". The routingModel must exist in enabled CHAT model catalog."
            );
        }
    }
}
