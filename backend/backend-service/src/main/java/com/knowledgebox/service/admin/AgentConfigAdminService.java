package com.knowledgebox.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentConfigExportView;
import com.knowledgebox.api.AgentConfigMcpBindingView;
import com.knowledgebox.api.AgentConfigSnapshotView;
import com.knowledgebox.api.AgentImportCommitRequest;
import com.knowledgebox.api.AgentImportCommitResultView;
import com.knowledgebox.api.AgentImportDecisionRequest;
import com.knowledgebox.api.AgentImportItemStatus;
import com.knowledgebox.api.AgentImportPreviewItemView;
import com.knowledgebox.api.AgentImportPreviewView;
import com.knowledgebox.api.AgentImportResolutionAction;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
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
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.integration.AgentProfileBindingService;
import com.knowledgebox.service.integration.AgentProfileVersionPolicyService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AgentConfigAdminService {

    private static final String DEFAULT_SCHEMA_VERSION = "knowledge-box.agent-config.v1";
    private static final Pattern PROFILE_CODE_PATTERN = Pattern.compile("^[a-z0-9]+(?:[-_][a-z0-9]+)*$");
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    private final AgentProfileRepository agentProfileRepository;
    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final AgentProfileVersionAgentBindingRepository agentBindingRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillBindingRepository;
    private final AgentProfileBindingService agentProfileBindingService;
    private final AgentProfileVersionPolicyService policyService;
    private final ObjectMapper objectMapper;
    private final Map<String, StoredPreview> previewStore = new ConcurrentHashMap<>();

    public AgentConfigAdminService(
            AgentProfileRepository agentProfileRepository,
            AgentProfileVersionRepository agentProfileVersionRepository,
            AgentProfileVersionAgentBindingRepository agentBindingRepository,
            ModelCatalogRepository modelCatalogRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillBindingRepository,
            AgentProfileBindingService agentProfileBindingService,
            AgentProfileVersionPolicyService policyService,
            ObjectMapper objectMapper
    ) {
        this.agentProfileRepository = agentProfileRepository;
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.agentBindingRepository = agentBindingRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.agentProfileBindingService = agentProfileBindingService;
        this.policyService = policyService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentConfigExportView exportCurrentProfiles() {
        Map<String, StoredAgentSnapshot> currentSnapshots = loadCurrentSnapshots();
        List<AgentConfigSnapshotView> agents = currentSnapshots.values().stream()
                .sorted(Comparator.comparing(StoredAgentSnapshot::profileCode))
                .map(StoredAgentSnapshot::toView)
                .toList();
        return new AgentConfigExportView(DEFAULT_SCHEMA_VERSION, OffsetDateTime.now(), agents);
    }

    @Transactional(readOnly = true)
    public AgentImportPreviewView previewImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Agent import file is required");
        }
        ParsedImportBundle bundle = parseBundle(file);
        Map<String, StoredAgentSnapshot> existingSnapshots = loadCurrentSnapshots();
        DefaultPlan defaultPlan = buildDefaultPlan(bundle, existingSnapshots);
        List<AgentImportPreviewItemView> items = defaultPlan.items().values().stream()
                .map(item -> toPreviewItem(item, defaultPlan.errorsByProfileCode()))
                .toList();
        String token = storePreview(bundle);
        return new AgentImportPreviewView(
                token,
                bundle.schemaVersion(),
                items.size(),
                countByStatus(items, AgentImportItemStatus.READY_CREATE),
                countByStatus(items, AgentImportItemStatus.CODE_CONFLICT),
                countByStatus(items, AgentImportItemStatus.NAME_CONFLICT),
                countByStatus(items, AgentImportItemStatus.VALIDATION_ERROR),
                List.copyOf(defaultPlan.globalMessages()),
                items
        );
    }

    @Transactional
    public AgentImportCommitResultView commitImport(AgentImportCommitRequest request) {
        StoredPreview storedPreview = loadPreview(request.previewToken());
        ParsedImportBundle bundle = storedPreview.bundle();
        Map<String, StoredAgentSnapshot> existingSnapshots = loadCurrentSnapshots();
        Map<String, AgentImportResolutionAction> actionByCode = normalizeDecisionMap(request.decisions());
        PlannedExecution plannedExecution = buildExecutionPlan(bundle, existingSnapshots, actionByCode, true);
        AppliedExecution appliedExecution = applyExecution(plannedExecution);
        previewStore.remove(request.previewToken());
        return new AgentImportCommitResultView(
                appliedExecution.createdCount(),
                appliedExecution.overwrittenCount(),
                appliedExecution.skippedCount(),
                appliedExecution.messages()
        );
    }

    @Transactional
    public BootstrapImportResult importForBootstrap(InputStream inputStream, String sourceDescription, boolean failFast) {
        ParsedImportBundle bundle = parseBundle(inputStream, sourceDescription);
        Map<String, StoredAgentSnapshot> existingSnapshots = loadCurrentSnapshots();
        PlannedExecution plannedExecution = buildExecutionPlan(
                bundle,
                existingSnapshots,
                Collections.emptyMap(),
                false
        );
        if (failFast && plannedExecution.failedCount() > 0) {
            throw new IllegalStateException(plannedExecution.failureSummary());
        }
        AppliedExecution appliedExecution = applyExecution(plannedExecution);
        return new BootstrapImportResult(
                appliedExecution.createdCount(),
                appliedExecution.skippedCount(),
                plannedExecution.failedCount(),
                mergeBootstrapMessages(plannedExecution.globalMessages(), appliedExecution.messages())
        );
    }

    private List<String> mergeBootstrapMessages(List<String> globalMessages, List<String> executionMessages) {
        ArrayList<String> messages = new ArrayList<>(globalMessages);
        messages.addAll(executionMessages);
        return List.copyOf(messages);
    }

    private String storePreview(ParsedImportBundle bundle) {
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

    private int countByStatus(List<AgentImportPreviewItemView> items, AgentImportItemStatus status) {
        return Math.toIntExact(items.stream().filter(item -> item.status() == status).count());
    }

    private AgentImportPreviewItemView toPreviewItem(PreviewItem item, Map<String, List<String>> errorsByProfileCode) {
        List<String> errors = errorsByProfileCode.getOrDefault(item.snapshot().profileCode(), List.of());
        AgentImportItemStatus status = errors.isEmpty() ? item.status() : AgentImportItemStatus.VALIDATION_ERROR;
        ArrayList<String> messages = new ArrayList<>(item.messages());
        messages.addAll(errors);
        return new AgentImportPreviewItemView(
                item.snapshot().profileCode(),
                status,
                item.availableActions(),
                item.defaultAction(),
                List.copyOf(messages),
                item.snapshot().toView(),
                item.existing() == null ? null : item.existing().toView()
        );
    }

    private ParsedImportBundle parseBundle(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return parseBundle(inputStream, file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read agent import file", exception);
        }
    }

    private ParsedImportBundle parseBundle(InputStream inputStream, String sourceDescription) {
        try {
            JsonNode root = objectMapper.readTree(inputStream);
            if (root == null || root.isNull()) {
                throw new IllegalArgumentException("Agent config JSON is empty");
            }
            String schemaVersion = DEFAULT_SCHEMA_VERSION;
            JsonNode agentsNode;
            if (root.isArray()) {
                agentsNode = root;
            } else if (root.isObject()) {
                schemaVersion = readOptionalText(root.get("schemaVersion"), DEFAULT_SCHEMA_VERSION);
                agentsNode = root.get("agents");
            } else {
                throw new IllegalArgumentException("Agent config root must be an object or array");
            }
            if (agentsNode == null || !agentsNode.isArray()) {
                throw new IllegalArgumentException("Agent config must contain an `agents` array");
            }
            List<IncomingAgentSnapshot> snapshots = new ArrayList<>();
            Map<String, Integer> codeCounts = new LinkedHashMap<>();
            Map<String, Integer> nameCounts = new LinkedHashMap<>();
            for (int index = 0; index < agentsNode.size(); index++) {
                IncomingAgentSnapshot snapshot = normalizeIncomingSnapshot(agentsNode.get(index), index);
                snapshots.add(snapshot);
                codeCounts.merge(snapshot.profileCode(), 1, Integer::sum);
                nameCounts.merge(normalizeNameKey(snapshot.profileName()), 1, Integer::sum);
            }
            return new ParsedImportBundle(schemaVersion, sourceDescription, List.copyOf(snapshots), codeCounts, nameCounts);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse agent config JSON", exception);
        }
    }

    private IncomingAgentSnapshot normalizeIncomingSnapshot(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Agent item at index %s must be a JSON object".formatted(index));
        }
        String profileCode = normalizeProfileCode(readRequiredText(node.get("profileCode"), "profileCode", index));
        String profileName = readRequiredText(node.get("profileName"), "profileName", index).trim();
        String description = normalizeOptionalText(readOptionalText(node.get("description"), null));
        AgentProfileVersionType agentType = readEnum(node.get("agentType"), AgentProfileVersionType.class, "agentType", index);
        ProfileStatus status = readEnumOrDefault(node.get("status"), ProfileStatus.class, ProfileStatus.DRAFT);
        boolean published = readBoolean(node.get("published"), false);
        boolean publicDebug = readBoolean(node.get("publicDebug"), false);
        String chatModel = readRequiredText(node.get("chatModel"), "chatModel", index).trim();
        String routingModel = normalizeOptionalText(readOptionalText(node.get("routingModel"), null));
        if (!StringUtils.hasText(routingModel)) {
            routingModel = chatModel;
        }
        String embeddingModel = readRequiredText(node.get("embeddingModel"), "embeddingModel", index).trim();
        String rerankModel = normalizeOptionalText(readOptionalText(node.get("rerankModel"), null));
        double temperature = readDouble(node.get("temperature"), 0.2D);
        int retrievalTopK = readInt(node.get("retrievalTopK"), 6);
        int reasoningBudget = readInt(node.get("reasoningBudget"), 1);
        String systemPrompt = readRequiredText(node.get("systemPrompt"), "systemPrompt", index);
        List<String> toolCodes = normalizeCodeList(node.get("toolCodes"));
        List<String> skillCodes = normalizeCodeList(node.get("skillCodes"));
        List<AgentConfigMcpBindingView> mcpBindings = normalizeMcpBindings(node.get("mcpBindings"));
        List<String> childAgentProfileCodes = normalizeCodeList(node.get("childAgentProfileCodes"));
        return new IncomingAgentSnapshot(
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
                childAgentProfileCodes
        );
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
            String mcpCode = normalizeCode(readOptionalText(item.get("mcpCode"), ""));
            if (!StringUtils.hasText(mcpCode)) {
                continue;
            }
            bindings.put(mcpCode, new AgentConfigMcpBindingView(
                    mcpCode,
                    normalizeCodeList(item.get("enableTools")),
                    normalizeCodeList(item.get("disableTools"))
            ));
        }
        return List.copyOf(bindings.values());
    }

    private DefaultPlan buildDefaultPlan(ParsedImportBundle bundle, Map<String, StoredAgentSnapshot> existingSnapshots) {
        LinkedHashMap<String, PreviewItem> items = buildPreviewItems(bundle, existingSnapshots);
        Map<String, AgentImportResolutionAction> actions = items.values().stream()
                .collect(Collectors.toMap(
                        item -> item.snapshot().profileCode(),
                        PreviewItem::defaultAction,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        PlanComputation computation = computePlan(bundle, existingSnapshots, items, actions, false, true);
        return new DefaultPlan(items, computation.errorsByProfileCode(), computation.globalMessages());
    }

    private PlannedExecution buildExecutionPlan(
            ParsedImportBundle bundle,
            Map<String, StoredAgentSnapshot> existingSnapshots,
            Map<String, AgentImportResolutionAction> actionByCode,
            boolean strict
    ) {
        LinkedHashMap<String, PreviewItem> items = buildPreviewItems(bundle, existingSnapshots);
        PlanComputation computation = computePlan(bundle, existingSnapshots, items, actionByCode, strict, false);
        if (strict && !computation.errorsByProfileCode().isEmpty()) {
            throw new IllegalArgumentException(renderErrors(computation.errorsByProfileCode(), computation.globalMessages()));
        }
        String failureSummary = computation.errorsByProfileCode().isEmpty()
                ? ""
                : renderErrors(computation.errorsByProfileCode(), computation.globalMessages());
        return new PlannedExecution(
                computation.operations(),
                computation.globalMessages(),
                computation.skippedCount(),
                computation.failedCount(),
                failureSummary
        );
    }

    private String renderErrors(Map<String, List<String>> errorsByProfileCode, List<String> globalMessages) {
        ArrayList<String> messages = new ArrayList<>(globalMessages);
        errorsByProfileCode.forEach((profileCode, errors) -> {
            if (errors == null || errors.isEmpty()) {
                return;
            }
            messages.add(profileCode + ": " + String.join("; ", errors));
        });
        return String.join("\n", messages);
    }

    private LinkedHashMap<String, PreviewItem> buildPreviewItems(
            ParsedImportBundle bundle,
            Map<String, StoredAgentSnapshot> existingSnapshots
    ) {
        LinkedHashMap<String, PreviewItem> items = new LinkedHashMap<>();
        Map<String, List<StoredAgentSnapshot>> existingByName = existingSnapshots.values().stream()
                .collect(Collectors.groupingBy(snapshot -> normalizeNameKey(snapshot.profileName()), LinkedHashMap::new, Collectors.toList()));
        for (IncomingAgentSnapshot snapshot : bundle.snapshots()) {
            StoredAgentSnapshot existingByCode = existingSnapshots.get(snapshot.profileCode());
            if (existingByCode != null) {
                items.put(snapshot.profileCode(), new PreviewItem(
                        snapshot,
                        existingByCode,
                        AgentImportItemStatus.CODE_CONFLICT,
                        List.of(AgentImportResolutionAction.SKIP, AgentImportResolutionAction.OVERWRITE_EXISTING),
                        AgentImportResolutionAction.SKIP,
                        List.of("数据库中已存在相同 profileCode，将默认跳过；管理员可改为覆盖")
                ));
                continue;
            }
            List<StoredAgentSnapshot> sameName = existingByName.getOrDefault(normalizeNameKey(snapshot.profileName()), List.of()).stream()
                    .filter(existing -> !Objects.equals(existing.profileCode(), snapshot.profileCode()))
                    .toList();
            if (!sameName.isEmpty()) {
                StoredAgentSnapshot existing = sameName.size() == 1 ? sameName.get(0) : null;
                String matchedCodes = sameName.stream().map(StoredAgentSnapshot::profileCode).collect(Collectors.joining(", "));
                items.put(snapshot.profileCode(), new PreviewItem(
                        snapshot,
                        existing,
                        AgentImportItemStatus.NAME_CONFLICT,
                        List.of(AgentImportResolutionAction.SKIP),
                        AgentImportResolutionAction.SKIP,
                        List.of("数据库中已存在同名 Agent，涉及 profileCode: " + matchedCodes + "；该项只能跳过")
                ));
                continue;
            }
            items.put(snapshot.profileCode(), new PreviewItem(
                    snapshot,
                    null,
                    AgentImportItemStatus.READY_CREATE,
                    List.of(AgentImportResolutionAction.CREATE),
                    AgentImportResolutionAction.CREATE,
                    List.of("将按配置直接创建该 Agent")
            ));
        }
        return items;
    }

    private PlanComputation computePlan(
            ParsedImportBundle bundle,
            Map<String, StoredAgentSnapshot> existingSnapshots,
            LinkedHashMap<String, PreviewItem> items,
            Map<String, AgentImportResolutionAction> requestedActions,
            boolean strict,
            boolean previewMode
    ) {
        LinkedHashMap<String, PlannedOperation> operations = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> errorsByProfileCode = new LinkedHashMap<>();
        ArrayList<String> globalMessages = new ArrayList<>();
        int skippedCount = 0;

        validateBundleLevelUniqueness(bundle, errorsByProfileCode);
        for (PreviewItem item : items.values()) {
            AgentImportResolutionAction action = resolveAction(item, requestedActions.get(item.snapshot().profileCode()));
            if (action == AgentImportResolutionAction.CREATE || action == AgentImportResolutionAction.OVERWRITE_EXISTING) {
                operations.put(item.snapshot().profileCode(), new PlannedOperation(action, item.snapshot(), item.existing()));
            } else {
                skippedCount++;
                if (!previewMode) {
                    globalMessages.add(item.snapshot().profileCode() + " 已按规则跳过: " + String.join("; ", item.messages()));
                }
            }
        }

        if (strict) {
            validateOperations(operations, existingSnapshots, errorsByProfileCode);
        } else {
            int previousSize = -1;
            while (previousSize != operations.size()) {
                previousSize = operations.size();
                LinkedHashMap<String, List<String>> iterationErrors = new LinkedHashMap<>();
                validateOperations(operations, existingSnapshots, iterationErrors);
                if (iterationErrors.isEmpty()) {
                    break;
                }
                if (previewMode) {
                    errorsByProfileCode.putAll(iterationErrors);
                    break;
                }
                for (Map.Entry<String, List<String>> entry : iterationErrors.entrySet()) {
                    errorsByProfileCode.put(entry.getKey(), entry.getValue());
                    PlannedOperation removed = operations.remove(entry.getKey());
                    if (removed != null) {
                        globalMessages.add(removed.snapshot().profileCode() + " 导入失败并已跳过: " + String.join("; ", entry.getValue()));
                    }
                }
            }
            if (previewMode) {
                LinkedHashMap<String, List<String>> previewErrors = new LinkedHashMap<>();
                validateOperations(operations, existingSnapshots, previewErrors);
                previewErrors.forEach(errorsByProfileCode::put);
            }
        }

        int failedCount = Math.toIntExact(errorsByProfileCode.values().stream().filter(errors -> errors != null && !errors.isEmpty()).count());
        return new PlanComputation(operations, errorsByProfileCode, List.copyOf(globalMessages), skippedCount, failedCount);
    }

    private void validateBundleLevelUniqueness(ParsedImportBundle bundle, Map<String, List<String>> errorsByProfileCode) {
        for (IncomingAgentSnapshot snapshot : bundle.snapshots()) {
            List<String> errors = errorsByProfileCode.computeIfAbsent(snapshot.profileCode(), ignored -> new ArrayList<>());
            if (bundle.codeCounts().getOrDefault(snapshot.profileCode(), 0) > 1) {
                errors.add("JSON 中存在重复的 profileCode: " + snapshot.profileCode());
            }
            if (bundle.nameCounts().getOrDefault(normalizeNameKey(snapshot.profileName()), 0) > 1) {
                errors.add("JSON 中存在重复的 profileName: " + snapshot.profileName());
            }
            if (!PROFILE_CODE_PATTERN.matcher(snapshot.profileCode()).matches()) {
                errors.add("profileCode 仅支持小写字母、数字、-、_");
            }
            if (!StringUtils.hasText(snapshot.systemPrompt())) {
                errors.add("systemPrompt 不能为空");
            }
            if (snapshot.retrievalTopK() < 1) {
                errors.add("retrievalTopK 必须大于等于 1");
            }
            if (snapshot.reasoningBudget() < 0) {
                errors.add("reasoningBudget 不能小于 0");
            }
            if (snapshot.temperature() < 0.0D || snapshot.temperature() > 2.0D) {
                errors.add("temperature 必须位于 0.0 到 2.0 之间");
            }
            if (snapshot.published() && snapshot.status() != ProfileStatus.PUBLISHED) {
                errors.add("published=true 时 status 必须为 PUBLISHED");
            }
            if (snapshot.publicDebug() && snapshot.agentType() != AgentProfileVersionType.ENTRY) {
                errors.add("publicDebug=true 时 agentType 必须为 ENTRY");
            }
        }
        errorsByProfileCode.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    private void validateOperations(
            Map<String, PlannedOperation> operations,
            Map<String, StoredAgentSnapshot> existingSnapshots,
            Map<String, List<String>> errorsByProfileCode
    ) {
        LinkedHashMap<String, ResolvedAgentSnapshot> finalSnapshots = new LinkedHashMap<>();
        existingSnapshots.forEach((profileCode, snapshot) -> finalSnapshots.put(profileCode, snapshot.toResolved()));
        operations.forEach((profileCode, operation) -> finalSnapshots.put(profileCode, operation.snapshot().toResolved(operation.existing())));

        validateMainConstraints(finalSnapshots, operations.keySet(), errorsByProfileCode);
        validateNameUniqueness(finalSnapshots.values(), operations.keySet(), errorsByProfileCode);
        for (PlannedOperation operation : operations.values()) {
            validateDependencies(operation.snapshot().toResolved(operation.existing()), finalSnapshots, errorsByProfileCode);
        }
    }

    private void validateMainConstraints(
            Map<String, ResolvedAgentSnapshot> finalSnapshots,
            Set<String> importedCodes,
            Map<String, List<String>> errorsByProfileCode
    ) {
        List<ResolvedAgentSnapshot> mainAgents = finalSnapshots.values().stream()
                .filter(snapshot -> snapshot.agentType() == AgentProfileVersionType.MAIN)
                .toList();
        if (mainAgents.size() != 1) {
            for (ResolvedAgentSnapshot snapshot : mainAgents) {
                if (importedCodes.contains(snapshot.profileCode())) {
                    addError(errorsByProfileCode, snapshot.profileCode(), "最终结果必须且只能保留一个 MAIN Agent");
                }
            }
            if (mainAgents.isEmpty()) {
                for (String importedCode : importedCodes) {
                    addError(errorsByProfileCode, importedCode, "导入完成后必须保留一个 MAIN Agent");
                }
            }
        }
        List<ResolvedAgentSnapshot> publishedAgents = finalSnapshots.values().stream()
                .filter(ResolvedAgentSnapshot::published)
                .toList();
        if (publishedAgents.size() != 1 || publishedAgents.stream().anyMatch(snapshot -> snapshot.agentType() != AgentProfileVersionType.MAIN)) {
            for (ResolvedAgentSnapshot snapshot : publishedAgents) {
                if (importedCodes.contains(snapshot.profileCode())) {
                    addError(errorsByProfileCode, snapshot.profileCode(), "最终结果必须且只能保留一个已发布的 MAIN Agent");
                }
            }
            if (publishedAgents.isEmpty()) {
                for (String importedCode : importedCodes) {
                    addError(errorsByProfileCode, importedCode, "导入完成后必须保留一个已发布的 MAIN Agent");
                }
            }
        }
    }

    private void validateNameUniqueness(
            Collection<ResolvedAgentSnapshot> snapshots,
            Set<String> importedCodes,
            Map<String, List<String>> errorsByProfileCode
    ) {
        Map<String, List<ResolvedAgentSnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> normalizeNameKey(snapshot.profileName()), LinkedHashMap::new, Collectors.toList()));
        for (List<ResolvedAgentSnapshot> sameNameSnapshots : grouped.values()) {
            if (sameNameSnapshots.size() <= 1) {
                continue;
            }
            String codes = sameNameSnapshots.stream().map(ResolvedAgentSnapshot::profileCode).collect(Collectors.joining(", "));
            for (ResolvedAgentSnapshot snapshot : sameNameSnapshots) {
                if (importedCodes.contains(snapshot.profileCode())) {
                    addError(errorsByProfileCode, snapshot.profileCode(), "profileName 与其他 Agent 重复: " + codes);
                }
            }
        }
    }

    private void validateDependencies(
            ResolvedAgentSnapshot snapshot,
            Map<String, ResolvedAgentSnapshot> finalSnapshots,
            Map<String, List<String>> errorsByProfileCode
    ) {
        validateEnabledModel(snapshot.chatModel(), ModelType.CHAT, snapshot.profileCode(), errorsByProfileCode, "chatModel");
        validateEnabledModel(snapshot.embeddingModel(), ModelType.EMBEDDING, snapshot.profileCode(), errorsByProfileCode, "embeddingModel");
        if (StringUtils.hasText(snapshot.rerankModel())) {
            validateEnabledModel(snapshot.rerankModel(), ModelType.RERANK, snapshot.profileCode(), errorsByProfileCode, "rerankModel");
        }
        if (snapshot.published() && snapshot.agentType() != AgentProfileVersionType.MAIN) {
            addError(errorsByProfileCode, snapshot.profileCode(), "仅 MAIN Agent 可以设置为 published=true");
        }
        if (snapshot.publicDebug() && snapshot.agentType() != AgentProfileVersionType.ENTRY) {
            addError(errorsByProfileCode, snapshot.profileCode(), "仅 ENTRY Agent 可以设置为 publicDebug=true");
        }
        for (String toolCode : snapshot.toolCodes()) {
            ToolDefinition definition = toolDefinitionRepository.findByCode(toolCode).orElse(null);
            if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
                addError(errorsByProfileCode, snapshot.profileCode(), "Tool 不存在或未启用: " + toolCode);
            }
        }
        for (String skillCode : snapshot.skillCodes()) {
            SkillBinding binding = skillBindingRepository.findByCode(skillCode).orElse(null);
            if (binding == null || !Boolean.TRUE.equals(binding.getEnabled())) {
                addError(errorsByProfileCode, snapshot.profileCode(), "Skill 不存在或未启用: " + skillCode);
            }
        }
        for (AgentConfigMcpBindingView mcpBinding : snapshot.mcpBindings()) {
            McpServerConfig config = mcpServerConfigRepository.findByCode(mcpBinding.mcpCode()).orElse(null);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                addError(errorsByProfileCode, snapshot.profileCode(), "MCP 服务不存在或未启用: " + mcpBinding.mcpCode());
            }
            for (String toolCode : mcpBinding.enableTools()) {
                ToolDefinition definition = toolDefinitionRepository.findByCode(toolCode).orElse(null);
                if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
                    addError(errorsByProfileCode, snapshot.profileCode(), "MCP enableTools 引用了不存在或未启用的 Tool: " + toolCode);
                }
            }
            for (String toolCode : mcpBinding.disableTools()) {
                ToolDefinition definition = toolDefinitionRepository.findByCode(toolCode).orElse(null);
                if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
                    addError(errorsByProfileCode, snapshot.profileCode(), "MCP disableTools 引用了不存在或未启用的 Tool: " + toolCode);
                }
            }
        }
        if (!snapshot.childAgentProfileCodes().isEmpty()) {
            if (snapshot.agentType() != AgentProfileVersionType.MAIN
                    && snapshot.agentType() != AgentProfileVersionType.ENTRY
                    && snapshot.agentType() != AgentProfileVersionType.ORCHESTRATOR) {
                addError(errorsByProfileCode, snapshot.profileCode(), "仅 MAIN / ENTRY / ORCHESTRATOR 可以绑定子 Agent");
            }
        }
        for (String childProfileCode : snapshot.childAgentProfileCodes()) {
            if (Objects.equals(childProfileCode, snapshot.profileCode())) {
                addError(errorsByProfileCode, snapshot.profileCode(), "Agent 不能绑定自己作为子 Agent");
                continue;
            }
            ResolvedAgentSnapshot child = finalSnapshots.get(childProfileCode);
            if (child == null) {
                addError(errorsByProfileCode, snapshot.profileCode(), "子 Agent 不存在: " + childProfileCode);
                continue;
            }
            if (child.agentType() != AgentProfileVersionType.ATOMIC) {
                addError(errorsByProfileCode, snapshot.profileCode(), "子 Agent 必须为 ATOMIC: " + childProfileCode);
            }
        }
    }

    private void validateEnabledModel(
            String code,
            ModelType modelType,
            String profileCode,
            Map<String, List<String>> errorsByProfileCode,
            String fieldName
    ) {
        ModelCatalog modelCatalog = modelCatalogRepository.findByCodeAndModelTypeAndEnabledTrue(code, modelType).orElse(null);
        if (modelCatalog == null) {
            addError(errorsByProfileCode, profileCode, fieldName + " 未找到已启用的 " + modelType + " 模型: " + code);
        }
    }

    private void addError(Map<String, List<String>> errorsByProfileCode, String profileCode, String message) {
        errorsByProfileCode.computeIfAbsent(profileCode, ignored -> new ArrayList<>());
        List<String> messages = errorsByProfileCode.get(profileCode);
        if (!messages.contains(message)) {
            messages.add(message);
        }
    }

    private AgentImportResolutionAction resolveAction(PreviewItem item, AgentImportResolutionAction requestedAction) {
        if (requestedAction == null) {
            return item.defaultAction();
        }
        if (!item.availableActions().contains(requestedAction)) {
            throw new IllegalArgumentException(
                    "Invalid action %s for profileCode %s".formatted(requestedAction, item.snapshot().profileCode())
            );
        }
        return requestedAction;
    }

    private Map<String, AgentImportResolutionAction> normalizeDecisionMap(List<AgentImportDecisionRequest> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, AgentImportResolutionAction> result = new LinkedHashMap<>();
        for (AgentImportDecisionRequest decision : decisions) {
            if (decision == null || !StringUtils.hasText(decision.profileCode()) || decision.action() == null) {
                continue;
            }
            result.put(normalizeProfileCode(decision.profileCode()), decision.action());
        }
        return result;
    }

    private AppliedExecution applyExecution(PlannedExecution plannedExecution) {
        if (plannedExecution.operations().isEmpty()) {
            return new AppliedExecution(0, 0, plannedExecution.skippedCount(), List.copyOf(plannedExecution.globalMessages()));
        }
        LinkedHashMap<String, StoredAgentSnapshot> persistedSnapshots = new LinkedHashMap<>();
        int created = 0;
        int overwritten = 0;
        for (PlannedOperation operation : plannedExecution.operations().values()) {
            if (operation.action() == AgentImportResolutionAction.CREATE) {
                persistedSnapshots.put(operation.snapshot().profileCode(), createSnapshot(operation.snapshot()));
                created++;
            } else if (operation.action() == AgentImportResolutionAction.OVERWRITE_EXISTING) {
                persistedSnapshots.put(operation.snapshot().profileCode(), overwriteSnapshot(operation));
                overwritten++;
            }
        }

        Map<String, StoredAgentSnapshot> finalSnapshots = loadCurrentSnapshots();
        finalSnapshots.putAll(persistedSnapshots);
        for (PlannedOperation operation : plannedExecution.operations().values()) {
            StoredAgentSnapshot snapshot = finalSnapshots.get(operation.snapshot().profileCode());
            if (snapshot == null) {
                continue;
            }
            List<Long> childVersionIds = operation.snapshot().childAgentProfileCodes().stream()
                    .map(childCode -> finalSnapshots.get(childCode))
                    .filter(Objects::nonNull)
                    .map(StoredAgentSnapshot::versionId)
                    .toList();
            agentProfileBindingService.updateBindings(
                    snapshot.versionId(),
                    new UpdateAgentProfileVersionBindingsRequest(
                            operation.snapshot().toolCodes(),
                            operation.snapshot().skillCodes(),
                            toProfileVersionMcpBindings(operation.snapshot().mcpBindings()),
                            childVersionIds,
                            List.of()
                    )
            );
        }
        int skipped = Math.max(0, plannedExecution.skippedCount());
        return new AppliedExecution(created, overwritten, skipped, List.copyOf(plannedExecution.globalMessages()));
    }

    private StoredAgentSnapshot createSnapshot(IncomingAgentSnapshot snapshot) {
        AgentProfile profile = new AgentProfile();
        profile.setCode(snapshot.profileCode());
        profile.setName(snapshot.profileName());
        profile.setDescription(snapshot.description());
        profile = agentProfileRepository.save(profile);

        AgentProfileVersion version = new AgentProfileVersion();
        version.setProfile(profile);
        version.setVersionNumber(1);
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
                snapshot.childAgentProfileCodes()
        );
    }

    private StoredAgentSnapshot overwriteSnapshot(PlannedOperation operation) {
        StoredAgentSnapshot existing = operation.existing();
        if (existing == null || existing.versionId() == null || existing.profileId() == null) {
            throw new IllegalArgumentException("Existing agent snapshot is required for overwrite: " + operation.snapshot().profileCode());
        }
        AgentProfile profile = agentProfileRepository.findById(existing.profileId())
                .orElseThrow(() -> new IllegalArgumentException("Agent profile not found: " + existing.profileId()));
        AgentProfileVersion version = agentProfileVersionRepository.findById(existing.versionId())
                .orElseThrow(() -> new IllegalArgumentException("Agent profile version not found: " + existing.versionId()));

        profile.setName(operation.snapshot().profileName());
        profile.setDescription(operation.snapshot().description());
        agentProfileRepository.save(profile);

        version.setStatus(operation.snapshot().status());
        version.setPublished(operation.snapshot().published());
        version.setPublicDebug(operation.snapshot().publicDebug());
        version.setAgentType(policyService.normalizeType(operation.snapshot().agentType()));
        version.setChatModel(operation.snapshot().chatModel());
        version.setRoutingModel(operation.snapshot().chatModel());
        version.setEmbeddingModel(operation.snapshot().embeddingModel());
        version.setRerankModel(operation.snapshot().rerankModel());
        version.setTemperature(operation.snapshot().temperature());
        version.setRetrievalTopK(operation.snapshot().retrievalTopK());
        version.setReasoningBudget(operation.snapshot().reasoningBudget());
        version.setSystemPrompt(operation.snapshot().systemPrompt());
        version = agentProfileVersionRepository.save(version);

        return new StoredAgentSnapshot(
                profile.getId(),
                version.getId(),
                version.getVersionNumber(),
                operation.snapshot().profileCode(),
                operation.snapshot().profileName(),
                operation.snapshot().description(),
                operation.snapshot().agentType(),
                operation.snapshot().status(),
                operation.snapshot().published(),
                operation.snapshot().publicDebug(),
                operation.snapshot().chatModel(),
                operation.snapshot().routingModel(),
                operation.snapshot().embeddingModel(),
                operation.snapshot().rerankModel(),
                operation.snapshot().temperature(),
                operation.snapshot().retrievalTopK(),
                operation.snapshot().reasoningBudget(),
                operation.snapshot().systemPrompt(),
                operation.snapshot().toolCodes(),
                operation.snapshot().skillCodes(),
                operation.snapshot().mcpBindings(),
                operation.snapshot().childAgentProfileCodes()
        );
    }

    private Map<String, StoredAgentSnapshot> loadCurrentSnapshots() {
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
                    toConfigMcpBindings(bindings.mcpBindings()),
                    bindings.childAgentBindings().stream().map(child -> child.profileCode()).toList()
            ));
        }
        return snapshots;
    }

    private List<AgentProfileVersionMcpBindingView> toProfileVersionMcpBindings(List<AgentConfigMcpBindingView> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> new AgentProfileVersionMcpBindingView(
                        binding.mcpCode(),
                        binding.enableTools(),
                        binding.disableTools()
                ))
                .toList();
    }

    private List<AgentConfigMcpBindingView> toConfigMcpBindings(List<AgentProfileVersionMcpBindingView> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> new AgentConfigMcpBindingView(
                        binding.mcpCode(),
                        binding.enableTools(),
                        binding.disableTools()
                ))
                .toList();
    }

    private String readRequiredText(JsonNode node, String fieldName, int index) {
        String value = readOptionalText(node, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing field `%s` at agent index %s".formatted(fieldName, index));
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

    private <E extends Enum<E>> E readEnum(JsonNode node, Class<E> enumType, String fieldName, int index) {
        String text = readRequiredText(node, fieldName, index).trim();
        try {
            return Enum.valueOf(enumType, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value `%s` for field `%s` at agent index %s".formatted(text, fieldName, index));
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
            String value = normalizeCode(readOptionalText(item, ""));
            if (StringUtils.hasText(value)) {
                codes.add(value);
            }
        }
        return List.copyOf(codes);
    }

    private String normalizeProfileCode(String profileCode) {
        if (!StringUtils.hasText(profileCode)) {
            throw new IllegalArgumentException("profileCode is required");
        }
        return profileCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return normalizeOptionalText(code == null ? null : code.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizeNameKey(String profileName) {
        return profileName == null ? "" : profileName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record BootstrapImportResult(
            int createdCount,
            int skippedCount,
            int failedCount,
            List<String> messages
    ) {
    }

    private record StoredPreview(ParsedImportBundle bundle, OffsetDateTime createdAt) {
    }

    private record ParsedImportBundle(
            String schemaVersion,
            String sourceDescription,
            List<IncomingAgentSnapshot> snapshots,
            Map<String, Integer> codeCounts,
            Map<String, Integer> nameCounts
    ) {
    }

    private record DefaultPlan(
            LinkedHashMap<String, PreviewItem> items,
            Map<String, List<String>> errorsByProfileCode,
            List<String> globalMessages
    ) {
    }

    private record PlanComputation(
            LinkedHashMap<String, PlannedOperation> operations,
            Map<String, List<String>> errorsByProfileCode,
            List<String> globalMessages,
            int skippedCount,
            int failedCount
    ) {
    }

    private record PlannedExecution(
            LinkedHashMap<String, PlannedOperation> operations,
            List<String> globalMessages,
            int skippedCount,
            int failedCount,
            String failureSummary
    ) {
    }

    private record AppliedExecution(
            int createdCount,
            int overwrittenCount,
            int skippedCount,
            List<String> messages
    ) {
    }

    private record PreviewItem(
            IncomingAgentSnapshot snapshot,
            StoredAgentSnapshot existing,
            AgentImportItemStatus status,
            List<AgentImportResolutionAction> availableActions,
            AgentImportResolutionAction defaultAction,
            List<String> messages
    ) {
    }

    private record PlannedOperation(
            AgentImportResolutionAction action,
            IncomingAgentSnapshot snapshot,
            StoredAgentSnapshot existing
    ) {
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
            List<String> childAgentProfileCodes
    ) {
        private AgentConfigSnapshotView toView() {
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
                    List.of()
            );
        }

        private ResolvedAgentSnapshot toResolved(StoredAgentSnapshot existing) {
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
                    existing == null ? null : existing.versionId()
            );
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
            List<String> childAgentProfileCodes
    ) {
        private AgentConfigSnapshotView toView() {
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
                    List.of()
            );
        }

        private ResolvedAgentSnapshot toResolved() {
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
            Long versionId
    ) {
    }
}
