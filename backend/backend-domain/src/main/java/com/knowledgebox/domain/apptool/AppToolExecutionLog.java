package com.knowledgebox.domain.apptool;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_tool_execution_log")
public class AppToolExecutionLog extends BaseEntity {

    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    private String executionId;

    @Column(name = "tool_code", nullable = false, length = 64)
    private String toolCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppToolExecutionStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "request_summary_json", columnDefinition = "TEXT")
    private String requestSummaryJson;

    @Column(name = "response_summary_json", columnDefinition = "TEXT")
    private String responseSummaryJson;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "client_ip_masked", length = 128)
    private String clientIpMasked;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AppToolExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(AppToolExecutionStatus status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRequestSummaryJson() {
        return requestSummaryJson;
    }

    public void setRequestSummaryJson(String requestSummaryJson) {
        this.requestSummaryJson = requestSummaryJson;
    }

    public String getResponseSummaryJson() {
        return responseSummaryJson;
    }

    public void setResponseSummaryJson(String responseSummaryJson) {
        this.responseSummaryJson = responseSummaryJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getClientIpMasked() {
        return clientIpMasked;
    }

    public void setClientIpMasked(String clientIpMasked) {
        this.clientIpMasked = clientIpMasked;
    }
}
