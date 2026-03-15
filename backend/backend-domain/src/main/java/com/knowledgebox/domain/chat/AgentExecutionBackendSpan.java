package com.knowledgebox.domain.chat;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_execution_backend_span")
public class AgentExecutionBackendSpan extends BaseEntity {

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "call_id", nullable = false, length = 64, unique = true)
    private String callId;

    @Column(name = "parent_call_id", length = 64)
    private String parentCallId;

    @Column(name = "call_name", nullable = false, length = 128)
    private String callName;

    @Column(name = "call_type", nullable = false, length = 32)
    private String callType;

    @Column(name = "service_class", length = 160)
    private String serviceClass;

    @Column(name = "method_name", length = 128)
    private String methodName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentExecutionStatus status = AgentExecutionStatus.RUNNING;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_json", columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "TEXT")
    private String outputJson;

    @Column(name = "error_json", columnDefinition = "TEXT")
    private String errorJson;

    @Column(name = "related_span_id", length = 64)
    private String relatedSpanId;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getParentCallId() {
        return parentCallId;
    }

    public void setParentCallId(String parentCallId) {
        this.parentCallId = parentCallId;
    }

    public String getCallName() {
        return callName;
    }

    public void setCallName(String callName) {
        this.callName = callName;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public AgentExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(AgentExecutionStatus status) {
        this.status = status;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
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

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
    }

    public String getRelatedSpanId() {
        return relatedSpanId;
    }

    public void setRelatedSpanId(String relatedSpanId) {
        this.relatedSpanId = relatedSpanId;
    }
}
