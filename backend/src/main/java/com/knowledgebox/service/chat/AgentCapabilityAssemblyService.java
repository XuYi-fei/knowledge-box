package com.knowledgebox.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.IntegrationSecretCipherService;
import com.knowledgebox.service.integration.SkillPackageStorageService;
import com.knowledgebox.service.integration.ToolRuntimeFactoryService;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.SkillHook;
import io.agentscope.core.skill.util.SkillUtil;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentCapabilityAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityAssemblyService.class);
    private static final String BUILTIN_KB_GROUP = "builtin-kb";

    private final AgentProfileVersionToolBindingRepository toolBindingRepository;
    private final AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    private final AgentProfileVersionSkillBindingRepository skillBindingRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillCatalogRepository;
    private final ToolRuntimeFactoryService toolRuntimeFactoryService;
    private final SkillPackageStorageService skillPackageStorageService;
    private final IntegrationSecretCipherService secretCipherService;
    private final KnowledgeBaseSearchTool knowledgeBaseSearchTool;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentSkill> skillCache = new ConcurrentHashMap<>();

    public AgentCapabilityAssemblyService(
            AgentProfileVersionToolBindingRepository toolBindingRepository,
            AgentProfileVersionMcpBindingRepository mcpBindingRepository,
            AgentProfileVersionSkillBindingRepository skillBindingRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillCatalogRepository,
            ToolRuntimeFactoryService toolRuntimeFactoryService,
            SkillPackageStorageService skillPackageStorageService,
            IntegrationSecretCipherService secretCipherService,
            KnowledgeBaseSearchTool knowledgeBaseSearchTool,
            ObjectMapper objectMapper
    ) {
        this.toolBindingRepository = toolBindingRepository;
        this.mcpBindingRepository = mcpBindingRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillCatalogRepository = skillCatalogRepository;
        this.toolRuntimeFactoryService = toolRuntimeFactoryService;
        this.skillPackageStorageService = skillPackageStorageService;
        this.secretCipherService = secretCipherService;
        this.knowledgeBaseSearchTool = knowledgeBaseSearchTool;
        this.objectMapper = objectMapper;
    }

    public AgentRuntimeCapabilities assemble(Long profileVersionId, boolean includeKnowledgeBaseTool) {
        Toolkit toolkit = new Toolkit();
        if (includeKnowledgeBaseTool) {
            ensureToolGroup(toolkit, BUILTIN_KB_GROUP, "Built-in Knowledge Box tools");
            toolkit.registration()
                    .tool(knowledgeBaseSearchTool)
                    .group(BUILTIN_KB_GROUP)
                    .apply();
        }
        if (profileVersionId == null) {
            return new AgentRuntimeCapabilities(toolkit, null, List.of());
        }

        registerDynamicTools(profileVersionId, toolkit);
        registerDynamicMcpClients(profileVersionId, toolkit);
        SkillBox skillBox = registerDynamicSkills(profileVersionId, toolkit);
        if (skillBox == null) {
            return new AgentRuntimeCapabilities(toolkit, null, List.of());
        }
        return new AgentRuntimeCapabilities(toolkit, skillBox, List.of(new SkillHook(skillBox)));
    }

    private void registerDynamicTools(Long profileVersionId, Toolkit toolkit) {
        List<AgentProfileVersionToolBinding> bindings = toolBindingRepository.findByProfileVersionId(profileVersionId);
        for (AgentProfileVersionToolBinding binding : bindings) {
            ToolDefinition definition = toolDefinitionRepository.findById(binding.getToolId()).orElse(null);
            if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(definition.getClassName())) {
                log.warn("Skip tool {} because className is blank", definition.getCode());
                continue;
            }
            try {
                String groupName = "tool-" + definition.getCode();
                ensureToolGroup(toolkit, groupName, "Dynamic tool group for " + definition.getCode());
                Object toolObject = toolRuntimeFactoryService.createToolObject(definition.getClassName(), definition.getBeanName());
                Toolkit.ToolRegistration registration = toolkit.registration()
                        .tool(toolObject)
                        .group(groupName);
                Map<String, Map<String, Object>> presetParameters = readPresetParameters(definition.getConfigJson());
                if (!presetParameters.isEmpty()) {
                    registration.presetParameters(presetParameters);
                }
                registration.apply();
            } catch (Exception exception) {
                log.warn("Skip dynamic tool registration for code={}", definition.getCode(), exception);
            }
        }
    }

    private void registerDynamicMcpClients(Long profileVersionId, Toolkit toolkit) {
        List<AgentProfileVersionMcpBinding> bindings = mcpBindingRepository.findByProfileVersionId(profileVersionId);
        for (AgentProfileVersionMcpBinding binding : bindings) {
            McpServerConfig config = mcpServerConfigRepository.findById(binding.getMcpId()).orElse(null);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                continue;
            }
            if (!"sse".equalsIgnoreCase(config.getTransportType())) {
                log.warn("Skip MCP {} because only sse transport is supported", config.getCode());
                continue;
            }
            try {
                McpClientBuilder builder = McpClientBuilder.create(config.getCode())
                        .sseTransport(config.getTarget());
                Map<String, String> queryParams = readStringMap(config.getQueryParamsJson());
                if (!queryParams.isEmpty()) {
                    builder.queryParams(queryParams);
                }
                Map<String, String> decryptedHeaders = decryptHeaderMap(config.getHeadersEncryptedJson());
                if (!decryptedHeaders.isEmpty()) {
                    builder.headers(decryptedHeaders);
                }
                if (config.getTimeoutMs() != null && config.getTimeoutMs() > 0) {
                    builder.timeout(Duration.ofMillis(config.getTimeoutMs()));
                }
                if (config.getInitializationTimeoutMs() != null && config.getInitializationTimeoutMs() > 0) {
                    builder.initializationTimeout(Duration.ofMillis(config.getInitializationTimeoutMs()));
                }
                String groupName = "mcp-" + config.getCode();
                ensureToolGroup(toolkit, groupName, "Dynamic MCP group for " + config.getCode());
                Toolkit.ToolRegistration registration = toolkit.registration()
                        .mcpClient(builder.buildSync())
                        .group(groupName);
                List<String> enableTools = readStringList(binding.getEnableToolsJson());
                List<String> disableTools = readStringList(binding.getDisableToolsJson());
                if (!enableTools.isEmpty()) {
                    registration.enableTools(enableTools);
                }
                if (!disableTools.isEmpty()) {
                    registration.disableTools(disableTools);
                }
                registration.apply();
            } catch (Exception exception) {
                log.warn("Skip dynamic MCP registration for code={}", config.getCode(), exception);
            }
        }
    }

    private SkillBox registerDynamicSkills(Long profileVersionId, Toolkit toolkit) {
        List<AgentProfileVersionSkillBinding> bindings = skillBindingRepository.findByProfileVersionId(profileVersionId);
        List<AgentSkill> skills = new ArrayList<>();
        for (AgentProfileVersionSkillBinding binding : bindings) {
            SkillBinding skillBinding = skillCatalogRepository.findById(binding.getSkillId()).orElse(null);
            if (skillBinding == null || !Boolean.TRUE.equals(skillBinding.getEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(skillBinding.getOssObjectKey()) || !StringUtils.hasText(skillBinding.getChecksumMd5())) {
                continue;
            }
            try {
                String cacheKey = skillBinding.getCode() + ":" + skillBinding.getChecksumMd5();
                AgentSkill skill = skillCache.computeIfAbsent(cacheKey, key -> {
                    byte[] zipBytes = skillPackageStorageService.load(skillBinding.getOssObjectKey());
                    return SkillUtil.createFromZip(zipBytes, "knowledge-box-runtime");
                });
                skills.add(skill);
            } catch (Exception exception) {
                log.warn("Skip dynamic skill registration for code={}", skillBinding.getCode(), exception);
            }
        }
        if (skills.isEmpty()) {
            return null;
        }
        SkillBox skillBox = new SkillBox(toolkit);
        for (AgentSkill skill : skills) {
            skillBox.registerSkill(skill);
        }
        return skillBox;
    }

    private Map<String, Map<String, Object>> readPresetParameters(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Map<String, Object>> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> valueMap)) {
                    continue;
                }
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<?, ?> valueEntry : valueMap.entrySet()) {
                    if (valueEntry.getKey() instanceof String valueKey) {
                        values.put(valueKey, valueEntry.getValue());
                    }
                }
                normalized.put(key, values);
            }
            return normalized;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Map<String, String> decryptHeaderMap(String encryptedJson) {
        Map<String, String> encrypted = readStringMap(encryptedJson);
        if (encrypted.isEmpty()) {
            return Map.of();
        }
        Map<String, String> decrypted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encrypted.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            decrypted.put(entry.getKey(), secretCipherService.decrypt(entry.getValue()));
        }
        return decrypted;
    }

    private Map<String, String> readStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return list.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void ensureToolGroup(Toolkit toolkit, String groupName, String description) {
        if (!StringUtils.hasText(groupName)) {
            return;
        }
        if (toolkit.getToolGroup(groupName) != null) {
            return;
        }
        toolkit.createToolGroup(groupName, description);
    }

    public record AgentRuntimeCapabilities(
            Toolkit toolkit,
            SkillBox skillBox,
            List<Hook> hooks
    ) {
    }
}
