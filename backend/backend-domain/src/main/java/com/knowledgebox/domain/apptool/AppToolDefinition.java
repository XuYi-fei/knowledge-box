package com.knowledgebox.domain.apptool;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_tool_definition")
public class AppToolDefinition extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 256)
    private String summary;

    @Column(name = "description_markdown", nullable = false, columnDefinition = "TEXT")
    private String descriptionMarkdown;

    @Column(name = "category_code", nullable = false, length = 64)
    private String categoryCode;

    @Column(name = "icon_key", nullable = false, length = 64)
    private String iconKey;

    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson = "[]";

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 16)
    private AppToolExecutionMode executionMode = AppToolExecutionMode.CLIENT;

    @Column(name = "renderer_code", nullable = false, length = 64)
    private String rendererCode;

    @Column(name = "handler_code", nullable = false, length = 64)
    private String handlerCode;

    @Column(name = "input_schema_json", nullable = false, columnDefinition = "TEXT")
    private String inputSchemaJson = "{}";

    @Column(name = "default_values_json", nullable = false, columnDefinition = "TEXT")
    private String defaultValuesJson = "{}";

    @Column(name = "result_schema_json", nullable = false, columnDefinition = "TEXT")
    private String resultSchemaJson = "{}";

    @Column(name = "server_config_json", nullable = false, columnDefinition = "TEXT")
    private String serverConfigJson = "{}";

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_scope", nullable = false, length = 16)
    private AppToolRateLimitScope rateLimitScope = AppToolRateLimitScope.NONE;

    @Column(name = "rate_limit_max_requests")
    private Integer rateLimitMaxRequests;

    @Column(name = "rate_limit_window_seconds")
    private Integer rateLimitWindowSeconds;

    @Column(name = "audit_enabled", nullable = false)
    private Boolean auditEnabled = Boolean.FALSE;

    @Column(name = "payload_limit_bytes")
    private Integer payloadLimitBytes;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescriptionMarkdown() {
        return descriptionMarkdown;
    }

    public void setDescriptionMarkdown(String descriptionMarkdown) {
        this.descriptionMarkdown = descriptionMarkdown;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getIconKey() {
        return iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public AppToolExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(AppToolExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public String getRendererCode() {
        return rendererCode;
    }

    public void setRendererCode(String rendererCode) {
        this.rendererCode = rendererCode;
    }

    public String getHandlerCode() {
        return handlerCode;
    }

    public void setHandlerCode(String handlerCode) {
        this.handlerCode = handlerCode;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public void setInputSchemaJson(String inputSchemaJson) {
        this.inputSchemaJson = inputSchemaJson;
    }

    public String getDefaultValuesJson() {
        return defaultValuesJson;
    }

    public void setDefaultValuesJson(String defaultValuesJson) {
        this.defaultValuesJson = defaultValuesJson;
    }

    public String getResultSchemaJson() {
        return resultSchemaJson;
    }

    public void setResultSchemaJson(String resultSchemaJson) {
        this.resultSchemaJson = resultSchemaJson;
    }

    public String getServerConfigJson() {
        return serverConfigJson;
    }

    public void setServerConfigJson(String serverConfigJson) {
        this.serverConfigJson = serverConfigJson;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public AppToolRateLimitScope getRateLimitScope() {
        return rateLimitScope;
    }

    public void setRateLimitScope(AppToolRateLimitScope rateLimitScope) {
        this.rateLimitScope = rateLimitScope;
    }

    public Integer getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(Integer rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public Integer getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(Integer rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public Boolean getAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(Boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public Integer getPayloadLimitBytes() {
        return payloadLimitBytes;
    }

    public void setPayloadLimitBytes(Integer payloadLimitBytes) {
        this.payloadLimitBytes = payloadLimitBytes;
    }
}
