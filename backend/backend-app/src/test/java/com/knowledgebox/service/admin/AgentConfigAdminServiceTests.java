package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentImportCommitRequest;
import com.knowledgebox.api.AgentImportDecisionRequest;
import com.knowledgebox.api.AgentImportItemStatus;
import com.knowledgebox.api.AgentImportResolutionAction;
import com.knowledgebox.api.AgentProfileVersionBindingsView;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.domain.agent.ProfileStatus;
import com.knowledgebox.repository.AgentProfileRepository;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.AgentProfileBindingService;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AgentConfigAdminServiceTests {

    @Mock
    private AgentProfileRepository agentProfileRepository;
    @Mock
    private AgentProfileVersionRepository agentProfileVersionRepository;
    @Mock
    private AgentProfileVersionAgentBindingRepository agentBindingRepository;
    @Mock
    private ModelCatalogRepository modelCatalogRepository;
    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;
    @Mock
    private McpServerConfigRepository mcpServerConfigRepository;
    @Mock
    private SkillBindingRepository skillBindingRepository;
    @Mock
    private AgentProfileBindingService agentProfileBindingService;
    @Mock
    private AgentProfileVersionPolicyService policyService;

    private AgentConfigAdminService service;

    @BeforeEach
    void setUp() {
        service = new AgentConfigAdminService(
                agentProfileRepository,
                agentProfileVersionRepository,
                agentBindingRepository,
                modelCatalogRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillBindingRepository,
                agentProfileBindingService,
                policyService,
                new ObjectMapper()
        );
        when(policyService.normalizeType(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldPreviewCodeConflictNameConflictAndCreatableAgent() {
        AgentProfileVersion mainVersion = version(10L, 1L, "default-main", "Default Main Agent", AgentProfileVersionType.MAIN, true, 1);
        AgentProfileVersion helperVersion = version(11L, 2L, "existing-atomic", "Existing Atomic", AgentProfileVersionType.ATOMIC, false, 1);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(mainVersion, helperVersion));
        when(agentProfileBindingService.bindings(10L)).thenReturn(emptyBindings(10L));
        when(agentProfileBindingService.bindings(11L)).thenReturn(emptyBindings(11L));
        mockEnabledModels();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents.json",
                "application/json",
                previewPayload().getBytes(StandardCharsets.UTF_8)
        );

        var preview = service.previewImport(file);

        assertThat(preview.previewToken()).isNotBlank();
        assertThat(preview.totalCount()).isEqualTo(3);
        assertThat(preview.codeConflictCount()).isEqualTo(1);
        assertThat(preview.nameConflictCount()).isEqualTo(1);
        assertThat(preview.creatableCount()).isEqualTo(1);
        assertThat(preview.validationErrorCount()).isEqualTo(0);
        assertThat(preview.items()).extracting(item -> item.status())
                .containsExactly(AgentImportItemStatus.CODE_CONFLICT, AgentImportItemStatus.NAME_CONFLICT, AgentImportItemStatus.READY_CREATE);
    }

    @Test
    void shouldCommitCreateAndOverwriteAgents() {
        AgentProfileVersion mainVersion = version(10L, 1L, "default-main", "Default Main Agent", AgentProfileVersionType.MAIN, true, 1);
        AgentProfileVersion helperVersion = version(11L, 2L, "helper-agent", "Helper Agent", AgentProfileVersionType.ATOMIC, false, 1);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(mainVersion, helperVersion));
        when(agentProfileBindingService.bindings(10L)).thenReturn(emptyBindings(10L));
        when(agentProfileBindingService.bindings(11L)).thenReturn(emptyBindings(11L));
        mockEnabledModels();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents.json",
                "application/json",
                commitPayload().getBytes(StandardCharsets.UTF_8)
        );
        var preview = service.previewImport(file);

        AgentProfile createdProfile = new AgentProfile();
        when(agentProfileRepository.save(any(AgentProfile.class))).thenAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            if ("new-atomic".equals(profile.getCode())) {
                setId(profile, 3L);
            } else if (profile.getId() == null) {
                setId(profile, 2L);
            }
            return profile;
        });
        when(agentProfileRepository.findById(2L)).thenReturn(Optional.of(helperVersion.getProfile()));
        when(agentProfileVersionRepository.findById(11L)).thenReturn(Optional.of(helperVersion));
        when(agentProfileVersionRepository.save(any(AgentProfileVersion.class))).thenAnswer(invocation -> {
            AgentProfileVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                setId(version, 21L);
            }
            return version;
        });

        var result = service.commitImport(new AgentImportCommitRequest(
                preview.previewToken(),
                List.of(new AgentImportDecisionRequest("helper-agent", AgentImportResolutionAction.OVERWRITE_EXISTING))
        ));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.overwrittenCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
        verify(agentProfileBindingService).updateBindings(eq(21L), any());
        verify(agentProfileBindingService).updateBindings(eq(11L), any());
    }

    @Test
    void shouldSkipExistingAgentDuringBootstrapImport() {
        AgentProfileVersion mainVersion = version(10L, 1L, "default-main", "Default Main Agent", AgentProfileVersionType.MAIN, true, 1);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(mainVersion));
        when(agentProfileBindingService.bindings(10L)).thenReturn(emptyBindings(10L));
        mockEnabledModels();

        AgentConfigAdminService.BootstrapImportResult result = service.importForBootstrap(
                new java.io.ByteArrayInputStream(singleExistingPayload().getBytes(StandardCharsets.UTF_8)),
                "bootstrap.json",
                false
        );

        assertThat(result.createdCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.messages()).anyMatch(message -> message.contains("default-main") && message.contains("跳过"));
    }

    private void mockEnabledModels() {
        lenient().when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-max", ModelType.CHAT))
                .thenReturn(Optional.of(model("qwen-max", ModelType.CHAT)));
        lenient().when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("qwen-plus", ModelType.CHAT))
                .thenReturn(Optional.of(model("qwen-plus", ModelType.CHAT)));
        lenient().when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("text-embedding-v3", ModelType.EMBEDDING))
                .thenReturn(Optional.of(model("text-embedding-v3", ModelType.EMBEDDING)));
        lenient().when(modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue("gte-rerank", ModelType.RERANK))
                .thenReturn(Optional.of(model("gte-rerank", ModelType.RERANK)));
    }

    private AgentProfileVersionBindingsView emptyBindings(Long versionId) {
        return new AgentProfileVersionBindingsView(versionId, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private String previewPayload() {
        return """
                {
                  "schemaVersion": "knowledge-box.agent-config.v1",
                  "agents": [
                    {
                      "profileCode": "default-main",
                      "profileName": "Default Main Agent",
                      "description": "main",
                      "agentType": "MAIN",
                      "status": "PUBLISHED",
                      "published": true,
                      "chatModel": "qwen-max",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.2,
                      "retrievalTopK": 6,
                      "reasoningBudget": 1,
                      "systemPrompt": "main prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    },
                    {
                      "profileCode": "name-clash",
                      "profileName": "Existing Atomic",
                      "description": "same name",
                      "agentType": "ATOMIC",
                      "status": "DRAFT",
                      "published": false,
                      "chatModel": "qwen-plus",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.2,
                      "retrievalTopK": 6,
                      "reasoningBudget": 1,
                      "systemPrompt": "atomic prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    },
                    {
                      "profileCode": "fresh-atomic",
                      "profileName": "Fresh Atomic",
                      "description": "new",
                      "agentType": "ATOMIC",
                      "status": "DRAFT",
                      "published": false,
                      "chatModel": "qwen-plus",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.2,
                      "retrievalTopK": 6,
                      "reasoningBudget": 1,
                      "systemPrompt": "fresh prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    }
                  ]
                }
                """;
    }

    private String commitPayload() {
        return """
                {
                  "schemaVersion": "knowledge-box.agent-config.v1",
                  "agents": [
                    {
                      "profileCode": "helper-agent",
                      "profileName": "Helper Agent Updated",
                      "description": "updated",
                      "agentType": "ATOMIC",
                      "status": "DRAFT",
                      "published": false,
                      "chatModel": "qwen-plus",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.3,
                      "retrievalTopK": 8,
                      "reasoningBudget": 2,
                      "systemPrompt": "updated prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    },
                    {
                      "profileCode": "new-atomic",
                      "profileName": "New Atomic",
                      "description": "new",
                      "agentType": "ATOMIC",
                      "status": "DRAFT",
                      "published": false,
                      "chatModel": "qwen-plus",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.2,
                      "retrievalTopK": 6,
                      "reasoningBudget": 1,
                      "systemPrompt": "new prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    }
                  ]
                }
                """;
    }

    private String singleExistingPayload() {
        return """
                {
                  "schemaVersion": "knowledge-box.agent-config.v1",
                  "agents": [
                    {
                      "profileCode": "default-main",
                      "profileName": "Default Main Agent",
                      "description": "main",
                      "agentType": "MAIN",
                      "status": "PUBLISHED",
                      "published": true,
                      "chatModel": "qwen-max",
                      "routingModel": "qwen-plus",
                      "embeddingModel": "text-embedding-v3",
                      "rerankModel": "gte-rerank",
                      "temperature": 0.2,
                      "retrievalTopK": 6,
                      "reasoningBudget": 1,
                      "systemPrompt": "main prompt",
                      "toolCodes": [],
                      "skillCodes": [],
                      "mcpBindings": [],
                      "childAgentProfileCodes": []
                    }
                  ]
                }
                """;
    }

    private ModelCatalog model(String code, ModelType modelType) {
        ModelCatalog catalog = new ModelCatalog();
        catalog.setCode(code);
        catalog.setModelType(modelType);
        catalog.setEnabled(Boolean.TRUE);
        return catalog;
    }

    private AgentProfileVersion version(
            Long versionId,
            Long profileId,
            String profileCode,
            String profileName,
            AgentProfileVersionType agentType,
            boolean published,
            int versionNumber
    ) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(profileCode);
        profile.setName(profileName);
        setId(profile, profileId);
        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(versionNumber);
        version.setAgentType(agentType);
        version.setStatus(published ? ProfileStatus.PUBLISHED : ProfileStatus.DRAFT);
        version.setPublished(published);
        version.setChatModel(published ? "qwen-max" : "qwen-plus");
        version.setRoutingModel("qwen-plus");
        version.setEmbeddingModel("text-embedding-v3");
        version.setRerankModel("gte-rerank");
        version.setTemperature(0.2D);
        version.setRetrievalTopK(6);
        version.setReasoningBudget(1);
        version.setSystemPrompt(profileName + " prompt");
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
