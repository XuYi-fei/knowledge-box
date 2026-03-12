package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcp_server")
public class McpServerConfig extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 32)
    private String transportType;

    @Column(nullable = false, length = 256)
    private String target;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String capabilitiesJson = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String headersEncryptedJson = "{}";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String queryParamsJson = "{}";

    @Column
    private Long timeoutMs;

    @Column
    private Long initializationTimeoutMs;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public String getHeadersEncryptedJson() {
        return headersEncryptedJson;
    }

    public void setHeadersEncryptedJson(String headersEncryptedJson) {
        this.headersEncryptedJson = headersEncryptedJson;
    }

    public String getQueryParamsJson() {
        return queryParamsJson;
    }

    public void setQueryParamsJson(String queryParamsJson) {
        this.queryParamsJson = queryParamsJson;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Long getInitializationTimeoutMs() {
        return initializationTimeoutMs;
    }

    public void setInitializationTimeoutMs(Long initializationTimeoutMs) {
        this.initializationTimeoutMs = initializationTimeoutMs;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
