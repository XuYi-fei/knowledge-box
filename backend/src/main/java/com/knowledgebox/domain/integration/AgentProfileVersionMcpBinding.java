package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version_mcp_binding")
public class AgentProfileVersionMcpBinding extends BaseEntity {

    @Column(name = "profile_version_id", nullable = false)
    private Long profileVersionId;

    @Column(name = "mcp_id", nullable = false)
    private Long mcpId;

    @Column(name = "enable_tools_json", nullable = false, columnDefinition = "TEXT")
    private String enableToolsJson = "[]";

    @Column(name = "disable_tools_json", nullable = false, columnDefinition = "TEXT")
    private String disableToolsJson = "[]";

    public Long getProfileVersionId() {
        return profileVersionId;
    }

    public void setProfileVersionId(Long profileVersionId) {
        this.profileVersionId = profileVersionId;
    }

    public Long getMcpId() {
        return mcpId;
    }

    public void setMcpId(Long mcpId) {
        this.mcpId = mcpId;
    }

    public String getEnableToolsJson() {
        return enableToolsJson;
    }

    public void setEnableToolsJson(String enableToolsJson) {
        this.enableToolsJson = enableToolsJson;
    }

    public String getDisableToolsJson() {
        return disableToolsJson;
    }

    public void setDisableToolsJson(String disableToolsJson) {
        this.disableToolsJson = disableToolsJson;
    }
}
