package com.knowledgebox.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentProfileVersionBindingsView;
import com.knowledgebox.api.ConfigBundleImportCommitRequest;
import com.knowledgebox.api.ConfigBundleImportDecisionRequest;
import com.knowledgebox.api.ConfigBundleResourceType;
import com.knowledgebox.domain.agent.AgentProfile;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.agent.ModelType;
import com.knowledgebox.domain.agent.ProfileStatus;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.AgentProfileBindingService;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import com.knowledgebox.service.integration.IntegrationSecretCipherService;
import com.knowledgebox.service.integration.SkillPackageStorageService;
import com.knowledgebox.service.integration.ToolRuntimeFactoryService;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ConfigBundleAdminServiceTests {

    @Mock
    private AgentProfileRepository agentProfileRepository;
    @Mock
    private AgentProfileVersionRepository agentProfileVersionRepository;
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
    @Mock
    private ToolRuntimeFactoryService toolRuntimeFactoryService;
    @Mock
    private SkillPackageStorageService skillPackageStorageService;
    @Mock
    private IntegrationSecretCipherService secretCipherService;
    @Mock
    private AgentConfigAdminService agentConfigAdminService;

    private ConfigBundleAdminService service;

    @BeforeEach
    void setUp() {
        service = new ConfigBundleAdminService(
                agentProfileRepository,
                agentProfileVersionRepository,
                modelCatalogRepository,
                toolDefinitionRepository,
                mcpServerConfigRepository,
                skillBindingRepository,
                agentProfileBindingService,
                policyService,
                toolRuntimeFactoryService,
                skillPackageStorageService,
                secretCipherService,
                new DefaultResourceLoader(),
                agentConfigAdminService,
                new ObjectMapper()
        );
        when(policyService.normalizeType(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(toolRuntimeFactoryService.validate(any(), any()))
                .thenReturn(new ToolRuntimeFactoryService.ToolValidationResult("com.example.tools.SampleTool", List.of("sample")));
        lenient().when(secretCipherService.decrypt("enc-token")).thenReturn("plain-token");
        mockEnabledModels();
    }

    @Test
    void shouldPreviewAndCommitBundleImportWithCrossResourceReferences() {
        AgentProfileVersion mainVersion = version(10L, 1L, "default-main", "Default Main", AgentProfileVersionType.MAIN, true, 1);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(mainVersion));
        when(agentProfileBindingService.bindings(10L)).thenReturn(emptyBindings(10L));

        SkillBinding existingSkill = new SkillBinding();
        existingSkill.setCode("existing-skill");
        existingSkill.setName("Existing Skill");
        existingSkill.setDescription("existing");
        existingSkill.setSourceType("UPLOAD");
        existingSkill.setChecksumMd5("abc123");
        existingSkill.setOssObjectKey("skills/existing.zip");
        existingSkill.setEnabled(Boolean.TRUE);
        setId(existingSkill, 5L);
        when(skillBindingRepository.findAll()).thenReturn(List.of(existingSkill));

        when(toolDefinitionRepository.findAll()).thenReturn(List.of());
        when(mcpServerConfigRepository.findAll()).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bundle.json",
                "application/json",
                bundlePayload().getBytes(StandardCharsets.UTF_8)
        );

        var preview = service.previewImport(file);

        assertThat(preview.totalCount()).isEqualTo(4);
        assertThat(preview.creatableCount()).isEqualTo(3);
        assertThat(preview.codeConflictCount()).isEqualTo(1);
        assertThat(preview.validationErrorCount()).isEqualTo(0);
        assertThat(preview.items()).extracting(item -> item.resourceType().name())
                .containsExactly("TOOL", "MCP", "SKILL", "AGENT");

        when(toolDefinitionRepository.save(any(ToolDefinition.class))).thenAnswer(invocation -> {
            ToolDefinition definition = invocation.getArgument(0);
            if (definition.getId() == null) {
                setId(definition, 20L);
            }
            return definition;
        });
        when(mcpServerConfigRepository.save(any(McpServerConfig.class))).thenAnswer(invocation -> {
            McpServerConfig config = invocation.getArgument(0);
            if (config.getId() == null) {
                setId(config, 30L);
            }
            return config;
        });
        when(agentProfileRepository.save(any(AgentProfile.class))).thenAnswer(invocation -> {
            AgentProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) {
                setId(profile, 2L);
            }
            return profile;
        });
        when(agentProfileVersionRepository.save(any(AgentProfileVersion.class))).thenAnswer(invocation -> {
            AgentProfileVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                setId(version, 11L);
            }
            return version;
        });

        var result = service.commitImport(new ConfigBundleImportCommitRequest(preview.previewToken(), List.of(
                new ConfigBundleImportDecisionRequest(ConfigBundleResourceType.SKILL, "existing-skill", com.knowledgebox.api.AgentImportResolutionAction.SKIP)
        )));

        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(result.overwrittenCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(agentProfileBindingService).updateBindings(eq(11L), any());
    }

    @Test
    void shouldExportBundleWithPlainHeadersAndDefaultSkillLocation() {
        AgentProfileVersion mainVersion = version(10L, 1L, "default-main", "Default Main", AgentProfileVersionType.MAIN, true, 1);
        when(agentProfileVersionRepository.findAllForAdmin()).thenReturn(List.of(mainVersion));
        when(agentProfileBindingService.bindings(10L)).thenReturn(emptyBindings(10L));

        ToolDefinition tool = new ToolDefinition();
        tool.setCode("sample-tool");
        tool.setName("Sample Tool");
        tool.setClassName("com.example.tools.SampleTool");
        tool.setBeanName(null);
        tool.setConfigJson("{\"mode\":\"strict\"}");
        tool.setEnabled(Boolean.TRUE);
        setId(tool, 20L);
        when(toolDefinitionRepository.findAll()).thenReturn(List.of(tool));

        McpServerConfig mcp = new McpServerConfig();
        mcp.setCode("sample-mcp");
        mcp.setTransportType("sse");
        mcp.setTarget("http://localhost:9999/sse");
        mcp.setHeadersEncryptedJson("{\"Authorization\":\"enc-token\"}");
        mcp.setQueryParamsJson("{\"tenant\":\"kb\"}");
        mcp.setTimeoutMs(1000L);
        mcp.setInitializationTimeoutMs(2000L);
        mcp.setEnabled(Boolean.TRUE);
        setId(mcp, 30L);
        when(mcpServerConfigRepository.findAll()).thenReturn(List.of(mcp));

        SkillBinding skill = new SkillBinding();
        skill.setCode("sample-skill");
        skill.setName("Sample Skill");
        skill.setDescription("skill desc");
        skill.setSourceType("UPLOAD");
        skill.setChecksumMd5("md5");
        skill.setOssObjectKey("skills/sample.zip");
        skill.setEnabled(Boolean.TRUE);
        setId(skill, 40L);
        when(skillBindingRepository.findAll()).thenReturn(List.of(skill));

        var exported = service.exportCurrentBundle();

        assertThat(exported.tools()).hasSize(1);
        assertThat(exported.mcpServers()).hasSize(1);
        assertThat(exported.mcpServers().get(0).headers()).isEqualTo(Map.of("Authorization", "plain-token"));
        assertThat(exported.skills()).hasSize(1);
        assertThat(exported.skills().get(0).packageLocation()).isEqualTo("classpath:bootstrap/skills/sample-skill");
        assertThat(exported.agents()).hasSize(1);
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
        return new AgentProfileVersionBindingsView(versionId, List.of(), List.of(), List.of(), List.of());
    }

    private String bundlePayload() {
        return """
                {
                  "schemaVersion": "knowledge-box.config-bundle.v1",
                  "tools": [
                    {
                      "code": "sample-tool",
                      "name": "Sample Tool",
                      "className": "com.example.tools.SampleTool",
                      "beanName": null,
                      "configJson": "{}",
                      "enabled": true
                    }
                  ],
                  "mcpServers": [
                    {
                      "code": "sample-mcp",
                      "transportType": "sse",
                      "target": "http://localhost:9999/sse",
                      "headers": {
                        "Authorization": "Bearer demo"
                      },
                      "queryParams": {
                        "tenant": "kb"
                      },
                      "enabled": true
                    }
                  ],
                  "skills": [
                    {
                      "code": "existing-skill",
                      "name": "Existing Skill",
                      "description": "existing",
                      "sourceType": "UPLOAD",
                      "enabled": true
                    }
                  ],
                  "agents": [
                    {
                      "profileCode": "helper-agent",
                      "profileName": "Helper Agent",
                      "description": "helper",
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
                      "systemPrompt": "helper prompt",
                      "toolCodes": ["sample-tool"],
                      "skillCodes": ["existing-skill"],
                      "mcpBindings": [
                        {
                          "mcpCode": "sample-mcp",
                          "enableTools": [],
                          "disableTools": []
                        }
                      ],
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
