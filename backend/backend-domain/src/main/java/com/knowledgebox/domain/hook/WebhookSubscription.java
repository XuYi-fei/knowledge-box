package com.knowledgebox.domain.hook;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HookEventType eventType;

    @Column(nullable = false, length = 256)
    private String targetUrl;

    @Column(nullable = false, length = 128)
    private String secretMasked;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

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

    public String getSecretMasked() {
        return secretMasked;
    }

    public void setSecretMasked(String secretMasked) {
        this.secretMasked = secretMasked;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

