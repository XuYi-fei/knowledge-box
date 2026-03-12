package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version_tool_binding")
public class AgentProfileVersionToolBinding extends BaseEntity {

    @Column(name = "profile_version_id", nullable = false)
    private Long profileVersionId;

    @Column(name = "tool_code", nullable = false, length = 64)
    private String toolCode;

    public Long getProfileVersionId() {
        return profileVersionId;
    }

    public void setProfileVersionId(Long profileVersionId) {
        this.profileVersionId = profileVersionId;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }
}
