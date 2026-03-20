package com.knowledgebox.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgebox.api.AgentConfigMcpBindingView;
import com.knowledgebox.api.AgentConfigSnapshotView;
import com.knowledgebox.api.AgentImportItemStatus;
import com.knowledgebox.api.AgentImportResolutionAction;
import com.knowledgebox.api.AgentRuntimeEnvVarView;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
import com.knowledgebox.api.ConfigBundleExportView;
import com.knowledgebox.api.ConfigBundleImportCommitRequest;
import com.knowledgebox.api.ConfigBundleImportCommitResultView;
import com.knowledgebox.api.ConfigBundleImportDecisionRequest;
import com.knowledgebox.api.ConfigBundleImportPreviewItemView;
import com.knowledgebox.api.ConfigBundleImportPreviewView;
import com.knowledgebox.api.ConfigBundleMcpServerView;
import com.knowledgebox.api.ConfigBundleResourceType;
import com.knowledgebox.api.ConfigBundleSkillView;
import com.knowledgebox.api.ConfigBundleToolView;
import com.knowledgebox.api.RuntimeEnvRequirementView;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
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
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConfigBundleAdminService {

    private static final String DEFAULT_SCHEMA_VERSION = "knowledge-box.config-bundle.v2";
    private static final String LEGACY_AGENT_SCHEMA_VERSION = "knowledge-box.agent-config.v1";
    private static final Pattern PROFILE_CODE_PATTERN = Pattern.compile("^[a-z0-9]+(?:[-_][a-z0-9]+)*$");
    private static final Pattern RESOURCE_CODE_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    private final AgentProfileRepository agentProfileRepository;
    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillBindingRepository;
    private final AgentProfileBindingService agentProfileBindingService;
    private final AgentProfileVersionPolicyService policyService;
    private final ToolRuntimeFactoryService toolRuntimeFactoryService;
    private final SkillPackageStorageService skillPackageStorageService;
    private final IntegrationSecretCipherService secretCipherService;
    private final ResourceLoader resourceLoader;
    private final AgentConfigAdminService agentConfigAdminService;
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourcePatternResolver;
    private final Map<String, StoredPreview> previewStore = new ConcurrentHashMap<>();

    public ConfigBundleAdminService(
            AgentProfileRepository agentProfileRepository,
            AgentProfileVersionRepository agentProfileVersionRepository,
            ModelCatalogRepository modelCatalogRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillBindingRepository,
            AgentProfileBindingService agentProfileBindingService,
            AgentProfileVersionPolicyService policyService,
            ToolRuntimeFactoryService toolRuntimeFactoryService,
            SkillPackageStorageService skillPackageStorageService,
            IntegrationSecretCipherService secretCipherService,
            ResourceLoader resourceLoader,
            AgentConfigAdminService agentConfigAdminService,
            ObjectMapper objectMapper
    ) {
        this.agentProfileRepository = agentProfileRepository;
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.agentProfileBindingService = agentProfileBindingService;
        this.policyService = policyService;
        this.toolRuntimeFactoryService = toolRuntimeFactoryService;
        this.skillPackageStorageService = skillPackageStorageService;
        this.secretCipherService = secretCipherService;
        this.resourceLoader = resourceLoader;
        this.agentConfigAdminService = agentConfigAdminService;
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
    }

    @Transactional(readOnly = true)
    public ConfigBundleExportView exportCurrentBundle() {
        List<ConfigBundleToolView> tools = toolDefinitionRepository.findAll().stream()
                .sorted(Comparator.comparing(ToolDefinition::getCode))
                .map(this::toToolView)
                .toList();
        List<ConfigBundleMcpServerView> mcpServers = mcpServerConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(McpServerConfig::getCode))
                .map(this::toMcpView)
                .toList();
        List<ConfigBundleSkillView> skills = skillBindingRepository.findAll().stream()
                .sorted(Comparator.comparing(SkillBinding::getCode))
                .map(this::toSkillView)
                .toList();
        List<AgentConfigSnapshotView> agents = loadCurrentAgentSnapshots().values().stream()
                .sorted(Comparator.comparing(StoredAgentSnapshot::profileCode))
                .map(StoredAgentSnapshot::toView)
                .toList();
        return new ConfigBundleExportView(DEFAULT_SCHEMA_VERSION, OffsetDateTime.now(), tools, mcpServers, skills, agents);
    }

    @Transactional(readOnly = true)
    public ConfigBundleImportPreviewView previewImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Config bundle file is required");
        }
        ParsedConfigBundle bundle = parseBundle(readBytes(file), file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots = loadExistingSnapshotMaps();
        DefaultPlan defaultPlan = buildDefaultPlan(bundle, existingSnapshots);
        List<ConfigBundleImportPreviewItemView> items = defaultPlan.items().values().stream()
                .map(item -> toPreviewItem(item, defaultPlan.errorsByKey()))
                .toList();
        String token = storePreview(bundle);
        return new ConfigBundleImportPreviewView(
                token,
                bundle.schemaVersion(),
                items.size(),
                countByStatus(items, AgentImportItemStatus.READY_CREATE),
                countByStatus(items, AgentImportItemStatus.CODE_CONFLICT),
                countByStatus(items, AgentImportItemStatus.NAME_CONFLICT),
                countByStatus(items, AgentImportItemStatus.VALIDATION_ERROR),
                defaultPlan.globalMessages(),
                items
        );
    }

    @Transactional
    public ConfigBundleImportCommitResultView commitImport(ConfigBundleImportCommitRequest request) {
        StoredPreview storedPreview = loadPreview(request.previewToken());
        ParsedConfigBundle bundle = storedPreview.bundle();
        Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots = loadExistingSnapshotMaps();
        Map<ResourceKey, AgentImportResolutionAction> decisions = normalizeDecisionMap(request.decisions());
        PlannedExecution execution = buildExecutionPlan(bundle, existingSnapshots, decisions, true);
        AppliedExecution appliedExecution = applyExecution(execution);
        previewStore.remove(request.previewToken());
        return new ConfigBundleImportCommitResultView(
                appliedExecution.createdCount(),
                appliedExecution.overwrittenCount(),
                appliedExecution.skippedCount(),
                appliedExecution.messages()
        );
    }

    @Transactional
    public AgentConfigAdminService.BootstrapImportResult importForBootstrap(InputStream inputStream, String sourceDescription, boolean failFast) {
        byte[] bytes = readBytes(inputStream);
        ParsedConfigBundle bundle = parseBundle(bytes, sourceDescription);
        Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots = loadExistingSnapshotMaps();
        PlannedExecution execution = buildExecutionPlan(bundle, existingSnapshots, Map.of(), false);
        if (failFast && execution.failedCount() > 0) {
            throw new IllegalStateException(execution.failureSummary());
        }
        AppliedExecution appliedExecution = applyExecution(execution);
        ArrayList<String> messages = new ArrayList<>(execution.globalMessages());
        messages.addAll(appliedExecution.messages());
        return new AgentConfigAdminService.BootstrapImportResult(
                appliedExecution.createdCount(),
                appliedExecution.skippedCount(),
                execution.failedCount(),
                List.copyOf(messages)
        );
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read import file", exception);
        }
    }

    private byte[] readBytes(InputStream inputStream) {
        try {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read config bundle stream", exception);
        }
    }

    private String storePreview(ParsedConfigBundle bundle) {
        cleanupExpiredPreviews();
        String token = UUID.randomUUID().toString();
        previewStore.put(token, new StoredPreview(bundle, OffsetDateTime.now()));
        return token;
    }

    private StoredPreview loadPreview(String token) {
        cleanupExpiredPreviews();
        StoredPreview preview = previewStore.get(token);
        if (preview == null) {
            throw new IllegalArgumentException("Import preview has expired or does not exist");
        }
        return preview;
    }

    private void cleanupExpiredPreviews() {
        OffsetDateTime now = OffsetDateTime.now();
        previewStore.entrySet().removeIf(entry -> entry.getValue().createdAt().plus(PREVIEW_TTL).isBefore(now));
    }

    private int countByStatus(List<ConfigBundleImportPreviewItemView> items, AgentImportItemStatus status) {
        return Math.toIntExact(items.stream().filter(item -> item.status() == status).count());
    }

    private ConfigBundleImportPreviewItemView toPreviewItem(PreviewItem item, Map<ResourceKey, List<String>> errorsByKey) {
        List<String> errors = errorsByKey.getOrDefault(item.key(), List.of());
        AgentImportItemStatus status = errors.isEmpty() ? item.status() : AgentImportItemStatus.VALIDATION_ERROR;
        ArrayList<String> messages = new ArrayList<>(item.messages());
        messages.addAll(errors);
        return new ConfigBundleImportPreviewItemView(
                item.key().resourceType(),
                item.key().resourceCode(),
                item.displayName(),
                status,
                item.availableActions(),
                item.defaultAction(),
                List.copyOf(messages),
                item.incomingNode(),
                item.existingNode()
        );
    }

    private ParsedConfigBundle parseBundle(byte[] bytes, String sourceDescription) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            if (root == null || root.isNull()) {
                throw new IllegalArgumentException("Config bundle JSON is empty");
            }
            String schemaVersion = DEFAULT_SCHEMA_VERSION;
            JsonNode toolsNode = null;
            JsonNode mcpNode = null;
            JsonNode skillsNode = null;
            JsonNode agentsNode = null;
            if (root.isArray()) {
                schemaVersion = LEGACY_AGENT_SCHEMA_VERSION;
                agentsNode = root;
            } else if (root.isObject()) {
                schemaVersion = readOptionalText(root.get("schemaVersion"), DEFAULT_SCHEMA_VERSION);
                toolsNode = root.get("tools");
                mcpNode = root.get("mcpServers");
                skillsNode = root.get("skills");
                agentsNode = root.get("agents");
            } else {
                throw new IllegalArgumentException("Config bundle root must be a JSON object or array");
            }
            List<IncomingToolSnapshot> tools = normalizeTools(toolsNode);
            List<IncomingMcpSnapshot> mcpServers = normalizeMcpServers(mcpNode);
            List<IncomingSkillSnapshot> skills = normalizeSkills(skillsNode);
            List<IncomingAgentSnapshot> agents = normalizeAgents(agentsNode);
            return new ParsedConfigBundle(
                    StringUtils.hasText(schemaVersion) ? schemaVersion.trim() : DEFAULT_SCHEMA_VERSION,
                    sourceDescription,
                    resolveSourceBase(sourceDescription),
                    List.copyOf(tools),
                    countCodes(tools.stream().map(IncomingToolSnapshot::code).toList()),
                    countNames(tools.stream().map(IncomingToolSnapshot::name).toList()),
                    List.copyOf(mcpServers),
                    countCodes(mcpServers.stream().map(IncomingMcpSnapshot::code).toList()),
                    List.copyOf(skills),
                    countCodes(skills.stream().map(IncomingSkillSnapshot::code).toList()),
                    countNames(skills.stream().map(IncomingSkillSnapshot::name).toList()),
                    List.copyOf(agents),
                    countCodes(agents.stream().map(IncomingAgentSnapshot::profileCode).toList()),
                    countNames(agents.stream().map(IncomingAgentSnapshot::profileName).toList())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse config bundle JSON", exception);
        }
    }

    private Map<String, Integer> countCodes(List<String> codes) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String code : codes) {
            counts.merge(code, 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> countNames(List<String> names) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String name : names) {
            counts.merge(normalizeNameKey(name), 1, Integer::sum);
        }
        return counts;
    }

    private Path resolveSourceBase(String sourceDescription) {
        if (!StringUtils.hasText(sourceDescription)) {
            return null;
        }
        String normalized = sourceDescription.trim();
        try {
            if (normalized.startsWith("file:")) {
                Path path = Path.of(normalized.substring("file:".length())).toAbsolutePath().normalize();
                return Files.isDirectory(path) ? path : path.getParent();
            }
            if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:\\\\.*")) {
                Path path = Path.of(normalized).toAbsolutePath().normalize();
                return Files.isDirectory(path) ? path : path.getParent();
            }
            if (normalized.contains("/")) {
                Path path = Path.of(normalized).toAbsolutePath().normalize();
                return Files.isDirectory(path) ? path : path.getParent();
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private List<IncomingToolSnapshot> normalizeTools(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`tools` must be an array");
        }
        ArrayList<IncomingToolSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("Tool item at index %s must be a JSON object".formatted(index));
            }
            String code = normalizeResourceCode(readRequiredText(item.get("code"), "tools[%s].code".formatted(index)));
            String name = readRequiredText(item.get("name"), "tools[%s].name".formatted(index)).trim();
            String className = readRequiredText(item.get("className"), "tools[%s].className".formatted(index)).trim();
            String beanName = normalizeOptionalText(readOptionalText(item.get("beanName"), null));
            String configJson = normalizeJsonObject(readOptionalText(item.get("configJson"), "{}"), "tools[%s].configJson".formatted(index));
            List<RuntimeEnvRequirementView> runtimeEnvRequirements = normalizeRuntimeEnvRequirements(item.get("runtimeEnvRequirements"));
            boolean enabled = readBoolean(item.get("enabled"), true);
            snapshots.add(new IncomingToolSnapshot(code, name, className, beanName, configJson, runtimeEnvRequirements, enabled));
        }
        return List.copyOf(snapshots);
    }

    private List<IncomingMcpSnapshot> normalizeMcpServers(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`mcpServers` must be an array");
        }
        ArrayList<IncomingMcpSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("MCP item at index %s must be a JSON object".formatted(index));
            }
            String code = normalizeResourceCode(readRequiredText(item.get("code"), "mcpServers[%s].code".formatted(index)));
            String transportType = normalizeTransportType(readRequiredText(item.get("transportType"), "mcpServers[%s].transportType".formatted(index)));
            String target = readRequiredText(item.get("target"), "mcpServers[%s].target".formatted(index)).trim();
            Map<String, String> headers = normalizeStringMap(item.get("headers"), "mcpServers[%s].headers".formatted(index));
            Map<String, String> queryParams = normalizeStringMap(item.get("queryParams"), "mcpServers[%s].queryParams".formatted(index));
            List<RuntimeEnvRequirementView> runtimeEnvRequirements = normalizeRuntimeEnvRequirements(item.get("runtimeEnvRequirements"));
            Long timeoutMs = readLong(item.get("timeoutMs"));
            Long initializationTimeoutMs = readLong(item.get("initializationTimeoutMs"));
            boolean enabled = readBoolean(item.get("enabled"), true);
            snapshots.add(new IncomingMcpSnapshot(code, transportType, target, headers, queryParams, runtimeEnvRequirements, timeoutMs, initializationTimeoutMs, enabled));
        }
        return List.copyOf(snapshots);
    }

    private List<IncomingSkillSnapshot> normalizeSkills(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`skills` must be an array");
        }
        ArrayList<IncomingSkillSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("Skill item at index %s must be a JSON object".formatted(index));
            }
            String code = normalizeResourceCode(readRequiredText(item.get("code"), "skills[%s].code".formatted(index)));
            String name = readRequiredText(item.get("name"), "skills[%s].name".formatted(index)).trim();
            String description = normalizeOptionalText(readOptionalText(item.get("description"), null));
            String sourceType = normalizeOptionalText(readOptionalText(item.get("sourceType"), "UPLOAD"));
            String checksumMd5 = normalizeOptionalText(readOptionalText(item.get("checksumMd5"), null));
            String ossObjectKey = normalizeOptionalText(readOptionalText(item.get("ossObjectKey"), null));
            String packageLocation = normalizeOptionalText(readOptionalText(item.get("packageLocation"), defaultSkillPackageLocation(code)));
            List<RuntimeEnvRequirementView> runtimeEnvRequirements = normalizeRuntimeEnvRequirements(item.get("runtimeEnvRequirements"));
            boolean enabled = readBoolean(item.get("enabled"), true);
            snapshots.add(new IncomingSkillSnapshot(code, name, description, sourceType, checksumMd5, ossObjectKey, packageLocation, runtimeEnvRequirements, enabled));
        }
        return List.copyOf(snapshots);
    }

    private List<IncomingAgentSnapshot> normalizeAgents(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`agents` must be an array");
        }
        ArrayList<IncomingAgentSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("Agent item at index %s must be a JSON object".formatted(index));
            }
            String profileCode = normalizeProfileCode(readRequiredText(item.get("profileCode"), "agents[%s].profileCode".formatted(index)));
            String profileName = readRequiredText(item.get("profileName"), "agents[%s].profileName".formatted(index)).trim();
            String description = normalizeOptionalText(readOptionalText(item.get("description"), null));
            AgentProfileVersionType agentType = readEnum(item.get("agentType"), AgentProfileVersionType.class, "agents[%s].agentType".formatted(index));
            ProfileStatus status = readEnumOrDefault(item.get("status"), ProfileStatus.class, ProfileStatus.DRAFT);
            boolean published = readBoolean(item.get("published"), false);
            boolean publicDebug = readBoolean(item.get("publicDebug"), false);
            String chatModel = readRequiredText(item.get("chatModel"), "agents[%s].chatModel".formatted(index)).trim();
            String routingModel = normalizeOptionalText(readOptionalText(item.get("routingModel"), null));
            if (!StringUtils.hasText(routingModel)) {
                routingModel = chatModel;
            }
            String embeddingModel = readRequiredText(item.get("embeddingModel"), "agents[%s].embeddingModel".formatted(index)).trim();
            String rerankModel = normalizeOptionalText(readOptionalText(item.get("rerankModel"), null));
            double temperature = readDouble(item.get("temperature"), 0.2D);
            int retrievalTopK = readInt(item.get("retrievalTopK"), 6);
            int reasoningBudget = readInt(item.get("reasoningBudget"), 1);
            String systemPrompt = readRequiredText(item.get("systemPrompt"), "agents[%s].systemPrompt".formatted(index));
            List<String> toolCodes = normalizeCodeList(item.get("toolCodes"));
            List<String> skillCodes = normalizeCodeList(item.get("skillCodes"));
            List<AgentConfigMcpBindingView> mcpBindings = normalizeMcpBindings(item.get("mcpBindings"));
            List<String> childAgentProfileCodes = normalizeCodeList(item.get("childAgentProfileCodes"));
            List<AgentRuntimeEnvVarView> envVars = normalizeAgentEnvVars(item.get("envVars"));
            snapshots.add(new IncomingAgentSnapshot(
                    profileCode,
                    profileName,
                    description,
                    agentType,
                    status,
                    published,
                    publicDebug,
                    chatModel,
                    routingModel,
                    embeddingModel,
                    rerankModel,
                    temperature,
                    retrievalTopK,
                    reasoningBudget,
                    systemPrompt,
                    toolCodes,
                    skillCodes,
                    mcpBindings,
                    childAgentProfileCodes,
                    envVars
            ));
        }
        return List.copyOf(snapshots);
    }

    private DefaultPlan buildDefaultPlan(ParsedConfigBundle bundle, Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots) {
        LinkedHashMap<ResourceKey, PreviewItem> items = buildPreviewItems(bundle, existingSnapshots);
        Map<ResourceKey, AgentImportResolutionAction> actions = items.values().stream()
                .collect(Collectors.toMap(
                        PreviewItem::key,
                        PreviewItem::defaultAction,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        PlanComputation computation = computePlan(bundle, existingSnapshots, items, actions, false, true);
        return new DefaultPlan(items, computation.errorsByKey(), computation.globalMessages());
    }

    private PlannedExecution buildExecutionPlan(
            ParsedConfigBundle bundle,
            Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots,
            Map<ResourceKey, AgentImportResolutionAction> actionByKey,
            boolean strict
    ) {
        LinkedHashMap<ResourceKey, PreviewItem> items = buildPreviewItems(bundle, existingSnapshots);
        PlanComputation computation = computePlan(bundle, existingSnapshots, items, actionByKey, strict, false);
        if (strict && !computation.errorsByKey().isEmpty()) {
            throw new IllegalArgumentException(renderErrors(computation.errorsByKey(), computation.globalMessages()));
        }
        String failureSummary = computation.errorsByKey().isEmpty()
                ? ""
                : renderErrors(computation.errorsByKey(), computation.globalMessages());
        return new PlannedExecution(
                bundle,
                computation.operations(),
                computation.globalMessages(),
                computation.skippedCount(),
                computation.failedCount(),
                failureSummary
        );
    }

    private String renderErrors(Map<ResourceKey, List<String>> errorsByKey, List<String> globalMessages) {
        ArrayList<String> messages = new ArrayList<>(globalMessages);
        errorsByKey.forEach((key, errors) -> {
            if (errors == null || errors.isEmpty()) {
                return;
            }
            messages.add(key.resourceType() + ":" + key.resourceCode() + ": " + String.join("; ", errors));
        });
        return String.join("\n", messages);
    }

    private LinkedHashMap<ResourceKey, PreviewItem> buildPreviewItems(
            ParsedConfigBundle bundle,
            Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots
    ) {
        LinkedHashMap<ResourceKey, PreviewItem> items = new LinkedHashMap<>();
        addPreviewItems(items, ConfigBundleResourceType.TOOL, bundle.tools(), castMap(existingSnapshots.get(ConfigBundleResourceType.TOOL), StoredToolSnapshot.class), bundle.toolNameCounts());
        addPreviewItems(items, ConfigBundleResourceType.MCP, bundle.mcpServers(), castMap(existingSnapshots.get(ConfigBundleResourceType.MCP), StoredMcpSnapshot.class), Map.of());
        addPreviewItems(items, ConfigBundleResourceType.SKILL, bundle.skills(), castMap(existingSnapshots.get(ConfigBundleResourceType.SKILL), StoredSkillSnapshot.class), bundle.skillNameCounts());
        addPreviewItems(items, ConfigBundleResourceType.AGENT, bundle.agents(), castMap(existingSnapshots.get(ConfigBundleResourceType.AGENT), StoredAgentSnapshot.class), bundle.agentNameCounts());
        return items;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> castMap(Map<String, ?> map, Class<T> targetType) {
        return map == null ? Map.of() : (Map<String, T>) map;
    }

    private <T extends ImportableSnapshot<T, S>, S extends ExistingSnapshot<T>> void addPreviewItems(
            LinkedHashMap<ResourceKey, PreviewItem> items,
            ConfigBundleResourceType resourceType,
            List<T> incomingSnapshots,
            Map<String, S> existingSnapshots,
            Map<String, Integer> importNameCounts
    ) {
        Map<String, List<S>> existingByName = existingSnapshots.values().stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.displayName()))
                .collect(Collectors.groupingBy(snapshot -> normalizeNameKey(snapshot.displayName()), LinkedHashMap::new, Collectors.toList()));
        for (T snapshot : incomingSnapshots) {
            ResourceKey key = new ResourceKey(resourceType, snapshot.resourceCode());
            S existingByCode = existingSnapshots.get(snapshot.resourceCode());
            if (existingByCode != null) {
                items.put(key, new PreviewItem(
                        key,
                        snapshot.displayName(),
                        AgentImportItemStatus.CODE_CONFLICT,
                        List.of(AgentImportResolutionAction.SKIP, AgentImportResolutionAction.OVERWRITE_EXISTING),
                        AgentImportResolutionAction.SKIP,
                        List.of("数据库中已存在相同编码，将默认跳过；管理员可改为覆盖"),
                        snapshot.previewNode(objectMapper),
                        existingByCode.previewNode(objectMapper)
                ));
                continue;
            }
            if (StringUtils.hasText(snapshot.displayName())) {
                List<S> sameName = existingByName.getOrDefault(normalizeNameKey(snapshot.displayName()), List.of()).stream()
                        .filter(existing -> !Objects.equals(existing.resourceCode(), snapshot.resourceCode()))
                        .toList();
                if (!sameName.isEmpty()) {
                    S existing = sameName.size() == 1 ? sameName.get(0) : null;
                    String matchedCodes = sameName.stream().map(ExistingSnapshot::resourceCode).collect(Collectors.joining(", "));
                    items.put(key, new PreviewItem(
                            key,
                            snapshot.displayName(),
                            AgentImportItemStatus.NAME_CONFLICT,
                            List.of(AgentImportResolutionAction.SKIP),
                            AgentImportResolutionAction.SKIP,
                            List.of("数据库中已存在同名配置，涉及编码: " + matchedCodes + "；该项只能跳过"),
                            snapshot.previewNode(objectMapper),
                            existing == null ? null : existing.previewNode(objectMapper)
                    ));
                    continue;
                }
            }
            items.put(key, new PreviewItem(
                    key,
                    snapshot.displayName(),
                    AgentImportItemStatus.READY_CREATE,
                    List.of(AgentImportResolutionAction.CREATE),
                    AgentImportResolutionAction.CREATE,
                    List.of("将按配置直接创建该资源"),
                    snapshot.previewNode(objectMapper),
                    null
            ));
        }
    }

    private PlanComputation computePlan(
            ParsedConfigBundle bundle,
            Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots,
            LinkedHashMap<ResourceKey, PreviewItem> items,
            Map<ResourceKey, AgentImportResolutionAction> requestedActions,
            boolean strict,
            boolean previewMode
    ) {
        LinkedHashMap<ResourceKey, PlannedOperation> operations = new LinkedHashMap<>();
        LinkedHashMap<ResourceKey, List<String>> errorsByKey = new LinkedHashMap<>();
        ArrayList<String> globalMessages = new ArrayList<>();
        int skippedCount = 0;

        validateBundleLevelUniqueness(bundle, errorsByKey);
        for (PreviewItem item : items.values()) {
            AgentImportResolutionAction action = resolveAction(item, requestedActions.get(item.key()));
            if (action == AgentImportResolutionAction.CREATE || action == AgentImportResolutionAction.OVERWRITE_EXISTING) {
                operations.put(item.key(), new PlannedOperation(action, item.key(), item.displayName(), item.incomingNode(), item.existingNode()));
            } else {
                skippedCount++;
                if (!previewMode) {
                    globalMessages.add(item.key().resourceType() + ":" + item.key().resourceCode() + " 已按规则跳过: " + String.join("; ", item.messages()));
                }
            }
        }

        if (strict) {
            validateOperations(bundle, existingSnapshots, operations, errorsByKey);
        } else {
            int previousSize = -1;
            while (previousSize != operations.size()) {
                previousSize = operations.size();
                LinkedHashMap<ResourceKey, List<String>> iterationErrors = new LinkedHashMap<>();
                validateOperations(bundle, existingSnapshots, operations, iterationErrors);
                if (iterationErrors.isEmpty()) {
                    break;
                }
                if (previewMode) {
                    errorsByKey.putAll(iterationErrors);
                    break;
                }
                for (Map.Entry<ResourceKey, List<String>> entry : iterationErrors.entrySet()) {
                    errorsByKey.put(entry.getKey(), entry.getValue());
                    PlannedOperation removed = operations.remove(entry.getKey());
                    if (removed != null) {
                        globalMessages.add(entry.getKey().resourceType() + ":" + entry.getKey().resourceCode() + " 导入失败并已跳过: " + String.join("; ", entry.getValue()));
                    }
                }
            }
            if (previewMode) {
                LinkedHashMap<ResourceKey, List<String>> previewErrors = new LinkedHashMap<>();
                validateOperations(bundle, existingSnapshots, operations, previewErrors);
                previewErrors.forEach(errorsByKey::put);
            }
        }

        int failedCount = Math.toIntExact(errorsByKey.values().stream().filter(errors -> errors != null && !errors.isEmpty()).count());
        return new PlanComputation(operations, errorsByKey, List.copyOf(globalMessages), skippedCount, failedCount);
    }

    private void validateBundleLevelUniqueness(ParsedConfigBundle bundle, Map<ResourceKey, List<String>> errorsByKey) {
        for (IncomingToolSnapshot tool : bundle.tools()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.TOOL, tool.code());
            if (bundle.toolCodeCounts().getOrDefault(tool.code(), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 tool code: " + tool.code());
            }
            if (bundle.toolNameCounts().getOrDefault(normalizeNameKey(tool.name()), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 tool name: " + tool.name());
            }
            validateResourceCode(key, tool.code(), errorsByKey);
            if (!StringUtils.hasText(tool.name())) {
                addError(errorsByKey, key, "Tool name 不能为空");
            }
            if (!StringUtils.hasText(tool.className())) {
                addError(errorsByKey, key, "Tool className 不能为空");
            }
        }
        for (IncomingMcpSnapshot mcp : bundle.mcpServers()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.MCP, mcp.code());
            if (bundle.mcpCodeCounts().getOrDefault(mcp.code(), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 MCP code: " + mcp.code());
            }
            validateResourceCode(key, mcp.code(), errorsByKey);
            if (!StringUtils.hasText(mcp.target())) {
                addError(errorsByKey, key, "MCP target 不能为空");
            }
        }
        for (IncomingSkillSnapshot skill : bundle.skills()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.SKILL, skill.code());
            if (bundle.skillCodeCounts().getOrDefault(skill.code(), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 skill code: " + skill.code());
            }
            if (bundle.skillNameCounts().getOrDefault(normalizeNameKey(skill.name()), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 skill name: " + skill.name());
            }
            validateResourceCode(key, skill.code(), errorsByKey);
            if (!StringUtils.hasText(skill.name())) {
                addError(errorsByKey, key, "Skill name 不能为空");
            }
        }
        for (IncomingAgentSnapshot agent : bundle.agents()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.AGENT, agent.profileCode());
            if (bundle.agentCodeCounts().getOrDefault(agent.profileCode(), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 profileCode: " + agent.profileCode());
            }
            if (bundle.agentNameCounts().getOrDefault(normalizeNameKey(agent.profileName()), 0) > 1) {
                addError(errorsByKey, key, "JSON 中存在重复的 profileName: " + agent.profileName());
            }
            if (!PROFILE_CODE_PATTERN.matcher(agent.profileCode()).matches()) {
                addError(errorsByKey, key, "profileCode 仅支持小写字母、数字、-、_");
            }
            if (!StringUtils.hasText(agent.systemPrompt())) {
                addError(errorsByKey, key, "systemPrompt 不能为空");
            }
            if (agent.retrievalTopK() < 1) {
                addError(errorsByKey, key, "retrievalTopK 必须大于等于 1");
            }
            if (agent.reasoningBudget() < 0) {
                addError(errorsByKey, key, "reasoningBudget 不能小于 0");
            }
            if (agent.temperature() < 0.0D || agent.temperature() > 2.0D) {
                addError(errorsByKey, key, "temperature 必须位于 0.0 到 2.0 之间");
            }
            if (agent.published() && agent.status() != ProfileStatus.PUBLISHED) {
                addError(errorsByKey, key, "published=true 时 status 必须为 PUBLISHED");
            }
            if (agent.publicDebug() && agent.agentType() != AgentProfileVersionType.ENTRY) {
                addError(errorsByKey, key, "publicDebug=true 时 agentType 必须为 ENTRY");
            }
        }
    }

    private void validateResourceCode(ResourceKey key, String code, Map<ResourceKey, List<String>> errorsByKey) {
        if (!RESOURCE_CODE_PATTERN.matcher(code).matches()) {
            addError(errorsByKey, key, "code 仅支持小写字母、数字、.、_、-");
        }
    }

    private void validateOperations(
            ParsedConfigBundle bundle,
            Map<ConfigBundleResourceType, Map<String, ?>> existingSnapshots,
            Map<ResourceKey, PlannedOperation> operations,
            Map<ResourceKey, List<String>> errorsByKey
    ) {
        LinkedHashMap<String, ResolvedToolSnapshot> finalTools = new LinkedHashMap<>();
        castMap(existingSnapshots.get(ConfigBundleResourceType.TOOL), StoredToolSnapshot.class)
                .forEach((code, snapshot) -> finalTools.put(code, snapshot.toResolved()));
        for (IncomingToolSnapshot snapshot : bundle.tools()) {
            PlannedOperation operation = operations.get(new ResourceKey(ConfigBundleResourceType.TOOL, snapshot.code()));
            if (operation != null) {
                StoredToolSnapshot existing = castMap(existingSnapshots.get(ConfigBundleResourceType.TOOL), StoredToolSnapshot.class).get(snapshot.code());
                finalTools.put(snapshot.code(), snapshot.toResolved(existing));
                validateToolSnapshot(snapshot, errorsByKey, operation.key());
            }
        }

        LinkedHashMap<String, ResolvedMcpSnapshot> finalMcp = new LinkedHashMap<>();
        castMap(existingSnapshots.get(ConfigBundleResourceType.MCP), StoredMcpSnapshot.class)
                .forEach((code, snapshot) -> finalMcp.put(code, snapshot.toResolved()));
        for (IncomingMcpSnapshot snapshot : bundle.mcpServers()) {
            PlannedOperation operation = operations.get(new ResourceKey(ConfigBundleResourceType.MCP, snapshot.code()));
            if (operation != null) {
                StoredMcpSnapshot existing = castMap(existingSnapshots.get(ConfigBundleResourceType.MCP), StoredMcpSnapshot.class).get(snapshot.code());
                finalMcp.put(snapshot.code(), snapshot.toResolved(existing));
                validateMcpSnapshot(snapshot, finalTools, errorsByKey, operation.key());
            }
        }

        LinkedHashMap<String, ResolvedSkillSnapshot> finalSkills = new LinkedHashMap<>();
        castMap(existingSnapshots.get(ConfigBundleResourceType.SKILL), StoredSkillSnapshot.class)
                .forEach((code, snapshot) -> finalSkills.put(code, snapshot.toResolved()));
        for (IncomingSkillSnapshot snapshot : bundle.skills()) {
            PlannedOperation operation = operations.get(new ResourceKey(ConfigBundleResourceType.SKILL, snapshot.code()));
            if (operation != null) {
                StoredSkillSnapshot existing = castMap(existingSnapshots.get(ConfigBundleResourceType.SKILL), StoredSkillSnapshot.class).get(snapshot.code());
                finalSkills.put(snapshot.code(), snapshot.toResolved(existing));
                validateSkillSnapshot(bundle, snapshot, existing, operation.action(), errorsByKey, operation.key());
            }
        }

        LinkedHashMap<String, ResolvedAgentSnapshot> finalAgents = new LinkedHashMap<>();
        castMap(existingSnapshots.get(ConfigBundleResourceType.AGENT), StoredAgentSnapshot.class)
                .forEach((code, snapshot) -> finalAgents.put(code, snapshot.toResolved()));
        for (IncomingAgentSnapshot snapshot : bundle.agents()) {
            PlannedOperation operation = operations.get(new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode()));
            if (operation != null) {
                StoredAgentSnapshot existing = castMap(existingSnapshots.get(ConfigBundleResourceType.AGENT), StoredAgentSnapshot.class).get(snapshot.profileCode());
                finalAgents.put(snapshot.profileCode(), snapshot.toResolved(existing));
            }
        }

        validateNameUniqueness(finalTools.values(), ConfigBundleResourceType.TOOL, operations.keySet(), errorsByKey);
        validateNameUniqueness(finalSkills.values(), ConfigBundleResourceType.SKILL, operations.keySet(), errorsByKey);
        validateNameUniqueness(finalAgents.values(), ConfigBundleResourceType.AGENT, operations.keySet(), errorsByKey);
        validateMainConstraints(finalAgents, operations.keySet(), errorsByKey);
        for (IncomingAgentSnapshot snapshot : bundle.agents()) {
            PlannedOperation operation = operations.get(new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode()));
            if (operation == null) {
                continue;
            }
            validateAgentSnapshot(snapshot.toResolved(castMap(existingSnapshots.get(ConfigBundleResourceType.AGENT), StoredAgentSnapshot.class).get(snapshot.profileCode())), finalTools, finalMcp, finalSkills, finalAgents, errorsByKey, operation.key());
        }
    }

    private void validateToolSnapshot(IncomingToolSnapshot snapshot, Map<ResourceKey, List<String>> errorsByKey, ResourceKey key) {
        try {
            toolRuntimeFactoryService.validate(snapshot.className(), snapshot.beanName());
        } catch (Exception exception) {
            addError(errorsByKey, key, exception.getMessage());
        }
        normalizeJsonObject(snapshot.configJson(), key.resourceType() + ":" + key.resourceCode() + ".configJson");
    }

    private void validateMcpSnapshot(
            IncomingMcpSnapshot snapshot,
            Map<String, ResolvedToolSnapshot> finalTools,
            Map<ResourceKey, List<String>> errorsByKey,
            ResourceKey key
    ) {
        if (!"sse".equalsIgnoreCase(snapshot.transportType())) {
            addError(errorsByKey, key, "Only SSE transport is supported for MCP servers");
        }
        if (snapshot.timeoutMs() != null && snapshot.timeoutMs() <= 0) {
            addError(errorsByKey, key, "timeoutMs 必须大于 0");
        }
        if (snapshot.initializationTimeoutMs() != null && snapshot.initializationTimeoutMs() <= 0) {
            addError(errorsByKey, key, "initializationTimeoutMs 必须大于 0");
        }
        for (String toolCode : snapshot.enableTools()) {
            ResolvedToolSnapshot tool = finalTools.get(toolCode);
            if (tool == null || !tool.enabled()) {
                addError(errorsByKey, key, "enableTools 引用了不存在或未启用的 Tool: " + toolCode);
            }
        }
        for (String toolCode : snapshot.disableTools()) {
            ResolvedToolSnapshot tool = finalTools.get(toolCode);
            if (tool == null || !tool.enabled()) {
                addError(errorsByKey, key, "disableTools 引用了不存在或未启用的 Tool: " + toolCode);
            }
        }
    }

    private void validateSkillSnapshot(
            ParsedConfigBundle bundle,
            IncomingSkillSnapshot snapshot,
            StoredSkillSnapshot existing,
            AgentImportResolutionAction action,
            Map<ResourceKey, List<String>> errorsByKey,
            ResourceKey key
    ) {
        SkillPackageCandidate candidate = loadSkillPackage(bundle, snapshot);
        if (action == AgentImportResolutionAction.CREATE && candidate == null) {
            addError(errorsByKey, key, "Skill 新建时必须提供可读取的 packageLocation，或在默认 classpath 位置存在对应目录");
            return;
        }
        if (candidate == null) {
            if (existing == null || !StringUtils.hasText(existing.ossObjectKey()) || !StringUtils.hasText(existing.checksumMd5())) {
                addError(errorsByKey, key, "Skill 覆盖时未找到可用的本地包，且数据库中也没有现有包体可复用");
            }
            return;
        }
        if (StringUtils.hasText(snapshot.checksumMd5()) && !snapshot.checksumMd5().equalsIgnoreCase(candidate.checksumMd5())) {
            addError(errorsByKey, key, "Skill checksumMd5 与本地打包结果不一致");
            return;
        }
        try {
            SkillUtil.createFromZip(candidate.zipBytes(), "knowledge-box-config-bundle-validate");
        } catch (Exception exception) {
            addError(errorsByKey, key, "Skill 包解析失败: " + exception.getMessage());
        }
    }

    private void validateNameUniqueness(
            Collection<? extends NamedResolvedSnapshot> snapshots,
            ConfigBundleResourceType resourceType,
            Set<ResourceKey> importedKeys,
            Map<ResourceKey, List<String>> errorsByKey
    ) {
        LinkedHashMap<String, List<NamedResolvedSnapshot>> grouped = new LinkedHashMap<>();
        for (NamedResolvedSnapshot snapshot : snapshots) {
            if (!StringUtils.hasText(snapshot.displayName())) {
                continue;
            }
            grouped.computeIfAbsent(normalizeNameKey(snapshot.displayName()), ignored -> new ArrayList<>()).add(snapshot);
        }
        for (List<NamedResolvedSnapshot> sameNameSnapshots : grouped.values()) {
            if (sameNameSnapshots.size() <= 1) {
                continue;
            }
            String codes = sameNameSnapshots.stream().map(NamedResolvedSnapshot::resourceCode).collect(Collectors.joining(", "));
            for (NamedResolvedSnapshot snapshot : sameNameSnapshots) {
                ResourceKey key = new ResourceKey(resourceType, snapshot.resourceCode());
                if (importedKeys.contains(key)) {
                    addError(errorsByKey, key, "名称与其他配置重复: " + codes);
                }
            }
        }
    }

    private void validateMainConstraints(
            Map<String, ResolvedAgentSnapshot> finalSnapshots,
            Set<ResourceKey> importedKeys,
            Map<ResourceKey, List<String>> errorsByKey
    ) {
        List<ResolvedAgentSnapshot> mainAgents = finalSnapshots.values().stream()
                .filter(snapshot -> snapshot.agentType() == AgentProfileVersionType.MAIN)
                .toList();
        if (mainAgents.size() != 1) {
            for (ResolvedAgentSnapshot snapshot : mainAgents) {
                ResourceKey key = new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode());
                if (importedKeys.contains(key)) {
                    addError(errorsByKey, key, "最终结果必须且只能保留一个 MAIN Agent");
                }
            }
            if (mainAgents.isEmpty()) {
                importedKeys.stream().filter(key -> key.resourceType() == ConfigBundleResourceType.AGENT)
                        .forEach(key -> addError(errorsByKey, key, "导入完成后必须保留一个 MAIN Agent"));
            }
        }
        List<ResolvedAgentSnapshot> publishedAgents = finalSnapshots.values().stream()
                .filter(ResolvedAgentSnapshot::published)
                .toList();
        if (publishedAgents.size() != 1 || publishedAgents.stream().anyMatch(snapshot -> snapshot.agentType() != AgentProfileVersionType.MAIN)) {
            for (ResolvedAgentSnapshot snapshot : publishedAgents) {
                ResourceKey key = new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode());
                if (importedKeys.contains(key)) {
                    addError(errorsByKey, key, "最终结果必须且只能保留一个已发布的 MAIN Agent");
                }
            }
            if (publishedAgents.isEmpty()) {
                importedKeys.stream().filter(key -> key.resourceType() == ConfigBundleResourceType.AGENT)
                        .forEach(key -> addError(errorsByKey, key, "导入完成后必须保留一个已发布的 MAIN Agent"));
            }
        }
    }

    private void validateAgentSnapshot(
            ResolvedAgentSnapshot snapshot,
            Map<String, ResolvedToolSnapshot> finalTools,
            Map<String, ResolvedMcpSnapshot> finalMcp,
            Map<String, ResolvedSkillSnapshot> finalSkills,
            Map<String, ResolvedAgentSnapshot> finalAgents,
            Map<ResourceKey, List<String>> errorsByKey,
            ResourceKey key
    ) {
        Set<String> configuredEnvKeys = snapshot.envVars().stream()
                .map(AgentRuntimeEnvVarView::key)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (AgentRuntimeEnvVarView envVar : snapshot.envVars()) {
            if (envVar.valueSource() == com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource.PROCESS_ENV
                    && !StringUtils.hasText(envVar.sourceRef())) {
                addError(errorsByKey, key, "PROCESS_ENV env var 缺少 sourceRef: " + envVar.key());
            }
            if (envVar.valueSource() == com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource.INLINE
                    && !StringUtils.hasText(envVar.value())
                    && !envVar.hasValue()) {
                addError(errorsByKey, key, "INLINE env var 缺少 value: " + envVar.key());
            }
        }
        validateEnabledModel(snapshot.chatModel(), ModelType.CHAT, key, errorsByKey, "chatModel");
        validateEnabledModel(snapshot.embeddingModel(), ModelType.EMBEDDING, key, errorsByKey, "embeddingModel");
        if (StringUtils.hasText(snapshot.rerankModel())) {
            validateEnabledModel(snapshot.rerankModel(), ModelType.RERANK, key, errorsByKey, "rerankModel");
        }
        if (snapshot.published() && snapshot.agentType() != AgentProfileVersionType.MAIN) {
            addError(errorsByKey, key, "仅 MAIN Agent 可以设置为 published=true");
        }
        if (snapshot.publicDebug() && snapshot.agentType() != AgentProfileVersionType.ENTRY) {
            addError(errorsByKey, key, "仅 ENTRY Agent 可以设置为 publicDebug=true");
        }
        for (String toolCode : snapshot.toolCodes()) {
            ResolvedToolSnapshot tool = finalTools.get(toolCode);
            if (tool == null || !tool.enabled()) {
                addError(errorsByKey, key, "Tool 不存在或未启用: " + toolCode);
                continue;
            }
            for (RuntimeEnvRequirementView requirement : tool.runtimeEnvRequirements()) {
                if (requirement.required() && !configuredEnvKeys.contains(requirement.key())) {
                    addError(errorsByKey, key, "缺少 Tool 运行时环境变量: " + requirement.key() + " (tool=" + toolCode + ")");
                }
            }
        }
        for (String skillCode : snapshot.skillCodes()) {
            ResolvedSkillSnapshot skill = finalSkills.get(skillCode);
            if (skill == null || !skill.enabled()) {
                addError(errorsByKey, key, "Skill 不存在或未启用: " + skillCode);
                continue;
            }
            for (RuntimeEnvRequirementView requirement : skill.runtimeEnvRequirements()) {
                if (requirement.required() && !configuredEnvKeys.contains(requirement.key())) {
                    addError(errorsByKey, key, "缺少 Skill 运行时环境变量: " + requirement.key() + " (skill=" + skillCode + ")");
                }
            }
        }
        for (AgentConfigMcpBindingView mcpBinding : snapshot.mcpBindings()) {
            ResolvedMcpSnapshot config = finalMcp.get(mcpBinding.mcpCode());
            if (config == null || !config.enabled()) {
                addError(errorsByKey, key, "MCP 服务不存在或未启用: " + mcpBinding.mcpCode());
                continue;
            }
            for (RuntimeEnvRequirementView requirement : config.runtimeEnvRequirements()) {
                if (requirement.required() && !configuredEnvKeys.contains(requirement.key())) {
                    addError(errorsByKey, key, "缺少 MCP 运行时环境变量: " + requirement.key() + " (mcp=" + mcpBinding.mcpCode() + ")");
                }
            }
            for (String toolCode : mcpBinding.enableTools()) {
                ResolvedToolSnapshot tool = finalTools.get(toolCode);
                if (tool == null || !tool.enabled()) {
                    addError(errorsByKey, key, "MCP enableTools 引用了不存在或未启用的 Tool: " + toolCode);
                }
            }
            for (String toolCode : mcpBinding.disableTools()) {
                ResolvedToolSnapshot tool = finalTools.get(toolCode);
                if (tool == null || !tool.enabled()) {
                    addError(errorsByKey, key, "MCP disableTools 引用了不存在或未启用的 Tool: " + toolCode);
                }
            }
        }
        if (!snapshot.childAgentProfileCodes().isEmpty()
                && snapshot.agentType() != AgentProfileVersionType.MAIN
                && snapshot.agentType() != AgentProfileVersionType.ENTRY
                && snapshot.agentType() != AgentProfileVersionType.ORCHESTRATOR) {
            addError(errorsByKey, key, "仅 MAIN / ENTRY / ORCHESTRATOR 可以绑定子 Agent");
        }
        for (String childProfileCode : snapshot.childAgentProfileCodes()) {
            if (Objects.equals(childProfileCode, snapshot.profileCode())) {
                addError(errorsByKey, key, "Agent 不能绑定自己作为子 Agent");
                continue;
            }
            ResolvedAgentSnapshot child = finalAgents.get(childProfileCode);
            if (child == null) {
                addError(errorsByKey, key, "子 Agent 不存在: " + childProfileCode);
                continue;
            }
            if (child.agentType() != AgentProfileVersionType.ATOMIC) {
                addError(errorsByKey, key, "子 Agent 必须为 ATOMIC: " + childProfileCode);
            }
        }
    }

    private void validateEnabledModel(
            String code,
            ModelType modelType,
            ResourceKey key,
            Map<ResourceKey, List<String>> errorsByKey,
            String fieldName
    ) {
        ModelCatalog modelCatalog = modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue(code, modelType).orElse(null);
        if (modelCatalog == null) {
            addError(errorsByKey, key, fieldName + " 未找到已启用的 " + modelType + " 模型: " + code);
        }
    }

    private void addError(Map<ResourceKey, List<String>> errorsByKey, ResourceKey key, String message) {
        errorsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
        List<String> messages = errorsByKey.get(key);
        if (!messages.contains(message)) {
            messages.add(message);
        }
    }

    private AgentImportResolutionAction resolveAction(PreviewItem item, AgentImportResolutionAction requestedAction) {
        if (requestedAction == null) {
            return item.defaultAction();
        }
        if (!item.availableActions().contains(requestedAction)) {
            throw new IllegalArgumentException("Invalid action %s for %s:%s".formatted(requestedAction, item.key().resourceType(), item.key().resourceCode()));
        }
        return requestedAction;
    }

    private Map<ResourceKey, AgentImportResolutionAction> normalizeDecisionMap(List<ConfigBundleImportDecisionRequest> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<ResourceKey, AgentImportResolutionAction> result = new LinkedHashMap<>();
        for (ConfigBundleImportDecisionRequest decision : decisions) {
            if (decision == null || decision.resourceType() == null || !StringUtils.hasText(decision.resourceCode()) || decision.action() == null) {
                continue;
            }
            result.put(new ResourceKey(decision.resourceType(), normalizeResourceCode(decision.resourceCode())), decision.action());
        }
        return result;
    }

    private AppliedExecution applyExecution(PlannedExecution execution) {
        if (execution.operations().isEmpty()) {
            return new AppliedExecution(0, 0, Math.max(0, execution.skippedCount()), execution.globalMessages());
        }
        int created = 0;
        int overwritten = 0;
        Map<String, StoredToolSnapshot> toolSnapshots = loadCurrentToolSnapshots();
        Map<String, StoredMcpSnapshot> mcpSnapshots = loadCurrentMcpSnapshots();
        Map<String, StoredSkillSnapshot> skillSnapshots = loadCurrentSkillSnapshots();
        Map<String, StoredAgentSnapshot> agentSnapshots = loadCurrentAgentSnapshots();

        for (IncomingToolSnapshot snapshot : execution.bundle().tools()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.TOOL, snapshot.code());
            PlannedOperation operation = execution.operations().get(key);
            if (operation == null) {
                continue;
            }
            StoredToolSnapshot existing = toolSnapshots.get(snapshot.code());
            toolSnapshots.put(snapshot.code(), persistTool(snapshot, existing));
            if (existing == null) {
                created++;
            } else {
                overwritten++;
            }
        }
        for (IncomingMcpSnapshot snapshot : execution.bundle().mcpServers()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.MCP, snapshot.code());
            PlannedOperation operation = execution.operations().get(key);
            if (operation == null) {
                continue;
            }
            StoredMcpSnapshot existing = mcpSnapshots.get(snapshot.code());
            mcpSnapshots.put(snapshot.code(), persistMcp(snapshot, existing));
            if (existing == null) {
                created++;
            } else {
                overwritten++;
            }
        }
        for (IncomingSkillSnapshot snapshot : execution.bundle().skills()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.SKILL, snapshot.code());
            PlannedOperation operation = execution.operations().get(key);
            if (operation == null) {
                continue;
            }
            StoredSkillSnapshot existing = skillSnapshots.get(snapshot.code());
            skillSnapshots.put(snapshot.code(), persistSkill(execution.bundle(), snapshot, existing, operation.action()));
            if (existing == null) {
                created++;
            } else {
                overwritten++;
            }
        }
        for (IncomingAgentSnapshot snapshot : execution.bundle().agents()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode());
            PlannedOperation operation = execution.operations().get(key);
            if (operation == null) {
                continue;
            }
            StoredAgentSnapshot existing = agentSnapshots.get(snapshot.profileCode());
            agentSnapshots.put(snapshot.profileCode(), persistAgent(snapshot, existing));
            if (existing == null) {
                created++;
            } else {
                overwritten++;
            }
        }
        for (IncomingAgentSnapshot snapshot : execution.bundle().agents()) {
            ResourceKey key = new ResourceKey(ConfigBundleResourceType.AGENT, snapshot.profileCode());
            PlannedOperation operation = execution.operations().get(key);
            if (operation == null) {
                continue;
            }
            StoredAgentSnapshot persisted = agentSnapshots.get(snapshot.profileCode());
            if (persisted == null) {
                continue;
            }
            List<Long> childVersionIds = snapshot.childAgentProfileCodes().stream()
                    .map(agentSnapshots::get)
                    .filter(Objects::nonNull)
                    .map(StoredAgentSnapshot::versionId)
                    .toList();
            agentProfileBindingService.updateBindings(
                    persisted.versionId(),
                    new UpdateAgentProfileVersionBindingsRequest(
                            snapshot.toolCodes(),
                            snapshot.skillCodes(),
                            toProfileVersionMcpBindings(snapshot.mcpBindings()),
                            childVersionIds,
                            snapshot.envVars()
                    )
            );
        }

        return new AppliedExecution(created, overwritten, Math.max(0, execution.skippedCount()), execution.globalMessages());
    }

    private StoredToolSnapshot persistTool(IncomingToolSnapshot snapshot, StoredToolSnapshot existing) {
        ToolDefinition definition = existing == null
                ? new ToolDefinition()
                : toolDefinitionRepository.findById(existing.id()).orElseThrow(() -> new IllegalArgumentException("Tool not found: " + existing.id()));
        definition.setCode(snapshot.code());
        definition.setName(snapshot.name());
        definition.setClassName(snapshot.className());
        definition.setBeanName(snapshot.beanName());
        definition.setConfigJson(snapshot.configJson());
        definition.setRuntimeEnvRequirementsJson(writeJson(snapshot.runtimeEnvRequirements()));
        definition.setEnabled(snapshot.enabled());
        definition.setInputSchema("{}");
        definition.setEndpoint("classpath://" + snapshot.className());
        definition = toolDefinitionRepository.save(definition);
        return new StoredToolSnapshot(
                definition.getId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.className(),
                snapshot.beanName(),
                snapshot.configJson(),
                snapshot.runtimeEnvRequirements(),
                snapshot.enabled()
        );
    }

    private StoredMcpSnapshot persistMcp(IncomingMcpSnapshot snapshot, StoredMcpSnapshot existing) {
        McpServerConfig config = existing == null
                ? new McpServerConfig()
                : mcpServerConfigRepository.findById(existing.id()).orElseThrow(() -> new IllegalArgumentException("MCP not found: " + existing.id()));
        config.setCode(snapshot.code());
        config.setTransportType(snapshot.transportType());
        config.setTarget(snapshot.target());
        config.setCapabilitiesJson("[]");
        config.setHeadersEncryptedJson(writeJson(encryptSecretMap(snapshot.headers())));
        config.setQueryParamsJson(writeJson(snapshot.queryParams()));
        config.setRuntimeEnvRequirementsJson(writeJson(snapshot.runtimeEnvRequirements()));
        config.setTimeoutMs(snapshot.timeoutMs());
        config.setInitializationTimeoutMs(snapshot.initializationTimeoutMs());
        config.setEnabled(snapshot.enabled());
        config = mcpServerConfigRepository.save(config);
        return new StoredMcpSnapshot(
                config.getId(),
                snapshot.code(),
                snapshot.transportType(),
                snapshot.target(),
                snapshot.headers(),
                snapshot.queryParams(),
                snapshot.runtimeEnvRequirements(),
                snapshot.timeoutMs(),
                snapshot.initializationTimeoutMs(),
                snapshot.enabled()
        );
    }

    private StoredSkillSnapshot persistSkill(
            ParsedConfigBundle bundle,
            IncomingSkillSnapshot snapshot,
            StoredSkillSnapshot existing,
            AgentImportResolutionAction action
    ) {
        SkillBinding binding = existing == null
                ? new SkillBinding()
                : skillBindingRepository.findById(existing.id()).orElseThrow(() -> new IllegalArgumentException("Skill not found: " + existing.id()));
        SkillPackageCandidate candidate = loadSkillPackage(bundle, snapshot);
        String objectKey = existing == null ? null : existing.ossObjectKey();
        String checksumMd5 = existing == null ? null : existing.checksumMd5();
        String promptTemplate = binding.getPromptTemplate();
        String sourceType = StringUtils.hasText(snapshot.sourceType()) ? snapshot.sourceType() : (existing == null ? "UPLOAD" : existing.sourceType());
        if (candidate != null) {
            AgentSkill parsedSkill = SkillUtil.createFromZip(candidate.zipBytes(), "knowledge-box-config-bundle-commit");
            SkillPackageStorageService.StoredSkillPackage stored = skillPackageStorageService.store(snapshot.code(), candidate.zipBytes());
            objectKey = stored.objectKey();
            checksumMd5 = stored.checksumMd5();
            promptTemplate = parsedSkill.getSkillContent();
            sourceType = StringUtils.hasText(snapshot.sourceType()) ? snapshot.sourceType() : "UPLOAD";
        } else if (action == AgentImportResolutionAction.CREATE) {
            throw new IllegalArgumentException("Skill 新建时必须提供可读取的 packageLocation");
        }
        binding.setCode(snapshot.code());
        binding.setName(snapshot.name());
        binding.setDescription(snapshot.description());
        binding.setEnabled(snapshot.enabled());
        binding.setSourceType(sourceType);
        binding.setOssObjectKey(objectKey);
        binding.setChecksumMd5(checksumMd5);
        binding.setPromptTemplate(promptTemplate);
        binding.setRuntimeEnvRequirementsJson(writeJson(snapshot.runtimeEnvRequirements()));
        binding = skillBindingRepository.save(binding);
        return new StoredSkillSnapshot(
                binding.getId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.description(),
                binding.getSourceType(),
                binding.getChecksumMd5(),
                binding.getOssObjectKey(),
                defaultSkillPackageLocation(snapshot.code()),
                snapshot.runtimeEnvRequirements(),
                snapshot.enabled()
        );
    }

    private StoredAgentSnapshot persistAgent(IncomingAgentSnapshot snapshot, StoredAgentSnapshot existing) {
        AgentProfile profile = existing == null
                ? new AgentProfile()
                : agentProfileRepository.findById(existing.profileId()).orElseThrow(() -> new IllegalArgumentException("Agent profile not found: " + existing.profileId()));
        profile.setCode(snapshot.profileCode());
        profile.setName(snapshot.profileName());
        profile.setDescription(snapshot.description());
        profile = agentProfileRepository.save(profile);

        AgentProfileVersion version = existing == null
                ? new AgentProfileVersion()
                : agentProfileVersionRepository.findById(existing.versionId()).orElseThrow(() -> new IllegalArgumentException("Agent version not found: " + existing.versionId()));
        version.setProfile(profile);
        if (existing == null) {
            version.setVersionNumber(1);
        }
        version.setStatus(snapshot.status());
        version.setPublished(snapshot.published());
        version.setPublicDebug(snapshot.publicDebug());
        version.setAgentType(policyService.normalizeType(snapshot.agentType()));
        version.setChatModel(snapshot.chatModel());
        version.setRoutingModel(snapshot.chatModel());
        version.setEmbeddingModel(snapshot.embeddingModel());
        version.setRerankModel(snapshot.rerankModel());
        version.setTemperature(snapshot.temperature());
        version.setRetrievalTopK(snapshot.retrievalTopK());
        version.setReasoningBudget(snapshot.reasoningBudget());
        version.setSystemPrompt(snapshot.systemPrompt());
        version.setToolBindings("[]");
        version.setMcpBindings("[]");
        version.setSkillBindings("[]");
        version = agentProfileVersionRepository.save(version);
        return new StoredAgentSnapshot(
                profile.getId(),
                version.getId(),
                version.getVersionNumber(),
                snapshot.profileCode(),
                snapshot.profileName(),
                snapshot.description(),
                snapshot.agentType(),
                snapshot.status(),
                snapshot.published(),
                snapshot.publicDebug(),
                snapshot.chatModel(),
                snapshot.routingModel(),
                snapshot.embeddingModel(),
                snapshot.rerankModel(),
                snapshot.temperature(),
                snapshot.retrievalTopK(),
                snapshot.reasoningBudget(),
                snapshot.systemPrompt(),
                snapshot.toolCodes(),
                snapshot.skillCodes(),
                snapshot.mcpBindings(),
                snapshot.childAgentProfileCodes(),
                snapshot.envVars()
        );
    }

    private SkillPackageCandidate loadSkillPackage(ParsedConfigBundle bundle, IncomingSkillSnapshot snapshot) {
        String location = snapshot.packageLocation();
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String trimmed = location.trim();
        try {
            if (trimmed.endsWith(".zip")) {
                Resource resource = resolveResource(bundle, trimmed);
                if (!resource.exists()) {
                    return null;
                }
                byte[] bytes;
                try (InputStream inputStream = resource.getInputStream()) {
                    bytes = inputStream.readAllBytes();
                }
                return new SkillPackageCandidate(bytes, hexMd5(bytes));
            }
            if (trimmed.startsWith("classpath:")) {
                return loadSkillPackageFromClasspathDir(trimmed);
            }
            Resource resource = resolveResource(bundle, trimmed);
            if (resource.exists()) {
                try {
                    Path path = resource.getFile().toPath();
                    if (Files.isDirectory(path)) {
                        return loadSkillPackageFromDirectory(path);
                    }
                    try (InputStream inputStream = resource.getInputStream()) {
                        byte[] bytes = inputStream.readAllBytes();
                        return new SkillPackageCandidate(bytes, hexMd5(bytes));
                    }
                } catch (IOException ignore) {
                    // fall through to path-based lookup
                }
            }
            Path path = resolveFilePath(bundle, trimmed);
            if (path == null || !Files.exists(path)) {
                return null;
            }
            if (Files.isDirectory(path)) {
                return loadSkillPackageFromDirectory(path);
            }
            byte[] bytes = Files.readAllBytes(path);
            return new SkillPackageCandidate(bytes, hexMd5(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read skill package from " + location, exception);
        }
    }

    private SkillPackageCandidate loadSkillPackageFromDirectory(Path directory) throws IOException {
        String rootName = directory.getFileName() == null ? "skill" : directory.getFileName().toString();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
             var walk = Files.walk(directory)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                zipOutputStream.putNextEntry(new ZipEntry(rootName + "/" + relative));
                Files.copy(file, zipOutputStream);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            byte[] bytes = outputStream.toByteArray();
            return new SkillPackageCandidate(bytes, hexMd5(bytes));
        }
    }

    private SkillPackageCandidate loadSkillPackageFromClasspathDir(String location) throws IOException {
        String base = location.substring("classpath:".length()).replaceAll("^/", "").replaceAll("/+$", "");
        Resource[] resources = resourcePatternResolver.getResources("classpath*:" + base + "/**");
        if (resources == null || resources.length == 0) {
            return null;
        }
        String rootName = base.substring(base.lastIndexOf('/') + 1);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            int written = 0;
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                String relative = relativePathFromClasspathResource(base, resource);
                if (!StringUtils.hasText(relative)) {
                    continue;
                }
                zipOutputStream.putNextEntry(new ZipEntry(rootName + "/" + relative));
                try (InputStream inputStream = resource.getInputStream()) {
                    inputStream.transferTo(zipOutputStream);
                }
                zipOutputStream.closeEntry();
                written++;
            }
            if (written == 0) {
                return null;
            }
            zipOutputStream.finish();
            byte[] bytes = outputStream.toByteArray();
            return new SkillPackageCandidate(bytes, hexMd5(bytes));
        }
    }

    private String relativePathFromClasspathResource(String base, Resource resource) throws IOException {
        String url = resource.getURL().toString().replace('\\', '/');
        String marker = "/" + base + "/";
        int index = url.indexOf(marker);
        if (index < 0) {
            marker = base + "/";
            index = url.indexOf(marker);
        }
        if (index < 0) {
            return null;
        }
        String relative = url.substring(index + marker.length());
        if (!StringUtils.hasText(relative) || relative.endsWith("/")) {
            return null;
        }
        return relative;
    }

    private Resource resolveResource(ParsedConfigBundle bundle, String location) {
        String trimmed = location.trim();
        if (trimmed.startsWith("classpath:") || trimmed.startsWith("file:")) {
            return resourceLoader.getResource(trimmed);
        }
        Path path = resolveFilePath(bundle, trimmed);
        if (path != null) {
            return resourceLoader.getResource("file:" + path.toAbsolutePath().normalize());
        }
        return resourceLoader.getResource(trimmed);
    }

    private Path resolveFilePath(ParsedConfigBundle bundle, String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        if (location.startsWith("classpath:")) {
            return null;
        }
        if (location.startsWith("file:")) {
            return Path.of(location.substring("file:".length())).toAbsolutePath().normalize();
        }
        Path raw = Path.of(location);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (bundle.sourceBasePath() != null) {
            return bundle.sourceBasePath().resolve(raw).normalize();
        }
        return Path.of("").toAbsolutePath().resolve(raw).normalize();
    }

    private String hexMd5(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute md5", exception);
        }
    }

    private List<AgentProfileVersionMcpBindingView> toProfileVersionMcpBindings(List<AgentConfigMcpBindingView> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> new AgentProfileVersionMcpBindingView(binding.mcpCode(), binding.enableTools(), binding.disableTools()))
                .toList();
    }

    private Map<ConfigBundleResourceType, Map<String, ?>> loadExistingSnapshotMaps() {
        LinkedHashMap<ConfigBundleResourceType, Map<String, ?>> snapshots = new LinkedHashMap<>();
        snapshots.put(ConfigBundleResourceType.TOOL, loadCurrentToolSnapshots());
        snapshots.put(ConfigBundleResourceType.MCP, loadCurrentMcpSnapshots());
        snapshots.put(ConfigBundleResourceType.SKILL, loadCurrentSkillSnapshots());
        snapshots.put(ConfigBundleResourceType.AGENT, loadCurrentAgentSnapshots());
        return snapshots;
    }

    private Map<String, StoredToolSnapshot> loadCurrentToolSnapshots() {
        LinkedHashMap<String, StoredToolSnapshot> snapshots = new LinkedHashMap<>();
        for (ToolDefinition definition : toolDefinitionRepository.findAll()) {
            snapshots.put(definition.getCode(), new StoredToolSnapshot(
                    definition.getId(),
                    definition.getCode(),
                    definition.getName(),
                    definition.getClassName(),
                    definition.getBeanName(),
                    normalizeJsonObject(definition.getConfigJson(), "tool.configJson"),
                    readRuntimeEnvRequirements(definition.getRuntimeEnvRequirementsJson()),
                    Boolean.TRUE.equals(definition.getEnabled())
            ));
        }
        return snapshots;
    }

    private Map<String, StoredMcpSnapshot> loadCurrentMcpSnapshots() {
        LinkedHashMap<String, StoredMcpSnapshot> snapshots = new LinkedHashMap<>();
        for (McpServerConfig config : mcpServerConfigRepository.findAll()) {
            snapshots.put(config.getCode(), new StoredMcpSnapshot(
                    config.getId(),
                    config.getCode(),
                    config.getTransportType(),
                    config.getTarget(),
                    decryptHeaderMap(config.getHeadersEncryptedJson()),
                    readStringMap(config.getQueryParamsJson()),
                    readRuntimeEnvRequirements(config.getRuntimeEnvRequirementsJson()),
                    config.getTimeoutMs(),
                    config.getInitializationTimeoutMs(),
                    Boolean.TRUE.equals(config.getEnabled())
            ));
        }
        return snapshots;
    }

    private Map<String, StoredSkillSnapshot> loadCurrentSkillSnapshots() {
        LinkedHashMap<String, StoredSkillSnapshot> snapshots = new LinkedHashMap<>();
        for (SkillBinding binding : skillBindingRepository.findAll()) {
            snapshots.put(binding.getCode(), new StoredSkillSnapshot(
                    binding.getId(),
                    binding.getCode(),
                    binding.getName(),
                    binding.getDescription(),
                    binding.getSourceType(),
                    binding.getChecksumMd5(),
                    binding.getOssObjectKey(),
                    defaultSkillPackageLocation(binding.getCode()),
                    readRuntimeEnvRequirements(binding.getRuntimeEnvRequirementsJson()),
                    Boolean.TRUE.equals(binding.getEnabled())
            ));
        }
        return snapshots;
    }

    private Map<String, StoredAgentSnapshot> loadCurrentAgentSnapshots() {
        List<AgentProfileVersion> versions = agentProfileVersionRepository.findAllForAdmin();
        LinkedHashMap<String, AgentProfileVersion> latestByCode = new LinkedHashMap<>();
        for (AgentProfileVersion version : versions) {
            latestByCode.putIfAbsent(version.getProfile().getCode(), version);
        }
        LinkedHashMap<String, StoredAgentSnapshot> snapshots = new LinkedHashMap<>();
        for (AgentProfileVersion version : latestByCode.values()) {
            var bindings = agentProfileBindingService.bindings(version.getId());
            snapshots.put(version.getProfile().getCode(), new StoredAgentSnapshot(
                    version.getProfile().getId(),
                    version.getId(),
                    version.getVersionNumber(),
                    version.getProfile().getCode(),
                    version.getProfile().getName(),
                    version.getProfile().getDescription(),
                    policyService.normalizeType(version.getAgentType()),
                    version.getStatus(),
                    Boolean.TRUE.equals(version.getPublished()),
                    Boolean.TRUE.equals(version.getPublicDebug()),
                    version.getChatModel(),
                    version.getRoutingModel(),
                    version.getEmbeddingModel(),
                    version.getRerankModel(),
                    version.getTemperature(),
                    version.getRetrievalTopK(),
                    version.getReasoningBudget(),
                    version.getSystemPrompt(),
                    bindings.toolCodes(),
                    bindings.skillCodes(),
                    bindings.mcpBindings().stream()
                            .map(binding -> new AgentConfigMcpBindingView(binding.mcpCode(), binding.enableTools(), binding.disableTools()))
                            .toList(),
                    bindings.childAgentBindings().stream().map(child -> child.profileCode()).toList(),
                    bindings.envVars()
            ));
        }
        return snapshots;
    }

    private ConfigBundleToolView toToolView(ToolDefinition definition) {
        return new ConfigBundleToolView(
                definition.getCode(),
                definition.getName(),
                definition.getClassName(),
                definition.getBeanName(),
                normalizeJsonObject(definition.getConfigJson(), "tool.configJson"),
                readRuntimeEnvRequirements(definition.getRuntimeEnvRequirementsJson()),
                Boolean.TRUE.equals(definition.getEnabled())
        );
    }

    private ConfigBundleMcpServerView toMcpView(McpServerConfig config) {
        return new ConfigBundleMcpServerView(
                config.getCode(),
                config.getTransportType(),
                config.getTarget(),
                decryptHeaderMap(config.getHeadersEncryptedJson()),
                readStringMap(config.getQueryParamsJson()),
                readRuntimeEnvRequirements(config.getRuntimeEnvRequirementsJson()),
                config.getTimeoutMs(),
                config.getInitializationTimeoutMs(),
                Boolean.TRUE.equals(config.getEnabled())
        );
    }

    private ConfigBundleSkillView toSkillView(SkillBinding binding) {
        return new ConfigBundleSkillView(
                binding.getCode(),
                binding.getName(),
                binding.getDescription(),
                binding.getSourceType(),
                binding.getChecksumMd5(),
                binding.getOssObjectKey(),
                defaultSkillPackageLocation(binding.getCode()),
                readRuntimeEnvRequirements(binding.getRuntimeEnvRequirementsJson()),
                Boolean.TRUE.equals(binding.getEnabled())
        );
    }

    private Map<String, String> encryptSecretMap(Map<String, String> plainSecrets) {
        Map<String, String> encrypted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : plainSecrets.entrySet()) {
            encrypted.put(entry.getKey(), secretCipherService.encrypt(entry.getValue()));
        }
        return encrypted;
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
            return objectMapper.readValue(json, STRING_MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<RuntimeEnvRequirementView> readRuntimeEnvRequirements(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return normalizeRuntimeEnvRequirements(objectMapper.readTree(json));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, String> normalizeStringMap(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue().isNull()) {
                return;
            }
            String value = entry.getValue().asText("").trim();
            if (!StringUtils.hasText(value)) {
                return;
            }
            result.put(entry.getKey().trim(), value);
        });
        return Map.copyOf(result);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize json", exception);
        }
    }

    private String normalizeTransportType(String transportType) {
        if (!StringUtils.hasText(transportType)) {
            throw new IllegalArgumentException("transportType is required");
        }
        return transportType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProfileCode(String profileCode) {
        if (!StringUtils.hasText(profileCode)) {
            throw new IllegalArgumentException("profileCode is required");
        }
        return profileCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeResourceCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code is required");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNameKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeJsonObject(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return "{}";
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid JSON object");
        }
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        String value = readOptionalText(node, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing field `" + fieldName + "`");
        }
        return value;
    }

    private String readOptionalText(JsonNode node, String defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText(defaultValue);
        return value == null ? defaultValue : value;
    }

    private boolean readBoolean(JsonNode node, boolean defaultValue) {
        return node == null || node.isNull() ? defaultValue : node.asBoolean(defaultValue);
    }

    private double readDouble(JsonNode node, double defaultValue) {
        return node == null || node.isNull() ? defaultValue : node.asDouble(defaultValue);
    }

    private int readInt(JsonNode node, int defaultValue) {
        return node == null || node.isNull() ? defaultValue : node.asInt(defaultValue);
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private <E extends Enum<E>> E readEnum(JsonNode node, Class<E> enumType, String fieldName) {
        String text = readRequiredText(node, fieldName).trim();
        try {
            return Enum.valueOf(enumType, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value `" + text + "` for field `" + fieldName + "`");
        }
    }

    private <E extends Enum<E>> E readEnumOrDefault(JsonNode node, Class<E> enumType, E defaultValue) {
        String text = readOptionalText(node, null);
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, text.trim().toUpperCase(Locale.ROOT));
    }

    private List<String> normalizeCodeList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected array of strings");
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = normalizeOptionalText(readOptionalText(item, ""));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            codes.add(normalizeResourceCode(value));
        }
        return List.copyOf(codes);
    }

    private List<AgentConfigMcpBindingView> normalizeMcpBindings(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`mcpBindings` must be an array");
        }
        LinkedHashMap<String, AgentConfigMcpBindingView> bindings = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String mcpCode = normalizeOptionalText(readOptionalText(item.get("mcpCode"), ""));
            if (!StringUtils.hasText(mcpCode)) {
                continue;
            }
            String normalizedCode = normalizeResourceCode(mcpCode);
            bindings.put(normalizedCode, new AgentConfigMcpBindingView(
                    normalizedCode,
                    normalizeCodeList(item.get("enableTools")),
                    normalizeCodeList(item.get("disableTools"))
            ));
        }
        return List.copyOf(bindings.values());
    }

    private List<RuntimeEnvRequirementView> normalizeRuntimeEnvRequirements(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`runtimeEnvRequirements` must be an array");
        }
        LinkedHashMap<String, RuntimeEnvRequirementView> requirements = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String key = normalizeOptionalText(readOptionalText(item.get("key"), ""));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalizedKey = key.trim().toUpperCase(Locale.ROOT);
            requirements.putIfAbsent(normalizedKey, new RuntimeEnvRequirementView(
                    normalizedKey,
                    readBoolean(item.get("required"), false),
                    readBoolean(item.get("secret"), true),
                    normalizeOptionalText(readOptionalText(item.get("description"), null))
            ));
        }
        return List.copyOf(requirements.values());
    }

    private List<AgentRuntimeEnvVarView> normalizeAgentEnvVars(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("`envVars` must be an array");
        }
        LinkedHashMap<String, AgentRuntimeEnvVarView> envVars = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String key = normalizeOptionalText(readOptionalText(item.get("key"), ""));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalizedKey = key.trim().toUpperCase(Locale.ROOT);
            envVars.putIfAbsent(normalizedKey, new AgentRuntimeEnvVarView(
                    normalizedKey,
                    normalizeOptionalText(readOptionalText(item.get("description"), null)),
                    readBoolean(item.get("secret"), true),
                    readEnumOrDefault(
                            item.get("valueSource"),
                            com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource.class,
                            com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource.INLINE
                    ),
                    normalizeOptionalText(readOptionalText(item.get("sourceRef"), null)),
                    normalizeOptionalText(readOptionalText(item.get("value"), null)),
                    readBoolean(item.get("hasValue"), false)
            ));
        }
        return List.copyOf(envVars.values());
    }

    private String defaultSkillPackageLocation(String code) {
        return "classpath:bootstrap/skills/" + normalizeResourceCode(code);
    }

    private interface ImportableSnapshot<T, S> {
        String resourceCode();

        String displayName();

        JsonNode previewNode(ObjectMapper objectMapper);

        Object resolvedValue(S existing);
    }

    private interface ExistingSnapshot<T> {
        String resourceCode();

        String displayName();

        JsonNode previewNode(ObjectMapper objectMapper);
    }

    private interface NamedResolvedSnapshot {
        String resourceCode();

        String displayName();
    }

    private record ResourceKey(ConfigBundleResourceType resourceType, String resourceCode) {
    }

    private record StoredPreview(ParsedConfigBundle bundle, OffsetDateTime createdAt) {
    }

    private record ParsedConfigBundle(
            String schemaVersion,
            String sourceDescription,
            Path sourceBasePath,
            List<IncomingToolSnapshot> tools,
            Map<String, Integer> toolCodeCounts,
            Map<String, Integer> toolNameCounts,
            List<IncomingMcpSnapshot> mcpServers,
            Map<String, Integer> mcpCodeCounts,
            List<IncomingSkillSnapshot> skills,
            Map<String, Integer> skillCodeCounts,
            Map<String, Integer> skillNameCounts,
            List<IncomingAgentSnapshot> agents,
            Map<String, Integer> agentCodeCounts,
            Map<String, Integer> agentNameCounts
    ) {
    }

    private record DefaultPlan(
            LinkedHashMap<ResourceKey, PreviewItem> items,
            Map<ResourceKey, List<String>> errorsByKey,
            List<String> globalMessages
    ) {
    }

    private record PlanComputation(
            LinkedHashMap<ResourceKey, PlannedOperation> operations,
            Map<ResourceKey, List<String>> errorsByKey,
            List<String> globalMessages,
            int skippedCount,
            int failedCount
    ) {
    }

    private record PlannedExecution(
            ParsedConfigBundle bundle,
            LinkedHashMap<ResourceKey, PlannedOperation> operations,
            List<String> globalMessages,
            int skippedCount,
            int failedCount,
            String failureSummary
    ) {
    }

    private record AppliedExecution(int createdCount, int overwrittenCount, int skippedCount, List<String> messages) {
    }

    private record PreviewItem(
            ResourceKey key,
            String displayName,
            AgentImportItemStatus status,
            List<AgentImportResolutionAction> availableActions,
            AgentImportResolutionAction defaultAction,
            List<String> messages,
            JsonNode incomingNode,
            JsonNode existingNode
    ) {
    }

    private record PlannedOperation(
            AgentImportResolutionAction action,
            ResourceKey key,
            String displayName,
            JsonNode incomingNode,
            JsonNode existingNode
    ) {
    }

    private record SkillPackageCandidate(byte[] zipBytes, String checksumMd5) {
    }

    private record IncomingToolSnapshot(
            String code,
            String name,
            String className,
            String beanName,
            String configJson,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled
    ) implements ImportableSnapshot<IncomingToolSnapshot, StoredToolSnapshot> {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleToolView(code, name, className, beanName, configJson, runtimeEnvRequirements, enabled));
        }

        ResolvedToolSnapshot toResolved(StoredToolSnapshot existing) {
            return new ResolvedToolSnapshot(code, name, className, beanName, configJson, runtimeEnvRequirements, enabled, existing == null ? null : existing.id());
        }

        @Override
        public Object resolvedValue(StoredToolSnapshot existing) {
            return toResolved(existing);
        }
    }

    private record StoredToolSnapshot(
            Long id,
            String code,
            String name,
            String className,
            String beanName,
            String configJson,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled
    ) implements ExistingSnapshot<IncomingToolSnapshot>, NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleToolView(code, name, className, beanName, configJson, runtimeEnvRequirements, enabled));
        }

        ResolvedToolSnapshot toResolved() {
            return new ResolvedToolSnapshot(code, name, className, beanName, configJson, runtimeEnvRequirements, enabled, id);
        }
    }

    private record ResolvedToolSnapshot(
            String code,
            String name,
            String className,
            String beanName,
            String configJson,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled,
            Long id
    ) implements NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }
    }

    private record IncomingMcpSnapshot(
            String code,
            String transportType,
            String target,
            Map<String, String> headers,
            Map<String, String> queryParams,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            Long timeoutMs,
            Long initializationTimeoutMs,
            boolean enabled
    ) implements ImportableSnapshot<IncomingMcpSnapshot, StoredMcpSnapshot> {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return code;
        }

        List<String> enableTools() {
            return List.of();
        }

        List<String> disableTools() {
            return List.of();
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleMcpServerView(code, transportType, target, headers, queryParams, runtimeEnvRequirements, timeoutMs, initializationTimeoutMs, enabled));
        }

        ResolvedMcpSnapshot toResolved(StoredMcpSnapshot existing) {
            return new ResolvedMcpSnapshot(code, transportType, target, headers, queryParams, runtimeEnvRequirements, timeoutMs, initializationTimeoutMs, enabled, existing == null ? null : existing.id());
        }

        @Override
        public Object resolvedValue(StoredMcpSnapshot existing) {
            return toResolved(existing);
        }
    }

    private record StoredMcpSnapshot(
            Long id,
            String code,
            String transportType,
            String target,
            Map<String, String> headers,
            Map<String, String> queryParams,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            Long timeoutMs,
            Long initializationTimeoutMs,
            boolean enabled
    ) implements ExistingSnapshot<IncomingMcpSnapshot>, NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return code;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleMcpServerView(code, transportType, target, headers, queryParams, runtimeEnvRequirements, timeoutMs, initializationTimeoutMs, enabled));
        }

        ResolvedMcpSnapshot toResolved() {
            return new ResolvedMcpSnapshot(code, transportType, target, headers, queryParams, runtimeEnvRequirements, timeoutMs, initializationTimeoutMs, enabled, id);
        }
    }

    private record ResolvedMcpSnapshot(
            String code,
            String transportType,
            String target,
            Map<String, String> headers,
            Map<String, String> queryParams,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            Long timeoutMs,
            Long initializationTimeoutMs,
            boolean enabled,
            Long id
    ) implements NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return code;
        }
    }

    private record IncomingSkillSnapshot(
            String code,
            String name,
            String description,
            String sourceType,
            String checksumMd5,
            String ossObjectKey,
            String packageLocation,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled
    ) implements ImportableSnapshot<IncomingSkillSnapshot, StoredSkillSnapshot> {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleSkillView(code, name, description, sourceType, checksumMd5, ossObjectKey, packageLocation, runtimeEnvRequirements, enabled));
        }

        ResolvedSkillSnapshot toResolved(StoredSkillSnapshot existing) {
            return new ResolvedSkillSnapshot(code, name, description, sourceType, checksumMd5, ossObjectKey, packageLocation, runtimeEnvRequirements, enabled, existing == null ? null : existing.id());
        }

        @Override
        public Object resolvedValue(StoredSkillSnapshot existing) {
            return toResolved(existing);
        }
    }

    private record StoredSkillSnapshot(
            Long id,
            String code,
            String name,
            String description,
            String sourceType,
            String checksumMd5,
            String ossObjectKey,
            String packageLocation,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled
    ) implements ExistingSnapshot<IncomingSkillSnapshot>, NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(new ConfigBundleSkillView(code, name, description, sourceType, checksumMd5, ossObjectKey, packageLocation, runtimeEnvRequirements, enabled));
        }

        ResolvedSkillSnapshot toResolved() {
            return new ResolvedSkillSnapshot(code, name, description, sourceType, checksumMd5, ossObjectKey, packageLocation, runtimeEnvRequirements, enabled, id);
        }
    }

    private record ResolvedSkillSnapshot(
            String code,
            String name,
            String description,
            String sourceType,
            String checksumMd5,
            String ossObjectKey,
            String packageLocation,
            List<RuntimeEnvRequirementView> runtimeEnvRequirements,
            boolean enabled,
            Long id
    ) implements NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }
    }

    private record IncomingAgentSnapshot(
            String profileCode,
            String profileName,
            String description,
            AgentProfileVersionType agentType,
            ProfileStatus status,
            boolean published,
            boolean publicDebug,
            String chatModel,
            String routingModel,
            String embeddingModel,
            String rerankModel,
            double temperature,
            int retrievalTopK,
            int reasoningBudget,
            String systemPrompt,
            List<String> toolCodes,
            List<String> skillCodes,
            List<AgentConfigMcpBindingView> mcpBindings,
            List<String> childAgentProfileCodes,
            List<AgentRuntimeEnvVarView> envVars
    ) implements ImportableSnapshot<IncomingAgentSnapshot, StoredAgentSnapshot> {
        @Override
        public String resourceCode() {
            return profileCode;
        }

        @Override
        public String displayName() {
            return profileName;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(toView());
        }

        AgentConfigSnapshotView toView() {
            return new AgentConfigSnapshotView(
                    profileCode,
                    profileName,
                    description,
                    agentType,
                    status,
                    published,
                    publicDebug,
                    chatModel,
                    embeddingModel,
                    rerankModel,
                    temperature,
                    retrievalTopK,
                    reasoningBudget,
                    systemPrompt,
                    toolCodes,
                    skillCodes,
                    mcpBindings,
                    childAgentProfileCodes,
                    envVars
            );
        }

        ResolvedAgentSnapshot toResolved(StoredAgentSnapshot existing) {
            return new ResolvedAgentSnapshot(
                    profileCode,
                    profileName,
                    description,
                    agentType,
                    status,
                    published,
                    publicDebug,
                    chatModel,
                    routingModel,
                    embeddingModel,
                    rerankModel,
                    temperature,
                    retrievalTopK,
                    reasoningBudget,
                    systemPrompt,
                    toolCodes,
                    skillCodes,
                    mcpBindings,
                    childAgentProfileCodes,
                    envVars,
                    existing == null ? null : existing.versionId()
            );
        }

        @Override
        public Object resolvedValue(StoredAgentSnapshot existing) {
            return toResolved(existing);
        }
    }

    private record StoredAgentSnapshot(
            Long profileId,
            Long versionId,
            Integer versionNumber,
            String profileCode,
            String profileName,
            String description,
            AgentProfileVersionType agentType,
            ProfileStatus status,
            boolean published,
            boolean publicDebug,
            String chatModel,
            String routingModel,
            String embeddingModel,
            String rerankModel,
            double temperature,
            int retrievalTopK,
            int reasoningBudget,
            String systemPrompt,
            List<String> toolCodes,
            List<String> skillCodes,
            List<AgentConfigMcpBindingView> mcpBindings,
            List<String> childAgentProfileCodes,
            List<AgentRuntimeEnvVarView> envVars
    ) implements ExistingSnapshot<IncomingAgentSnapshot>, NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return profileCode;
        }

        @Override
        public String displayName() {
            return profileName;
        }

        @Override
        public JsonNode previewNode(ObjectMapper objectMapper) {
            return objectMapper.valueToTree(toView());
        }

        AgentConfigSnapshotView toView() {
            return new AgentConfigSnapshotView(
                    profileCode,
                    profileName,
                    description,
                    agentType,
                    status,
                    published,
                    publicDebug,
                    chatModel,
                    embeddingModel,
                    rerankModel,
                    temperature,
                    retrievalTopK,
                    reasoningBudget,
                    systemPrompt,
                    toolCodes,
                    skillCodes,
                    mcpBindings,
                    childAgentProfileCodes,
                    envVars
            );
        }

        ResolvedAgentSnapshot toResolved() {
            return new ResolvedAgentSnapshot(
                    profileCode,
                    profileName,
                    description,
                    agentType,
                    status,
                    published,
                    publicDebug,
                    chatModel,
                    routingModel,
                    embeddingModel,
                    rerankModel,
                    temperature,
                    retrievalTopK,
                    reasoningBudget,
                    systemPrompt,
                    toolCodes,
                    skillCodes,
                    mcpBindings,
                    childAgentProfileCodes,
                    envVars,
                    versionId
            );
        }
    }

    private record ResolvedAgentSnapshot(
            String profileCode,
            String profileName,
            String description,
            AgentProfileVersionType agentType,
            ProfileStatus status,
            boolean published,
            boolean publicDebug,
            String chatModel,
            String routingModel,
            String embeddingModel,
            String rerankModel,
            double temperature,
            int retrievalTopK,
            int reasoningBudget,
            String systemPrompt,
            List<String> toolCodes,
            List<String> skillCodes,
            List<AgentConfigMcpBindingView> mcpBindings,
            List<String> childAgentProfileCodes,
            List<AgentRuntimeEnvVarView> envVars,
            Long versionId
    ) implements NamedResolvedSnapshot {
        @Override
        public String resourceCode() {
            return profileCode;
        }

        @Override
        public String displayName() {
            return profileName;
        }
    }
}
