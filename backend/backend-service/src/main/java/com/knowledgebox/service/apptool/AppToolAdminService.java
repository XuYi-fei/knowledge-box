package com.knowledgebox.service.apptool;

import com.knowledgebox.api.AppToolDefinitionView;
import com.knowledgebox.api.AppToolExecutionLogPageView;
import com.knowledgebox.api.AppToolExecutionLogView;
import com.knowledgebox.api.CreateAppToolDefinitionRequest;
import com.knowledgebox.api.UpdateAppToolDefinitionRequest;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.apptool.AppToolDefinition;
import com.knowledgebox.domain.apptool.AppToolExecutionLog;
import com.knowledgebox.domain.apptool.AppToolExecutionMode;
import com.knowledgebox.domain.apptool.AppToolRateLimitScope;
import com.knowledgebox.repository.AppToolDefinitionRepository;
import com.knowledgebox.repository.AppToolExecutionLogRepository;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AppToolAdminService {

    private static final String DEFAULT_RENDERER = "text-workbench";
    private static final Set<String> CLIENT_HANDLER_CODES = Set.of(
            "base64-encode",
            "base64-decode",
            "url-encode",
            "url-decode",
            "sha256-digest",
            "json-format",
            "json-minify",
            "timestamp-convert"
    );

    private final AppToolDefinitionRepository definitionRepository;
    private final AppToolExecutionLogRepository executionLogRepository;
    private final AppToolSchemaSupport schemaSupport;
    private final AppToolExecutorRegistry executorRegistry;
    public AppToolAdminService(
            AppToolDefinitionRepository definitionRepository,
            AppToolExecutionLogRepository executionLogRepository,
            AppToolSchemaSupport schemaSupport,
            AppToolExecutorRegistry executorRegistry
    ) {
        this.definitionRepository = definitionRepository;
        this.executionLogRepository = executionLogRepository;
        this.schemaSupport = schemaSupport;
        this.executorRegistry = executorRegistry;
    }

    @Transactional(readOnly = true)
    public List<AppToolDefinitionView> appTools() {
        return definitionRepository.findAllByOrderByDisplayOrderAscNameAscIdAsc().stream()
                .map(this::toDefinitionView)
                .toList();
    }

    @Transactional
    public AppToolDefinitionView create(CreateAppToolDefinitionRequest request) {
        String code = schemaSupport.normalizeCode(request.code());
        if (definitionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("用户工具已存在: " + code);
        }
        AppToolDefinition definition = new AppToolDefinition();
        definition.setCode(code);
        apply(definition, request.name(), request.summary(), request.descriptionMarkdown(), request.categoryCode(), request.iconKey(), request.tags(), request.displayOrder(), request.enabled(), request.executionMode(), request.rendererCode(), request.handlerCode(), request.inputSchemaJson(), request.defaultValuesJson(), request.resultSchemaJson(), request.serverConfigJson(), request.timeoutMs(), request.rateLimitScope(), request.rateLimitMaxRequests(), request.rateLimitWindowSeconds(), request.auditEnabled(), request.payloadLimitBytes());
        return toDefinitionView(definitionRepository.save(definition));
    }

    @Transactional
    public AppToolDefinitionView update(String code, UpdateAppToolDefinitionRequest request) {
        AppToolDefinition definition = require(code);
        apply(definition, request.name(), request.summary(), request.descriptionMarkdown(), request.categoryCode(), request.iconKey(), request.tags(), request.displayOrder(), request.enabled(), request.executionMode(), request.rendererCode(), request.handlerCode(), request.inputSchemaJson(), request.defaultValuesJson(), request.resultSchemaJson(), request.serverConfigJson(), request.timeoutMs(), request.rateLimitScope(), request.rateLimitMaxRequests(), request.rateLimitWindowSeconds(), request.auditEnabled(), request.payloadLimitBytes());
        return toDefinitionView(definitionRepository.save(definition));
    }

    @Transactional
    public void delete(String code) {
        definitionRepository.delete(require(code));
    }

    @Transactional(readOnly = true)
    public AppToolExecutionLogPageView executionLogs(
            @Nullable String toolCode,
            @Nullable String status,
            @Nullable Long userId,
            int page,
            int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Specification<AppToolExecutionLog> specification = Specification.where(null);
        if (StringUtils.hasText(toolCode)) {
            String normalizedToolCode = schemaSupport.normalizeCode(toolCode);
            specification = specification.and((root, query, builder) -> builder.equal(root.get("toolCode"), normalizedToolCode));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status").as(String.class), status.trim().toUpperCase(Locale.ROOT)));
        }
        if (userId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("userId"), userId));
        }
        Page<AppToolExecutionLog> result = executionLogRepository.findAll(
                specification,
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        return new AppToolExecutionLogPageView(result.getContent().stream().map(this::toExecutionLogView).toList(), result.getTotalElements(), safePage, safePageSize);
    }

    public AppToolDefinition require(String code) {
        return definitionRepository.findByCode(schemaSupport.normalizeCode(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APP_TOOL_NOT_FOUND", "用户工具不存在: " + code));
    }

    private void apply(
            AppToolDefinition definition,
            String name,
            String summary,
            String descriptionMarkdown,
            String categoryCode,
            String iconKey,
            List<String> tags,
            Integer displayOrder,
            boolean enabled,
            String executionMode,
            String rendererCode,
            String handlerCode,
            String inputSchemaJson,
            String defaultValuesJson,
            String resultSchemaJson,
            String serverConfigJson,
            Integer timeoutMs,
            String rateLimitScope,
            Integer rateLimitMaxRequests,
            Integer rateLimitWindowSeconds,
            boolean auditEnabled,
            Integer payloadLimitBytes
    ) {
        AppToolExecutionMode resolvedExecutionMode = parseExecutionMode(executionMode);
        AppToolRateLimitScope resolvedRateLimitScope = parseRateLimitScope(rateLimitScope);
        String normalizedRendererCode = schemaSupport.normalizeCodeLike(rendererCode, "渲染器编码", 64);
        if (!DEFAULT_RENDERER.equals(normalizedRendererCode)) {
            throw new IllegalArgumentException("当前仅支持渲染器: " + DEFAULT_RENDERER);
        }
        String normalizedHandlerCode = schemaSupport.normalizeCodeLike(handlerCode, "处理器编码", 64);
        if (resolvedExecutionMode == AppToolExecutionMode.SERVER && !executorRegistry.supports(normalizedHandlerCode)) {
            throw new IllegalArgumentException("未找到可执行的后端处理器: " + normalizedHandlerCode);
        }
        if (resolvedExecutionMode == AppToolExecutionMode.CLIENT && !CLIENT_HANDLER_CODES.contains(normalizedHandlerCode)) {
            throw new IllegalArgumentException("纯前端工具仅支持内置处理器: " + normalizedHandlerCode);
        }
        if (resolvedExecutionMode == AppToolExecutionMode.CLIENT && (resolvedRateLimitScope != AppToolRateLimitScope.NONE || auditEnabled)) {
            throw new IllegalArgumentException("纯前端工具不能启用后端限流或审计日志");
        }
        String normalizedInputSchemaJson = schemaSupport.normalizeJsonObject(inputSchemaJson, "输入 Schema", "{}");
        schemaSupport.validateInputSchema(normalizedInputSchemaJson);
        String normalizedDefaultValuesJson = schemaSupport.normalizeJsonObject(defaultValuesJson, "默认值 JSON", "{}");
        String normalizedResultSchemaJson = schemaSupport.normalizeJsonObject(resultSchemaJson, "结果 Schema", "{}");
        String normalizedServerConfigJson = schemaSupport.normalizeJsonObject(serverConfigJson, "服务端配置 JSON", "{}");
        if (resolvedExecutionMode == AppToolExecutionMode.SERVER && (payloadLimitBytes == null || payloadLimitBytes <= 0)) {
            throw new IllegalArgumentException("后端工具必须配置 payloadLimitBytes");
        }
        if (resolvedExecutionMode == AppToolExecutionMode.SERVER && resolvedRateLimitScope != AppToolRateLimitScope.NONE) {
            if (rateLimitMaxRequests == null || rateLimitMaxRequests <= 0 || rateLimitWindowSeconds == null || rateLimitWindowSeconds <= 0) {
                throw new IllegalArgumentException("启用限流时必须同时配置请求次数和时间窗口");
            }
        }

        definition.setName(schemaSupport.normalizeText(name, "名称", 128));
        definition.setSummary(schemaSupport.normalizeText(summary, "简介", 256));
        definition.setDescriptionMarkdown(StringUtils.hasText(descriptionMarkdown) ? descriptionMarkdown.trim() : "");
        definition.setCategoryCode(schemaSupport.normalizeCodeLike(categoryCode, "分类编码", 64));
        definition.setIconKey(schemaSupport.normalizeCodeLike(iconKey, "图标编码", 64));
        definition.setTagsJson(schemaSupport.tagsToJson(tags));
        definition.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
        definition.setEnabled(enabled);
        definition.setExecutionMode(resolvedExecutionMode);
        definition.setRendererCode(normalizedRendererCode);
        definition.setHandlerCode(normalizedHandlerCode);
        definition.setInputSchemaJson(normalizedInputSchemaJson);
        definition.setDefaultValuesJson(normalizedDefaultValuesJson);
        definition.setResultSchemaJson(normalizedResultSchemaJson);
        definition.setServerConfigJson(normalizedServerConfigJson);
        definition.setTimeoutMs(timeoutMs);
        definition.setRateLimitScope(resolvedExecutionMode == AppToolExecutionMode.CLIENT ? AppToolRateLimitScope.NONE : resolvedRateLimitScope);
        definition.setRateLimitMaxRequests(resolvedExecutionMode == AppToolExecutionMode.CLIENT ? null : rateLimitMaxRequests);
        definition.setRateLimitWindowSeconds(resolvedExecutionMode == AppToolExecutionMode.CLIENT ? null : rateLimitWindowSeconds);
        definition.setAuditEnabled(resolvedExecutionMode == AppToolExecutionMode.SERVER && auditEnabled);
        definition.setPayloadLimitBytes(payloadLimitBytes);
    }

    private AppToolDefinitionView toDefinitionView(AppToolDefinition definition) {
        return new AppToolDefinitionView(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getSummary(),
                definition.getDescriptionMarkdown(),
                definition.getCategoryCode(),
                definition.getIconKey(),
                schemaSupport.tagsFromJson(definition.getTagsJson()),
                definition.getDisplayOrder(),
                Boolean.TRUE.equals(definition.getEnabled()),
                definition.getExecutionMode().name(),
                definition.getRendererCode(),
                definition.getHandlerCode(),
                definition.getInputSchemaJson(),
                definition.getDefaultValuesJson(),
                definition.getResultSchemaJson(),
                definition.getServerConfigJson(),
                definition.getTimeoutMs(),
                definition.getRateLimitScope().name(),
                definition.getRateLimitMaxRequests(),
                definition.getRateLimitWindowSeconds(),
                Boolean.TRUE.equals(definition.getAuditEnabled()),
                definition.getPayloadLimitBytes(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    private AppToolExecutionLogView toExecutionLogView(AppToolExecutionLog log) {
        return new AppToolExecutionLogView(
                log.getExecutionId(),
                log.getToolCode(),
                log.getUserId(),
                log.getStatus().name(),
                log.getDurationMs(),
                log.getRequestSummaryJson(),
                log.getResponseSummaryJson(),
                log.getErrorCode(),
                log.getErrorMessage(),
                log.getClientIpMasked(),
                log.getCreatedAt()
        );
    }

    private AppToolExecutionMode parseExecutionMode(String executionMode) {
        try {
            return AppToolExecutionMode.valueOf(schemaSupport.normalizeText(executionMode, "执行模式", 16).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的执行模式: " + executionMode, exception);
        }
    }

    private AppToolRateLimitScope parseRateLimitScope(String rateLimitScope) {
        try {
            return AppToolRateLimitScope.valueOf(schemaSupport.normalizeText(rateLimitScope, "限流范围", 16).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的限流范围: " + rateLimitScope, exception);
        }
    }
}
