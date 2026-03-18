package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill_binding")
public class SkillBinding extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 32)
    private String sourceType = "UPLOAD";

    @Column(length = 512)
    private String ossObjectKey;

    @Column(length = 64)
    private String checksumMd5;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String runtimeEnvRequirementsJson = "[]";

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

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

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getOssObjectKey() {
        return ossObjectKey;
    }

    public void setOssObjectKey(String ossObjectKey) {
        this.ossObjectKey = ossObjectKey;
    }

    public String getChecksumMd5() {
        return checksumMd5;
    }

    public void setChecksumMd5(String checksumMd5) {
        this.checksumMd5 = checksumMd5;
    }

    public String getRuntimeEnvRequirementsJson() {
        return runtimeEnvRequirementsJson;
    }

    public void setRuntimeEnvRequirementsJson(String runtimeEnvRequirementsJson) {
        this.runtimeEnvRequirementsJson = runtimeEnvRequirementsJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
