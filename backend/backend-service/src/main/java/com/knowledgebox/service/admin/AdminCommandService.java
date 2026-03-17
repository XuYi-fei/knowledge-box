package com.knowledgebox.service.admin;

import com.knowledgebox.api.CreateModelCatalogRequest;
import com.knowledgebox.api.AgentProfileVersionView;
import com.knowledgebox.api.ModelCatalogView;
import com.knowledgebox.api.UpdateAgentProfileVersionRequest;
import com.knowledgebox.api.UpdateModelCatalogRequest;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCommandService {

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final AdminSecurityService adminSecurityService;
    private final AgentProfileVersionPolicyService policyService;

    public AdminCommandService(
            AgentProfileVersionRepository agentProfileVersionRepository,
            ModelCatalogRepository modelCatalogRepository,
            AdminSecurityService adminSecurityService,
            AgentProfileVersionPolicyService policyService
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.adminSecurityService = adminSecurityService;
        this.policyService = policyService;
    }

    @Transactional
    public void changeAdminPassword(String username, String currentPassword, String newPassword) {
        adminSecurityService.changePassword(username, currentPassword, newPassword);
    }

    @Transactional
    public AgentProfileVersionView updateProfileVersion(Long id, UpdateAgentProfileVersionRequest request) {
        AgentProfileVersion version = agentProfileVersionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent profile version not found: " + id));

        AgentProfileVersionType targetType = policyService.normalizeType(request.agentType());
        policyService.validateTypeTransition(version, targetType);
        version.setAgentType(targetType);
        version.setChatModel(requireEnabledModel(request.chatModel(), ModelType.CHAT).getCode());
        version.setRoutingModel(requireEnabledModel(request.routingModel(), ModelType.CHAT).getCode());
        version.setEmbeddingModel(requireEnabledModel(request.embeddingModel(), ModelType.EMBEDDING).getCode());
        version.setRerankModel(normalizeOptionalRerank(request.rerankModel()));
        version.setTemperature(request.temperature());
        version.setRetrievalTopK(request.retrievalTopK());
        version.setReasoningBudget(request.reasoningBudget());
        return toProfileVersionView(agentProfileVersionRepository.save(version));
    }

    @Transactional
    public ModelCatalogView createModelCatalog(CreateModelCatalogRequest request) {
        if (modelCatalogRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Model code already exists: " + request.code());
        }
        ModelCatalog modelCatalog = new ModelCatalog();
        modelCatalog.setCode(request.code().trim());
        modelCatalog.setDisplayName(request.displayName().trim());
        modelCatalog.setProvider(request.provider().trim());
        modelCatalog.setModelType(request.modelType());
        modelCatalog.setDescription(blankToNull(request.description()));
        modelCatalog.setEnabled(request.enabled());
        applyPublicChatSettings(modelCatalog, request.publicSelectable(), request.defaultForPublic());
        return toView(modelCatalogRepository.save(modelCatalog));
    }

    @Transactional
    public ModelCatalogView updateModelCatalog(Long id, UpdateModelCatalogRequest request) {
        ModelCatalog modelCatalog = modelCatalogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Model catalog not found: " + id));
        modelCatalog.setDisplayName(request.displayName().trim());
        modelCatalog.setProvider(request.provider().trim());
        modelCatalog.setDescription(blankToNull(request.description()));
        modelCatalog.setEnabled(request.enabled());
        applyPublicChatSettings(modelCatalog, request.publicSelectable(), request.defaultForPublic());
        return toView(modelCatalogRepository.save(modelCatalog));
    }

    private ModelCatalog requireEnabledModel(String code, ModelType modelType) {
        return modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue(code.trim(), modelType)
                .orElseThrow(() -> new IllegalArgumentException("Enabled " + modelType + " model not found: " + code));
    }

    private String normalizeOptionalRerank(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return requireEnabledModel(code, ModelType.RERANK).getCode();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void applyPublicChatSettings(ModelCatalog modelCatalog, boolean publicSelectable, boolean defaultForPublic) {
        if (modelCatalog.getModelType() != ModelType.CHAT) {
            if (publicSelectable || defaultForPublic) {
                throw new IllegalArgumentException("Only chat models can be exposed on the public entry");
            }
            modelCatalog.setPublicSelectable(Boolean.FALSE);
            modelCatalog.setDefaultForPublic(Boolean.FALSE);
            return;
        }

        if (defaultForPublic && !publicSelectable) {
            throw new IllegalArgumentException("Default public chat model must also be public selectable");
        }
        if (defaultForPublic && !Boolean.TRUE.equals(modelCatalog.getEnabled())) {
            throw new IllegalArgumentException("Default public chat model must be enabled");
        }

        modelCatalog.setPublicSelectable(publicSelectable);
        modelCatalog.setDefaultForPublic(defaultForPublic);

        if (defaultForPublic) {
            clearExistingPublicDefault(modelCatalog);
        }
    }

    private void clearExistingPublicDefault(ModelCatalog current) {
        for (ModelCatalog modelCatalog : modelCatalogRepository.findAllByModelTypeAndDefaultForPublicTrue(ModelType.CHAT)) {
            if (!modelCatalog.getId().equals(current.getId())) {
                modelCatalog.setDefaultForPublic(Boolean.FALSE);
            }
        }
    }

    private ModelCatalogView toView(ModelCatalog modelCatalog) {
        return new ModelCatalogView(
                modelCatalog.getId(),
                modelCatalog.getCode(),
                modelCatalog.getDisplayName(),
                modelCatalog.getProvider(),
                modelCatalog.getModelType(),
                modelCatalog.getDescription(),
                Boolean.TRUE.equals(modelCatalog.getEnabled()),
                Boolean.TRUE.equals(modelCatalog.getPublicSelectable()),
                Boolean.TRUE.equals(modelCatalog.getDefaultForPublic())
        );
    }

    private AgentProfileVersionView toProfileVersionView(AgentProfileVersion version) {
        return new AgentProfileVersionView(
                version.getId(),
                version.getProfile().getCode(),
                version.getProfile().getName(),
                version.getVersionNumber(),
                version.getStatus(),
                Boolean.TRUE.equals(version.getPublished()),
                policyService.normalizeType(version.getAgentType()),
                version.getChatModel(),
                version.getRoutingModel(),
                version.getEmbeddingModel(),
                version.getRerankModel(),
                version.getTemperature(),
                version.getRetrievalTopK(),
                version.getReasoningBudget()
        );
    }
}
