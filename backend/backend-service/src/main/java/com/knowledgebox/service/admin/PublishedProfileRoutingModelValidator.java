package com.knowledgebox.service.admin;

import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
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
        if (publishedVersions.isEmpty()) {
            throw new IllegalStateException("A published MAIN agent profile version is required for public chat");
        }
        for (AgentProfileVersion version : publishedVersions) {
            validatePublishedEntryType(version);
            validateRoutingModel(version);
        }
    }

    private void validatePublishedEntryType(AgentProfileVersion version) {
        AgentProfileVersionType agentType = version.getAgentType() == null ? AgentProfileVersionType.ENTRY : version.getAgentType();
        if (agentType != AgentProfileVersionType.MAIN) {
            throw new IllegalStateException(
                    "Published agent profile version must be MAIN: profile="
                            + version.getProfile().getCode()
                            + ", version="
                            + version.getVersionNumber()
                            + ", agentType="
                            + agentType
            );
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
