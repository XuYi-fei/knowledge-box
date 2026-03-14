package com.knowledgebox.domain.chat;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_execution_trace")
public class AgentExecutionTrace extends BaseEntity {

    @Column(name = "trace_id", nullable = false, length = 64, unique = true)
    private String traceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_code", nullable = false, length = 64)
    private String sessionCode;

    @Column(name = "assistant_message_code", nullable = false, length = 64, unique = true)
    private String assistantMessageCode;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(name = "profile_code", nullable = false, length = 64)
    private String profileCode;

    @Column(name = "chat_model_code", nullable = false, length = 64)
    private String chatModelCode;

    @Column(name = "request_query_masked", nullable = false, columnDefinition = "TEXT")
    private String requestQueryMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentExecutionStatus status = AgentExecutionStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "root_span_id", nullable = false, length = 64)
    private String rootSpanId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getAssistantMessageCode() {
        return assistantMessageCode;
    }

    public void setAssistantMessageCode(String assistantMessageCode) {
        this.assistantMessageCode = assistantMessageCode;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String profileCode) {
        this.profileCode = profileCode;
    }

    public String getChatModelCode() {
        return chatModelCode;
    }

    public void setChatModelCode(String chatModelCode) {
        this.chatModelCode = chatModelCode;
    }

    public String getRequestQueryMasked() {
        return requestQueryMasked;
    }

    public void setRequestQueryMasked(String requestQueryMasked) {
        this.requestQueryMasked = requestQueryMasked;
    }

    public AgentExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(AgentExecutionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRootSpanId() {
        return rootSpanId;
    }

    public void setRootSpanId(String rootSpanId) {
        this.rootSpanId = rootSpanId;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
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

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }
}
