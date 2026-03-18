package com.knowledgebox.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.integration.AgentProfileVersionAgentBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import com.knowledgebox.service.integration.IntegrationSecretCipherService;
import com.knowledgebox.service.integration.SkillPackageStorageService;
import com.knowledgebox.service.integration.ToolRuntimeFactoryService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.SkillHook;
import io.agentscope.core.skill.util.SkillUtil;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentCapabilityAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityAssemblyService.class);
    private static final String BUILTIN_KB_GROUP = "builtin-kb";

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final AgentProfileVersionAgentBindingRepository agentBindingRepository;
    private final AgentProfileVersionToolBindingRepository toolBindingRepository;
    private final AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    private final AgentProfileVersionSkillBindingRepository skillBindingRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillCatalogRepository;
    private final ToolRuntimeFactoryService toolRuntimeFactoryService;
    private final SkillPackageStorageService skillPackageStorageService;
    private final IntegrationSecretCipherService secretCipherService;
    private final AgentProfileVersionPolicyService policyService;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final KnowledgeBaseSearchTool knowledgeBaseSearchTool;
    private final WebSearchTool webSearchTool;
    private final ObjectMapper objectMapper;
    private final ChatModelFactory chatModelFactory;
    private final AgentRuntimeEnvironmentResolver environmentResolver;
    private final Map<String, AgentSkill> skillCache = new ConcurrentHashMap<>();

    public AgentCapabilityAssemblyService(
            KnowledgeBoxProperties properties,
            AgentProfileVersionRepository agentProfileVersionRepository,
            AgentProfileVersionAgentBindingRepository agentBindingRepository,
            AgentProfileVersionToolBindingRepository toolBindingRepository,
            AgentProfileVersionMcpBindingRepository mcpBindingRepository,
            AgentProfileVersionSkillBindingRepository skillBindingRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillCatalogRepository,
            ToolRuntimeFactoryService toolRuntimeFactoryService,
            SkillPackageStorageService skillPackageStorageService,
            IntegrationSecretCipherService secretCipherService,
            AgentProfileVersionPolicyService policyService,
            AgentExecutionTraceService agentExecutionTraceService,
            KnowledgeBaseSearchTool knowledgeBaseSearchTool,
            WebSearchTool webSearchTool,
            AgentRuntimeEnvironmentResolver environmentResolver,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:${spring.ai.alibaba.dashscope.api-key:}}") String dashScopeApiKey,
            @Value("${spring.ai.dashscope.base-url:}") String dashScopeBaseUrl
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.agentBindingRepository = agentBindingRepository;
        this.toolBindingRepository = toolBindingRepository;
        this.mcpBindingRepository = mcpBindingRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillCatalogRepository = skillCatalogRepository;
        this.toolRuntimeFactoryService = toolRuntimeFactoryService;
        this.skillPackageStorageService = skillPackageStorageService;
        this.secretCipherService = secretCipherService;
        this.policyService = policyService;
        this.agentExecutionTraceService = agentExecutionTraceService;
        this.knowledgeBaseSearchTool = knowledgeBaseSearchTool;
        this.webSearchTool = webSearchTool;
        this.environmentResolver = environmentResolver;
        this.objectMapper = objectMapper;
        this.chatModelFactory = new ChatModelFactory(properties, dashScopeApiKey, dashScopeBaseUrl);
    }

    public AgentRuntimeCapabilities assemble(Long profileVersionId, boolean includeKnowledgeBaseTool) {
        return assemble(profileVersionId, includeKnowledgeBaseTool, null, null);
    }

    public AgentRuntimeCapabilities assemble(
            Long profileVersionId,
            boolean includeKnowledgeBaseTool,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        return assembleInternal(profileVersionId, includeKnowledgeBaseTool, true, traceContext, exchangeRuntime);
    }

    private AgentRuntimeCapabilities assembleInternal(
            Long profileVersionId,
            boolean includeKnowledgeBaseTool,
            boolean includeChildAgents,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        Toolkit toolkit = new Toolkit();
        Map<String, Map<String, Object>> subAgentToolMetadata = new LinkedHashMap<>();
        if (includeKnowledgeBaseTool) {
            ensureToolGroup(toolkit, BUILTIN_KB_GROUP, "Built-in Knowledge Box tools");
            toolkit.registration()
                    .tool(knowledgeBaseSearchTool)
                    .group(BUILTIN_KB_GROUP)
                    .apply();
        }
        if (profileVersionId == null) {
            return new AgentRuntimeCapabilities(toolkit, null, List.of(), subAgentToolMetadata, AgentRuntimeEnvironment.empty());
        }

        AgentProfileVersion profileVersion = policyService.requireVersion(profileVersionId);
        AgentRuntimeEnvironment runtimeEnvironment = environmentResolver.resolve(profileVersionId);
        registerDynamicTools(profileVersionId, toolkit);
        registerDynamicMcpClients(profileVersionId, toolkit, runtimeEnvironment);
        if (includeChildAgents && canBindChildAgents(profileVersion)) {
            registerChildAgents(profileVersion, toolkit, subAgentToolMetadata, traceContext, exchangeRuntime);
        }
        SkillBox skillBox = registerDynamicSkills(profileVersionId, toolkit);
        if (skillBox == null) {
            return new AgentRuntimeCapabilities(toolkit, null, List.of(), subAgentToolMetadata, runtimeEnvironment);
        }
        return new AgentRuntimeCapabilities(toolkit, skillBox, List.of(new SkillHook(skillBox)), subAgentToolMetadata, runtimeEnvironment);
    }

    private boolean canBindChildAgents(AgentProfileVersion profileVersion) {
        AgentProfileVersionType agentType = policyService.normalizeType(profileVersion.getAgentType());
        return agentType == AgentProfileVersionType.MAIN
                || agentType == AgentProfileVersionType.ENTRY
                || agentType == AgentProfileVersionType.ORCHESTRATOR;
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

    private void registerDynamicMcpClients(Long profileVersionId, Toolkit toolkit, AgentRuntimeEnvironment runtimeEnvironment) {
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
                Map<String, String> queryParams = resolveTemplateMap(readStringMap(config.getQueryParamsJson()), runtimeEnvironment);
                if (!queryParams.isEmpty()) {
                    builder.queryParams(queryParams);
                }
                Map<String, String> decryptedHeaders = resolveTemplateMap(decryptHeaderMap(config.getHeadersEncryptedJson()), runtimeEnvironment);
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

    private void registerChildAgents(
            AgentProfileVersion parentProfileVersion,
            Toolkit toolkit,
            Map<String, Map<String, Object>> subAgentToolMetadata,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        List<Long> childIds = agentBindingRepository.findByParentProfileVersionId(parentProfileVersion.getId())
                .stream()
                .map(AgentProfileVersionAgentBinding::getChildProfileVersionId)
                .distinct()
                .toList();
        if (childIds.isEmpty()) {
            return;
        }
        List<AgentProfileVersion> childVersions = agentProfileVersionRepository.findAllByIdIn(childIds).stream()
                .filter(version -> policyService.normalizeType(version.getAgentType()) == AgentProfileVersionType.ATOMIC)
                .toList();
        for (AgentProfileVersion childVersion : childVersions) {
            try {
                String toolName = subAgentToolName(childVersion);
                toolkit.registerAgentTool(new SubAgentTool(
                        () -> createChildAgent(childVersion, traceContext, exchangeRuntime),
                        SubAgentConfig.builder()
                                .toolName(toolName)
                                .description(subAgentDescription(childVersion))
                                .forwardEvents(false)
                                .build()
                ));
                subAgentToolMetadata.put(toolName, Map.of(
                        "toolKind", "SUB_AGENT",
                        "childProfileVersionId", childVersion.getId(),
                        "childProfileCode", childVersion.getProfile().getCode(),
                        "childProfileName", childVersion.getProfile().getName(),
                        "childVersionNumber", childVersion.getVersionNumber(),
                        "childAgentType", policyService.normalizeType(childVersion.getAgentType()).name()
                ));
            } catch (Exception exception) {
                log.warn(
                        "Skip child agent registration for parent={} child={} v{}",
                        parentProfileVersion.getProfile().getCode(),
                        childVersion.getProfile().getCode(),
                        childVersion.getVersionNumber(),
                        exception
                );
            }
        }
    }

    private ReActAgent createChildAgent(
            AgentProfileVersion childVersion,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        AgentRuntimeCapabilities childCapabilities = assembleInternal(
                childVersion.getId(),
                false,
                false,
                traceContext,
                exchangeRuntime
        );
        List<Hook> hooks = new ArrayList<>();
        if (childCapabilities.hooks() != null && !childCapabilities.hooks().isEmpty()) {
            hooks.addAll(childCapabilities.hooks());
        }
        if (traceContext != null) {
            String spanId = agentExecutionTraceService.nextSpanIdValue();
            Map<String, Object> spanTags = new LinkedHashMap<>();
            spanTags.put("agentKind", "SUB_AGENT");
            spanTags.put("profileCode", childVersion.getProfile().getCode());
            spanTags.put("profileName", childVersion.getProfile().getName());
            spanTags.put("profileVersionId", childVersion.getId());
            spanTags.put("versionNumber", childVersion.getVersionNumber());
            hooks.add(new AgentExecutionTraceHook(
                    agentExecutionTraceService,
                    traceContext,
                    spanId,
                    () -> spanId,
                    new AgentExecutionTraceHook.InvocationSpanDescriptor(
                            spanId,
                            null,
                            "agent.execute[" + childVersion.getProfile().getCode() + " v" + childVersion.getVersionNumber() + "]",
                            com.knowledgebox.domain.chat.AgentExecutionSpanType.AGENT,
                            spanTags
                    ),
                    childCapabilities.subAgentToolMetadataByName()
            ));
        }
        ToolExecutionContext toolExecutionContext = null;
        if (traceContext != null || exchangeRuntime != null || childCapabilities.runtimeEnvironment() != null) {
            ToolExecutionContext.Builder builder = ToolExecutionContext.builder();
            if (traceContext != null) {
                builder.register(AgentExecutionTraceContext.class, traceContext);
            }
            if (exchangeRuntime != null) {
                builder.register(ChatExchangeRuntime.class, exchangeRuntime);
            }
            if (childCapabilities.runtimeEnvironment() != null) {
                builder.register(AgentRuntimeEnvironment.class, childCapabilities.runtimeEnvironment());
            }
            toolExecutionContext = builder.build();
        }
        return chatModelFactory.createReActAgent(
                childVersion,
                childVersion.getChatModel(),
                false,
                false,
                false,
                childCapabilities.toolkit(),
                childCapabilities.skillBox(),
                hooks,
                toolExecutionContext
        );
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

    private String subAgentToolName(AgentProfileVersion version) {
        return "agent_" + sanitizeToolToken(version.getProfile().getCode()) + "_v" + version.getVersionNumber();
    }

    private String subAgentDescription(AgentProfileVersion version) {
        String description = version.getProfile().getDescription();
        String suffix = StringUtils.hasText(description) ? "。用途：" + description.trim() : "";
        return "调用原子 Agent " + version.getProfile().getName() + "（" + version.getProfile().getCode() + " v" + version.getVersionNumber() + "）" + suffix;
    }

    private String sanitizeToolToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "atomic_agent";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "atomic_agent" : normalized;
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

    private Map<String, String> resolveTemplateMap(Map<String, String> values, AgentRuntimeEnvironment runtimeEnvironment) {
        if (values.isEmpty() || runtimeEnvironment == null) {
            return values;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        values.forEach((key, value) -> resolved.put(key, runtimeEnvironment.resolvePlaceholders(value)));
        return resolved;
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
            List<Hook> hooks,
            Map<String, Map<String, Object>> subAgentToolMetadataByName,
            AgentRuntimeEnvironment runtimeEnvironment
    ) {
    }
}
