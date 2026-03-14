package com.knowledgebox.domain.agent;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version")
public class AgentProfileVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private AgentProfile profile;

    @Column(nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProfileStatus status = ProfileStatus.DRAFT;

    @Column(nullable = false, length = 64)
    private String chatModel;

    @Column(nullable = false, length = 64)
    private String routingModel;

    @Column(nullable = false, length = 64)
    private String embeddingModel;

    @Column(length = 64)
    private String rerankModel;

    @Column(nullable = false)
    private Double temperature = 0.2D;

    @Column(name = "retrieval_top_k", nullable = false)
    private Integer retrievalTopK = 6;

    @Column(nullable = false)
    private Integer reasoningBudget = 1;

    @Column(nullable = false)
    private Boolean published = Boolean.FALSE;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String toolBindings = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mcpBindings = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String skillBindings = "[]";

    public AgentProfile getProfile() {
        return profile;
    }

    public void setProfile(AgentProfile profile) {
        this.profile = profile;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public ProfileStatus getStatus() {
        return status;
    }

    public void setStatus(ProfileStatus status) {
        this.status = status;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getRoutingModel() {
        return routingModel;
    }

    public void setRoutingModel(String routingModel) {
        this.routingModel = routingModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(Integer retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public Integer getReasoningBudget() {
        return reasoningBudget;
    }

    public void setReasoningBudget(Integer reasoningBudget) {
        this.reasoningBudget = reasoningBudget;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getToolBindings() {
        return toolBindings;
    }

    public void setToolBindings(String toolBindings) {
        this.toolBindings = toolBindings;
    }

    public String getMcpBindings() {
        return mcpBindings;
    }

    public void setMcpBindings(String mcpBindings) {
        this.mcpBindings = mcpBindings;
    }

    public String getSkillBindings() {
        return skillBindings;
    }

    public void setSkillBindings(String skillBindings) {
        this.skillBindings = skillBindings;
    }
}
