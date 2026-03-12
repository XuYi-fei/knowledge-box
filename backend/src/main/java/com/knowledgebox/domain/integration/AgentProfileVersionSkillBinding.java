package com.knowledgebox.domain.integration;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_profile_version_skill_binding")
public class AgentProfileVersionSkillBinding extends BaseEntity {

    @Column(name = "profile_version_id", nullable = false)
    private Long profileVersionId;

    @Column(name = "skill_code", nullable = false, length = 64)
    private String skillCode;

    public Long getProfileVersionId() {
        return profileVersionId;
    }

    public void setProfileVersionId(Long profileVersionId) {
        this.profileVersionId = profileVersionId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }
}
