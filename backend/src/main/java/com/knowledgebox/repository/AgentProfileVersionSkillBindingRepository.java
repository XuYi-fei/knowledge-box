package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileVersionSkillBindingRepository extends JpaRepository<AgentProfileVersionSkillBinding, Long> {

    List<AgentProfileVersionSkillBinding> findByProfileVersionId(Long profileVersionId);

    long countBySkillCode(String skillCode);

    void deleteByProfileVersionId(Long profileVersionId);
}
