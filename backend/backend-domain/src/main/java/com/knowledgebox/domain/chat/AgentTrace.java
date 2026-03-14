package com.knowledgebox.domain.chat;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_trace")
public class AgentTrace extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String traceCode;

    @Column(nullable = false, length = 64)
    private String sessionCode;

    @Column(nullable = false, length = 64)
    private String stage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    public String getTraceCode() {
        return traceCode;
    }

    public void setTraceCode(String traceCode) {
        this.traceCode = traceCode;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}

