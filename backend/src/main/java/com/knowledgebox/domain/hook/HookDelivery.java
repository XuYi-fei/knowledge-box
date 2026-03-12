package com.knowledgebox.domain.hook;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "hook_delivery")
public class HookDelivery extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HookEventType eventType;

    @Column(nullable = false, length = 256)
    private String targetUrl;

    @Column(nullable = false)
    private Integer attemptCount = 0;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    public HookEventType getEventType() {
        return eventType;
    }

    public void setEventType(HookEventType eventType) {
        this.eventType = eventType;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}

