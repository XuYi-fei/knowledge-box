package com.knowledgebox.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.AgentProfileVersionBindingsView;
import com.knowledgebox.api.AgentProfileVersionMcpBindingView;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
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
        List<String> toolCodes = toolBindingRepository.findByProfileVersionId(profileVersionId).stream()
                .map(AgentProfileVersionToolBinding::getToolCode)
                .toList();
        List<String> skillCodes = skillBindingRepository.findByProfileVersionId(profileVersionId).stream()
                .map(AgentProfileVersionSkillBinding::getSkillCode)
                .toList();
        List<AgentProfileVersionMcpBindingView> mcpBindings = mcpBindingRepository.findByProfileVersionId(profileVersionId).stream()
                .map(binding -> new AgentProfileVersionMcpBindingView(
                        binding.getMcpCode(),
                        readStringList(binding.getEnableToolsJson()),
                        readStringList(binding.getDisableToolsJson())
                ))
                .toList();
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

        for (String code : toolCodes) {
            if (!toolDefinitionRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Tool not found: " + code);
            }
        }
        for (String code : skillCodes) {
            if (!skillCatalogRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Skill not found: " + code);
            }
        }

        List<AgentProfileVersionMcpBindingView> normalizedMcpBindings = new ArrayList<>();
        Set<String> seenMcpCodes = new LinkedHashSet<>();
        for (AgentProfileVersionMcpBindingView binding : mcpBindings) {
            if (binding == null || !StringUtils.hasText(binding.mcpCode())) {
                continue;
            }
            String mcpCode = binding.mcpCode().trim().toLowerCase(Locale.ROOT);
            if (!mcpServerConfigRepository.existsByCode(mcpCode)) {
                throw new IllegalArgumentException("MCP server not found: " + mcpCode);
            }
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
            toolBindingRepository.saveAll(toolCodes.stream()
                    .map(code -> {
                        AgentProfileVersionToolBinding binding = new AgentProfileVersionToolBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setToolCode(code);
                        return binding;
                    })
                    .toList());
        }

        if (!normalizedMcpBindings.isEmpty()) {
            mcpBindingRepository.saveAll(normalizedMcpBindings.stream()
                    .map(bindingView -> {
                        AgentProfileVersionMcpBinding binding = new AgentProfileVersionMcpBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setMcpCode(bindingView.mcpCode());
                        binding.setEnableToolsJson(writeJson(bindingView.enableTools()));
                        binding.setDisableToolsJson(writeJson(bindingView.disableTools()));
                        return binding;
                    })
                    .toList());
        }

        if (!skillCodes.isEmpty()) {
            skillBindingRepository.saveAll(skillCodes.stream()
                    .map(code -> {
                        AgentProfileVersionSkillBinding binding = new AgentProfileVersionSkillBinding();
                        binding.setProfileVersionId(profileVersionId);
                        binding.setSkillCode(code);
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
