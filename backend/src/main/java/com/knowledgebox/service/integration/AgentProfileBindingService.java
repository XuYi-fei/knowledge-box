package com.knowledgebox.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentProfileVersionBindingsView;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentProfileBindingService {

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final AgentProfileVersionToolBindingRepository toolBindingRepository;
    private final AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    private final AgentProfileVersionSkillBindingRepository skillBindingRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillCatalogRepository;
    private final ObjectMapper objectMapper;

    public AgentProfileBindingService(
            AgentProfileVersionRepository agentProfileVersionRepository,
            AgentProfileVersionToolBindingRepository toolBindingRepository,
            AgentProfileVersionMcpBindingRepository mcpBindingRepository,
            AgentProfileVersionSkillBindingRepository skillBindingRepository,
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillCatalogRepository,
            ObjectMapper objectMapper
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.toolBindingRepository = toolBindingRepository;
        this.mcpBindingRepository = mcpBindingRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillCatalogRepository = skillCatalogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentProfileVersionBindingsView bindings(Long profileVersionId) {
        ensureProfileVersionExists(profileVersionId);
        List<String> toolCodes = new ArrayList<>();
        for (AgentProfileVersionToolBinding binding : toolBindingRepository.findByProfileVersionId(profileVersionId)) {
            String code = resolveToolCode(binding.getToolId());
            if (StringUtils.hasText(code)) {
                toolCodes.add(code);
            }
        }
        List<String> skillCodes = new ArrayList<>();
        for (AgentProfileVersionSkillBinding binding : skillBindingRepository.findByProfileVersionId(profileVersionId)) {
            String code = resolveSkillCode(binding.getSkillId());
            if (StringUtils.hasText(code)) {
                skillCodes.add(code);
            }
        }
        List<AgentProfileVersionMcpBindingView> mcpBindings = new ArrayList<>();
        for (AgentProfileVersionMcpBinding binding : mcpBindingRepository.findByProfileVersionId(profileVersionId)) {
            String mcpCode = resolveMcpCode(binding.getMcpId());
            if (!StringUtils.hasText(mcpCode)) {
                continue;
            }
            mcpBindings.add(new AgentProfileVersionMcpBindingView(
                    mcpCode,
                    readStringList(binding.getEnableToolsJson()),
                    readStringList(binding.getDisableToolsJson())
            ));
        }
        return new AgentProfileVersionBindingsView(profileVersionId, toolCodes, skillCodes, mcpBindings);
    }

    @Transactional
    public AgentProfileVersionBindingsView updateBindings(Long profileVersionId, UpdateAgentProfileVersionBindingsRequest request) {
        ensureProfileVersionExists(profileVersionId);

        List<String> toolCodes = normalizeCodes(request.toolCodes());
        List<String> skillCodes = normalizeCodes(request.skillCodes());
        List<AgentProfileVersionMcpBindingView> mcpBindings = request.mcpBindings() == null
                ? List.of()
                : request.mcpBindings();

        List<Long> toolIds = toolCodes.stream().map(this::requireToolId).toList();
        List<Long> skillIds = skillCodes.stream().map(this::requireSkillId).toList();

        List<AgentProfileVersionMcpBindingView> normalizedMcpBindings = new ArrayList<>();
        Set<String> seenMcpCodes = new LinkedHashSet<>();
        for (AgentProfileVersionMcpBindingView binding : mcpBindings) {
            if (binding == null || !StringUtils.hasText(binding.mcpCode())) {
                continue;
            }
            String mcpCode = binding.mcpCode().trim().toLowerCase(Locale.ROOT);
            if (seenMcpCodes.add(mcpCode)) {
                normalizedMcpBindings.add(new AgentProfileVersionMcpBindingView(
                        mcpCode,
                        normalizeCodes(binding.enableTools()),
                        normalizeCodes(binding.disableTools())
                ));
            }
        }

        toolBindingRepository.deleteByProfileVersionId(profileVersionId);
        mcpBindingRepository.deleteByProfileVersionId(profileVersionId);
        skillBindingRepository.deleteByProfileVersionId(profileVersionId);

        if (!toolCodes.isEmpty()) {
            toolBindingRepository.saveAll(toolIds.stream()
                    .map(toolId -> {
                        AgentProfileVersionToolBinding binding = new AgentProfileVersionToolBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setToolId(toolId);
                        return binding;
                    })
                    .toList());
        }

        if (!normalizedMcpBindings.isEmpty()) {
            mcpBindingRepository.saveAll(normalizedMcpBindings.stream()
                    .map(bindingView -> {
                        AgentProfileVersionMcpBinding binding = new AgentProfileVersionMcpBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setMcpId(requireMcpId(bindingView.mcpCode()));
                        binding.setEnableToolsJson(writeJson(bindingView.enableTools()));
                        binding.setDisableToolsJson(writeJson(bindingView.disableTools()));
                        return binding;
                    })
                    .toList());
        }

        if (!skillIds.isEmpty()) {
            skillBindingRepository.saveAll(skillIds.stream()
                    .map(skillId -> {
                        AgentProfileVersionSkillBinding binding = new AgentProfileVersionSkillBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setSkillId(skillId);
                        return binding;
                    })
                    .toList());
        }

        return bindings(profileVersionId);
    }

    private void ensureProfileVersionExists(Long profileVersionId) {
        if (profileVersionId == null) {
            throw new IllegalArgumentException("profileVersionId is required");
        }
        if (!agentProfileVersionRepository.existsById(profileVersionId)) {
            throw new IllegalArgumentException("Agent profile version not found: " + profileVersionId);
        }
    }

    private Long requireToolId(String code) {
        return toolDefinitionRepository.findByCode(code)
                .map(ToolDefinition::getId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + code));
    }

    private Long requireMcpId(String code) {
        return mcpServerConfigRepository.findByCode(code)
                .map(McpServerConfig::getId)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + code));
    }

    private Long requireSkillId(String code) {
        return skillCatalogRepository.findByCode(code)
                .map(SkillBinding::getId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + code));
    }

    private String resolveToolCode(Long toolId) {
        if (toolId == null) {
            return null;
        }
        return toolDefinitionRepository.findById(toolId)
                .map(ToolDefinition::getCode)
                .orElse(null);
    }

    private String resolveMcpCode(Long mcpId) {
        if (mcpId == null) {
            return null;
        }
        return mcpServerConfigRepository.findById(mcpId)
                .map(McpServerConfig::getCode)
                .orElse(null);
    }

    private String resolveSkillCode(Long skillId) {
        if (skillId == null) {
            return null;
        }
        return skillCatalogRepository.findById(skillId)
                .map(SkillBinding::getCode)
                .orElse(null);
    }

    private List<String> normalizeCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : rawCodes) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            normalized.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize profile binding json", exception);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return normalizeCodes(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception exception) {
            return List.of();
        }
    }
}
