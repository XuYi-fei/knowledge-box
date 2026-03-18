package com.knowledgebox.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.CreateMcpServerRequest;
import com.knowledgebox.api.CreateToolDefinitionRequest;
import com.knowledgebox.api.McpServerView;
import com.knowledgebox.api.RuntimeEnvRequirementView;
import com.knowledgebox.api.SkillBindingView;
import com.knowledgebox.api.ToolDefinitionView;
import com.knowledgebox.api.UpdateMcpServerRequest;
import com.knowledgebox.api.UpdateSkillBindingRequest;
import com.knowledgebox.api.UpdateToolDefinitionRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.integration.McpServerConfig;
import com.knowledgebox.domain.integration.SkillBinding;
import com.knowledgebox.domain.integration.ToolDefinition;
import com.knowledgebox.repository.AgentProfileVersionMcpBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionSkillBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionToolBindingRepository;
import com.knowledgebox.repository.McpServerConfigRepository;
import com.knowledgebox.repository.SkillBindingRepository;
import com.knowledgebox.repository.ToolDefinitionRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import jakarta.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IntegrationAdminService {

    private static final String MCP_TRANSPORT_SSE = "sse";
    private static final String MASKED_SECRET = "********";

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final McpServerConfigRepository mcpServerConfigRepository;
    private final SkillBindingRepository skillBindingRepository;
    private final AgentProfileVersionToolBindingRepository toolBindingRepository;
    private final AgentProfileVersionMcpBindingRepository mcpBindingRepository;
    private final AgentProfileVersionSkillBindingRepository skillProfileBindingRepository;
    private final ToolRuntimeFactoryService toolRuntimeFactoryService;
    private final SkillPackageStorageService skillPackageStorageService;
    private final IntegrationSecretCipherService secretCipherService;
    private final ObjectMapper objectMapper;

    public IntegrationAdminService(
            ToolDefinitionRepository toolDefinitionRepository,
            McpServerConfigRepository mcpServerConfigRepository,
            SkillBindingRepository skillBindingRepository,
            AgentProfileVersionToolBindingRepository toolBindingRepository,
            AgentProfileVersionMcpBindingRepository mcpBindingRepository,
            AgentProfileVersionSkillBindingRepository skillProfileBindingRepository,
            ToolRuntimeFactoryService toolRuntimeFactoryService,
            SkillPackageStorageService skillPackageStorageService,
            IntegrationSecretCipherService secretCipherService,
            ObjectMapper objectMapper
    ) {
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.mcpServerConfigRepository = mcpServerConfigRepository;
        this.skillBindingRepository = skillBindingRepository;
        this.toolBindingRepository = toolBindingRepository;
        this.mcpBindingRepository = mcpBindingRepository;
        this.skillProfileBindingRepository = skillProfileBindingRepository;
        this.toolRuntimeFactoryService = toolRuntimeFactoryService;
        this.skillPackageStorageService = skillPackageStorageService;
        this.secretCipherService = secretCipherService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ToolDefinitionView> tools() {
        return toolDefinitionRepository.findAll().stream()
                .map(this::toToolView)
                .toList();
    }

    @Transactional
    public ToolDefinitionView createTool(CreateToolDefinitionRequest request) {
        String code = normalizeCode(request.code());
        if (toolDefinitionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Tool already exists: " + code);
        }
        toolRuntimeFactoryService.validate(request.className(), request.beanName());

        ToolDefinition definition = new ToolDefinition();
        definition.setCode(code);
        definition.setName(request.name().trim());
        definition.setClassName(request.className().trim());
        definition.setBeanName(normalizeNullable(request.beanName()));
        definition.setConfigJson(normalizeJsonObject(request.configJson(), "{}"));
        definition.setRuntimeEnvRequirementsJson(writeJson(normalizeRuntimeEnvRequirements(request.runtimeEnvRequirements())));
        definition.setEnabled(request.enabled());
        definition.setInputSchema("{}");
        definition.setEndpoint("classpath://" + definition.getClassName());
        return toToolView(toolDefinitionRepository.save(definition));
    }

    @Transactional
    public ToolDefinitionView updateTool(String code, UpdateToolDefinitionRequest request) {
        ToolDefinition definition = requireTool(code);
        toolRuntimeFactoryService.validate(request.className(), request.beanName());

        definition.setName(request.name().trim());
        definition.setClassName(request.className().trim());
        definition.setBeanName(normalizeNullable(request.beanName()));
        definition.setConfigJson(normalizeJsonObject(request.configJson(), "{}"));
        definition.setRuntimeEnvRequirementsJson(writeJson(normalizeRuntimeEnvRequirements(request.runtimeEnvRequirements())));
        definition.setEnabled(request.enabled());
        definition.setEndpoint("classpath://" + definition.getClassName());
        return toToolView(toolDefinitionRepository.save(definition));
    }

    @Transactional
    public void deleteTool(String code) {
        String normalizedCode = normalizeCode(code);
        ToolDefinition definition = requireTool(normalizedCode);
        long used = toolBindingRepository.countByToolId(definition.getId());
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INTEGRATION_IN_USE",
                    "Tool is still bound by " + used + " profile version(s): " + normalizedCode
            );
        }
        toolDefinitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public List<McpServerView> mcpServers() {
        return mcpServerConfigRepository.findAll().stream()
                .map(this::toMcpView)
                .toList();
    }

    @Transactional
    public McpServerView createMcpServer(CreateMcpServerRequest request) {
        String code = normalizeCode(request.code());
        if (mcpServerConfigRepository.existsByCode(code)) {
            throw new IllegalArgumentException("MCP server already exists: " + code);
        }
        String transport = normalizeTransportType(request.transportType());

        McpServerConfig config = new McpServerConfig();
        config.setCode(code);
        config.setTransportType(transport);
        config.setTarget(request.target().trim());
        config.setEnabled(request.enabled());
        config.setCapabilitiesJson("[]");
        config.setHeadersEncryptedJson(writeJson(encryptSecretMap(request.headers())));
        config.setQueryParamsJson(writeJson(normalizeMap(request.queryParams())));
        config.setRuntimeEnvRequirementsJson(writeJson(normalizeRuntimeEnvRequirements(request.runtimeEnvRequirements())));
        config.setTimeoutMs(request.timeoutMs());
        config.setInitializationTimeoutMs(request.initializationTimeoutMs());
        return toMcpView(mcpServerConfigRepository.save(config));
    }

    @Transactional
    public McpServerView updateMcpServer(String code, UpdateMcpServerRequest request) {
        McpServerConfig config = requireMcp(code);
        config.setTransportType(normalizeTransportType(request.transportType()));
        config.setTarget(request.target().trim());
        config.setEnabled(request.enabled());
        config.setHeadersEncryptedJson(writeJson(resolveUpdatedEncryptedHeaders(
                config.getHeadersEncryptedJson(),
                request.headers()
        )));
        if (request.queryParams() != null) {
            config.setQueryParamsJson(writeJson(normalizeMap(request.queryParams())));
        }
        config.setRuntimeEnvRequirementsJson(writeJson(normalizeRuntimeEnvRequirements(request.runtimeEnvRequirements())));
        config.setTimeoutMs(request.timeoutMs());
        config.setInitializationTimeoutMs(request.initializationTimeoutMs());
        return toMcpView(mcpServerConfigRepository.save(config));
    }

    @Transactional
    public void deleteMcpServer(String code) {
        String normalizedCode = normalizeCode(code);
        McpServerConfig config = requireMcp(normalizedCode);
        long used = mcpBindingRepository.countByMcpId(config.getId());
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INTEGRATION_IN_USE",
                    "MCP server is still bound by " + used + " profile version(s): " + normalizedCode
            );
        }
        mcpServerConfigRepository.delete(config);
    }

    @Transactional(readOnly = true)
    public List<SkillBindingView> skills() {
        return skillBindingRepository.findAll().stream()
                .map(this::toSkillView)
                .toList();
    }

    @Transactional
    public SkillBindingView uploadSkill(
            @Nullable String codeOverride,
            @Nullable String displayName,
            @Nullable String description,
            @Nullable Boolean enabled,
            @Nullable MultipartFile zipFile,
            @Nullable List<MultipartFile> folderFiles,
            @Nullable List<String> relativePaths,
            boolean replace
    ) {
        byte[] zipBytes = resolveZipBytes(codeOverride, zipFile, folderFiles, relativePaths);
        AgentSkill parsedSkill = SkillUtil.createFromZip(zipBytes, "knowledge-box-admin-upload");
        String code = StringUtils.hasText(codeOverride) ? normalizeCode(codeOverride) : normalizeCode(parsedSkill.getName());
        SkillBinding existing = skillBindingRepository.findByCode(code).orElse(null);
        if (existing != null && !replace) {
            throw new IllegalArgumentException("Skill already exists: " + code + " (set replace=true to overwrite)");
        }
        SkillPackageStorageService.StoredSkillPackage stored = skillPackageStorageService.store(code, zipBytes);

        SkillBinding binding = existing == null ? new SkillBinding() : existing;
        binding.setCode(code);
        binding.setName(StringUtils.hasText(displayName) ? displayName.trim() : parsedSkill.getName());
        binding.setDescription(StringUtils.hasText(description) ? description.trim() : parsedSkill.getDescription());
        binding.setPromptTemplate(parsedSkill.getSkillContent());
        binding.setSourceType("UPLOAD");
        binding.setOssObjectKey(stored.objectKey());
        binding.setChecksumMd5(stored.checksumMd5());
        binding.setRuntimeEnvRequirementsJson("[]");
        binding.setEnabled(enabled != null ? enabled : existing == null || Boolean.TRUE.equals(existing.getEnabled()));
        return toSkillView(skillBindingRepository.save(binding));
    }

    @Transactional
    public SkillBindingView updateSkill(String code, UpdateSkillBindingRequest request) {
        SkillBinding binding = requireSkill(code);
        binding.setName(request.name().trim());
        binding.setDescription(normalizeNullable(request.description()));
        binding.setRuntimeEnvRequirementsJson(writeJson(normalizeRuntimeEnvRequirements(request.runtimeEnvRequirements())));
        binding.setEnabled(request.enabled());
        return toSkillView(skillBindingRepository.save(binding));
    }

    @Transactional
    public void deleteSkill(String code) {
        String normalizedCode = normalizeCode(code);
        SkillBinding binding = requireSkill(normalizedCode);
        long used = skillProfileBindingRepository.countBySkillId(binding.getId());
        if (used > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INTEGRATION_IN_USE",
                    "Skill is still bound by " + used + " profile version(s): " + normalizedCode
            );
        }
        skillBindingRepository.delete(binding);
    }

    private byte[] resolveZipBytes(
            @Nullable String codeOverride,
            @Nullable MultipartFile zipFile,
            @Nullable List<MultipartFile> folderFiles,
            @Nullable List<String> relativePaths
    ) {
        try {
            if (zipFile != null && !zipFile.isEmpty()) {
                return zipFile.getBytes();
            }
            List<MultipartFile> files = folderFiles == null ? List.of() : folderFiles.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .toList();
            if (files.isEmpty()) {
                throw new IllegalArgumentException("Either zip file or folder files are required for skill upload");
            }
            List<String> paths = relativePaths == null ? List.of() : relativePaths;
            String root = "skill-" + (StringUtils.hasText(codeOverride) ? normalizeCode(codeOverride) : "upload");
            return zipFolderFiles(files, paths, root);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare skill upload package", exception);
        }
    }

    private byte[] zipFolderFiles(List<MultipartFile> files, List<String> relativePaths, String fallbackRoot) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String relative = i < relativePaths.size() ? relativePaths.get(i) : file.getOriginalFilename();
                String entryPath = normalizeEntryPath(relative, fallbackRoot, file.getOriginalFilename());
                zipOutputStream.putNextEntry(new ZipEntry(entryPath));
                zipOutputStream.write(file.getBytes());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private String normalizeEntryPath(String relativePath, String fallbackRoot, String fileName) {
        String candidate = StringUtils.hasText(relativePath) ? relativePath : fileName;
        if (!StringUtils.hasText(candidate)) {
            throw new IllegalArgumentException("Folder upload contains empty file path");
        }
        String normalized = candidate.replace('\\', '/').replaceAll("^/+", "");
        if (!normalized.contains("/")) {
            normalized = fallbackRoot + "/" + normalized;
        }
        if (normalized.contains("../")) {
            throw new IllegalArgumentException("Folder upload path must not contain parent traversal: " + relativePath);
        }
        return normalized;
    }

    private ToolDefinition requireTool(String code) {
        return toolDefinitionRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + code));
    }

    private McpServerConfig requireMcp(String code) {
        return mcpServerConfigRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + code));
    }

    private SkillBinding requireSkill(String code) {
        return skillBindingRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + code));
    }

    private ToolDefinitionView toToolView(ToolDefinition definition) {
        return new ToolDefinitionView(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getClassName(),
                definition.getBeanName(),
                normalizeJsonObject(definition.getConfigJson(), "{}"),
                readRuntimeEnvRequirements(definition.getRuntimeEnvRequirementsJson()),
                Boolean.TRUE.equals(definition.getEnabled())
        );
    }

    private McpServerView toMcpView(McpServerConfig config) {
        Map<String, String> encrypted = readStringMap(config.getHeadersEncryptedJson());
        Map<String, String> masked = new LinkedHashMap<>();
        for (String key : encrypted.keySet()) {
            masked.put(key, MASKED_SECRET);
        }
        return new McpServerView(
                config.getId(),
                config.getCode(),
                config.getTransportType(),
                config.getTarget(),
                writeJson(masked),
                normalizeJsonObject(config.getQueryParamsJson(), "{}"),
                readRuntimeEnvRequirements(config.getRuntimeEnvRequirementsJson()),
                config.getTimeoutMs(),
                config.getInitializationTimeoutMs(),
                Boolean.TRUE.equals(config.getEnabled())
        );
    }

    private SkillBindingView toSkillView(SkillBinding binding) {
        return new SkillBindingView(
                binding.getId(),
                binding.getCode(),
                binding.getName(),
                binding.getDescription(),
                binding.getSourceType(),
                binding.getOssObjectKey(),
                binding.getChecksumMd5(),
                readRuntimeEnvRequirements(binding.getRuntimeEnvRequirementsJson()),
                Boolean.TRUE.equals(binding.getEnabled())
        );
    }

    private List<RuntimeEnvRequirementView> normalizeRuntimeEnvRequirements(List<RuntimeEnvRequirementView> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, RuntimeEnvRequirementView> normalized = new LinkedHashMap<>();
        for (RuntimeEnvRequirementView requirement : requirements) {
            if (requirement == null || !StringUtils.hasText(requirement.key())) {
                continue;
            }
            String key = requirement.key().trim().toUpperCase(Locale.ROOT);
            normalized.putIfAbsent(key, new RuntimeEnvRequirementView(
                    key,
                    requirement.required(),
                    requirement.secret(),
                    normalizeNullable(requirement.description())
            ));
        }
        return List.copyOf(normalized.values());
    }

    private List<RuntimeEnvRequirementView> readRuntimeEnvRequirements(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return normalizeRuntimeEnvRequirements(objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuntimeEnvRequirementView.class)
            ));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, String> encryptSecretMap(Map<String, String> plainSecrets) {
        Map<String, String> normalized = normalizeMap(plainSecrets);
        Map<String, String> encrypted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            encrypted.put(entry.getKey(), secretCipherService.encrypt(entry.getValue()));
        }
        return encrypted;
    }

    private Map<String, String> resolveUpdatedEncryptedHeaders(
            String existingHeadersEncryptedJson,
            @Nullable Map<String, String> incomingHeaders
    ) {
        if (incomingHeaders == null) {
            return readStringMap(existingHeadersEncryptedJson);
        }
        Map<String, String> existingEncrypted = readStringMap(existingHeadersEncryptedJson);
        Map<String, String> existingPlain = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : existingEncrypted.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            existingPlain.put(entry.getKey(), secretCipherService.decrypt(entry.getValue()));
        }
        Map<String, String> mergedPlain = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : incomingHeaders.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            String key = entry.getKey().trim();
            String incomingValue = entry.getValue().trim();
            if (MASKED_SECRET.equals(incomingValue) && existingPlain.containsKey(key)) {
                mergedPlain.put(key, existingPlain.get(key));
                continue;
            }
            mergedPlain.put(key, incomingValue);
        }
        return encryptSecretMap(mergedPlain);
    }

    private Map<String, String> normalizeMap(Map<String, String> source) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            normalized.put(entry.getKey().trim(), entry.getValue().trim());
        }
        return normalized;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize integration json", exception);
        }
    }

    private String normalizeTransportType(String transportType) {
        if (!StringUtils.hasText(transportType)) {
            throw new IllegalArgumentException("transportType is required");
        }
        String normalized = transportType.strip().toLowerCase(Locale.ROOT);
        if (!MCP_TRANSPORT_SSE.equals(normalized)) {
            throw new IllegalArgumentException("Only SSE transport is supported for MCP servers");
        }
        return normalized;
    }

    private String normalizeCode(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new IllegalArgumentException("Code is required");
        }
        String normalized = rawCode.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Code must match [a-z0-9._-]+");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeJsonObject(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("JSON must be an object");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON object");
        }
    }
}
