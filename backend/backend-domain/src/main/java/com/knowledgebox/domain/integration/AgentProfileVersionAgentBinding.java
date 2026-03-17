package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version_agent_binding")
public class AgentProfileVersionAgentBinding extends BaseEntity {

    @Column(name = "parent_profile_version_id", nullable = false)
    private Long parentProfileVersionId;

    @Column(name = "child_profile_version_id", nullable = false)
    private Long childProfileVersionId;

    public Long getParentProfileVersionId() {
        return parentProfileVersionId;
    }

    public void setParentProfileVersionId(Long parentProfileVersionId) {
        this.parentProfileVersionId = parentProfileVersionId;
    }

    public Long getChildProfileVersionId() {
        return childProfileVersionId;
    }

    public void setChildProfileVersionId(Long childProfileVersionId) {
        this.childProfileVersionId = childProfileVersionId;
    }
}
