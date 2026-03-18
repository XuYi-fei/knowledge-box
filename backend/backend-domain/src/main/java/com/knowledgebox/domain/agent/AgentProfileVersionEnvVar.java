package com.knowledgebox.domain.agent;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version_env_var")
public class AgentProfileVersionEnvVar extends BaseEntity {

    @Column(nullable = false)
    private Long profileVersionId;

    @Column(nullable = false, length = 128)
    private String envKey;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean secret = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRuntimeEnvValueSource valueSource = AgentRuntimeEnvValueSource.INLINE;

    @Column(columnDefinition = "TEXT")
    private String valueEncrypted;

    @Column(length = 255)
    private String sourceRef;

    public Long getProfileVersionId() {
        return profileVersionId;
    }

    public void setProfileVersionId(Long profileVersionId) {
        this.profileVersionId = profileVersionId;
    }

    public String getEnvKey() {
        return envKey;
    }

    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getSecret() {
        return secret;
    }

    public void setSecret(Boolean secret) {
        this.secret = secret;
    }

    public AgentRuntimeEnvValueSource getValueSource() {
        return valueSource;
    }

    public void setValueSource(AgentRuntimeEnvValueSource valueSource) {
        this.valueSource = valueSource;
    }

    public String getValueEncrypted() {
        return valueEncrypted;
    }

    public void setValueEncrypted(String valueEncrypted) {
        this.valueEncrypted = valueEncrypted;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }
}
