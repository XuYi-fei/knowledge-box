package com.knowledgebox.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.RuntimeEnvRequirementView;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionEnvVar;
import com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource;
import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionEnvVarRepository;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import com.knowledgebox.service.chat.AgentRuntimeEnvironmentResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AgentRuntimeEnvStartupCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeEnvStartupCheckRunner.class);
    private static final String LOG_PREFIX = "[AGENT-RUNTIME-ENV-CHECK]";
    private static final TypeReference<List<RuntimeEnvRequirementView>> REQUIREMENTS_TYPE = new TypeReference<>() {
    };

    private final KnowledgeBoxProperties properties;
    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final AgentProfileVersionEnvVarRepository envVarRepository;
    private final AgentProfileVersionToolBindingRepository toolBindingRepository;
    private final AgentProfileVersionSkillBindingRepository skillBindingRepository;
    private final AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final SkillBindingRepository skillBindingRepositoryRef;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final AgentRuntimeEnvironmentResolver environmentResolver;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public AgentRuntimeEnvStartupCheckRunner(
            KnowledgeBoxProperties properties,
            AgentProfileVersionRepository agentProfileVersionRepository,
            AgentProfileVersionEnvVarRepository envVarRepository,
            AgentProfileVersionToolBindingRepository toolBindingRepository,
            AgentProfileVersionSkillBindingRepository skillBindingRepository,
            AgentProfileVersionMcpBindingRepository mcpBindingRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            SkillBindingRepository skillBindingRepositoryRef,
            McpServerConfigRepository mcpServerConfigRepository,
            AgentRuntimeEnvironmentResolver environmentResolver,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.properties = properties;
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.envVarRepository = envVarRepository;
        this.toolBindingRepository = toolBindingRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.mcpBindingRepository = mcpBindingRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.skillBindingRepositoryRef = skillBindingRepositoryRef;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.environmentResolver = environmentResolver;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeBoxProperties.RuntimeEnvCheck check = properties.getAgent().getRuntimeEnvCheck();
        if (!check.isEnabled()) {
            return;
        }
        StartupCheckReport report = inspect(check);
        log.info(
                "{} completed. checkedAgents={}, issueCount={}, failFast={}, checkUnpublished={}, includeInline={}, requiredProcessEnvKeys={}",
                LOG_PREFIX,
                report.checkedAgents(),
                report.issueCount(),
                check.isFailFast(),
                check.isCheckUnpublished(),
                check.isIncludeInline(),
                check.getRequiredProcessEnvKeys()
        );
        if (report.issues().isEmpty()) {
            log.info("{} all required runtime env keys are available.", LOG_PREFIX);
            return;
        }
        for (String issue : report.issues()) {
            log.warn("{} {}", LOG_PREFIX, issue);
        }
        if (check.isFailFast()) {
            throw new IllegalStateException(
                    "Agent runtime env startup check failed with %s issue(s). First issue: %s"
                            .formatted(report.issueCount(), report.issues().get(0))
            );
        }
    }

    StartupCheckReport inspect(KnowledgeBoxProperties.RuntimeEnvCheck check) {
        ArrayList<String> issues = new ArrayList<>();
        validateExtraProcessEnvKeys(check, issues);

        List<AgentProfileVersion> versions = agentProfileVersionRepository.findAllForAdmin();
        int checkedAgents = 0;
        for (AgentProfileVersion version : versions) {
            if (!check.isCheckUnpublished() && !Boolean.TRUE.equals(version.getPublished())) {
                continue;
            }
            checkedAgents++;
            inspectVersion(version, check, issues);
        }
        return new StartupCheckReport(checkedAgents, List.copyOf(issues));
    }

    private void validateExtraProcessEnvKeys(KnowledgeBoxProperties.RuntimeEnvCheck check, List<String> issues) {
        if (check.getRequiredProcessEnvKeys() == null) {
            return;
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String key : check.getRequiredProcessEnvKeys()) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            keys.add(key.trim());
        }
        for (String key : keys) {
            if (!StringUtils.hasText(resolveExternalValue(key))) {
                issues.add("宿主环境变量缺失: " + key);
            }
        }
    }

    private void inspectVersion(
            AgentProfileVersion version,
            KnowledgeBoxProperties.RuntimeEnvCheck check,
            List<String> issues
    ) {
        List<AgentProfileVersionEnvVar> envVars = envVarRepository.findByProfileVersionIdOrderByEnvKeyAsc(version.getId());
        Set<String> availableKeys = new LinkedHashSet<>();
        for (AgentProfileVersionEnvVar envVar : envVars) {
            String envKey = normalizeKey(envVar.getEnvKey());
            if (envKey == null) {
                continue;
            }
            validateValueSource(version, envVar, check, issues);
            if (StringUtils.hasText(environmentResolver.resolveValue(envVar))) {
                availableKeys.add(envKey);
            }
        }

        String agentLabel = agentLabel(version);
        for (AgentProfileVersionToolBinding binding : toolBindingRepository.findByProfileVersionId(version.getId())) {
            ToolDefinition tool = toolDefinitionRepository.findById(binding.getToolId()).orElse(null);
            if (tool == null || !Boolean.TRUE.equals(tool.getEnabled())) {
                continue;
            }
            validateRequirements(agentLabel, "Tool", tool.getCode(), tool.getRuntimeEnvRequirementsJson(), availableKeys, issues);
        }
        for (AgentProfileVersionSkillBinding binding : skillBindingRepository.findByProfileVersionId(version.getId())) {
            SkillBinding skill = skillBindingRepositoryRef.findById(binding.getSkillId()).orElse(null);
            if (skill == null || !Boolean.TRUE.equals(skill.getEnabled())) {
                continue;
            }
            validateRequirements(agentLabel, "Skill", skill.getCode(), skill.getRuntimeEnvRequirementsJson(), availableKeys, issues);
        }
        for (AgentProfileVersionMcpBinding binding : mcpBindingRepository.findByProfileVersionId(version.getId())) {
            McpServerConfig mcp = mcpServerConfigRepository.findById(binding.getMcpId()).orElse(null);
            if (mcp == null || !Boolean.TRUE.equals(mcp.getEnabled())) {
                continue;
            }
            validateRequirements(agentLabel, "MCP", mcp.getCode(), mcp.getRuntimeEnvRequirementsJson(), availableKeys, issues);
        }
    }

    private void validateValueSource(
            AgentProfileVersion version,
            AgentProfileVersionEnvVar envVar,
            KnowledgeBoxProperties.RuntimeEnvCheck check,
            List<String> issues
    ) {
        String envKey = normalizeKey(envVar.getEnvKey());
        if (envKey == null) {
            return;
        }
        String agentLabel = agentLabel(version);
        AgentRuntimeEnvValueSource valueSource = envVar.getValueSource() == null
                ? AgentRuntimeEnvValueSource.INLINE
                : envVar.getValueSource();
        if (valueSource == AgentRuntimeEnvValueSource.PROCESS_ENV) {
            if (!StringUtils.hasText(envVar.getSourceRef())) {
                issues.add(agentLabel + " envVar " + envKey + " 使用 PROCESS_ENV，但 sourceRef 为空");
                return;
            }
            String sourceRef = envVar.getSourceRef().trim();
            if (!StringUtils.hasText(resolveExternalValue(sourceRef))) {
                issues.add(agentLabel + " envVar " + envKey + " 缺少宿主环境变量 " + sourceRef);
            }
            return;
        }
        if (check.isIncludeInline() && !StringUtils.hasText(envVar.getValueEncrypted())) {
            issues.add(agentLabel + " envVar " + envKey + " 使用 INLINE，但未保存值");
        }
    }

    private void validateRequirements(
            String agentLabel,
            String dependencyType,
            String dependencyCode,
            String requirementsJson,
            Set<String> availableKeys,
            List<String> issues
    ) {
        for (RuntimeEnvRequirementView requirement : parseRequirements(requirementsJson)) {
            if (!requirement.required()) {
                continue;
            }
            String key = normalizeKey(requirement.key());
            if (key == null || availableKeys.contains(key)) {
                continue;
            }
            issues.add(agentLabel + " 缺少必填运行时环境变量 " + key + "，来源 " + dependencyType + "=" + dependencyCode);
        }
    }

    private List<RuntimeEnvRequirementView> parseRequirements(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<RuntimeEnvRequirementView> requirements = objectMapper.readValue(json, REQUIREMENTS_TYPE);
            return requirements == null ? List.of() : requirements;
        } catch (Exception exception) {
            log.warn("{} failed to parse runtimeEnvRequirements json: {}", LOG_PREFIX, exception.getMessage());
            return List.of();
        }
    }

    private String agentLabel(AgentProfileVersion version) {
        String profileCode = version.getProfile() == null ? String.valueOf(version.getId()) : version.getProfile().getCode();
        return "Agent[" + profileCode + "#v" + version.getVersionNumber() + "]";
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveExternalValue(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String trimmed = key.trim();
        String processEnvValue = System.getenv(trimmed);
        if (StringUtils.hasText(processEnvValue)) {
            return processEnvValue;
        }
        return environment.getProperty(trimmed);
    }

    record StartupCheckReport(int checkedAgents, List<String> issues) {
        int issueCount() {
            return issues == null ? 0 : issues.size();
        }
    }
}
