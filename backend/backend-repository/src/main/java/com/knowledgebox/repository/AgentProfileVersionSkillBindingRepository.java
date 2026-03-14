package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionSkillBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProfileVersionSkillBindingRepository extends JpaRepository<AgentProfileVersionSkillBinding, Long> {

    List<AgentProfileVersionSkillBinding> findByProfileVersionId(Long profileVersionId);

    long countBySkillId(Long skillId);

    @Modifying
    @Query("delete from AgentProfileVersionSkillBinding binding where binding.profileVersionId = :profileVersionId")
    int deleteByProfileVersionId(@Param("profileVersionId") Long profileVersionId);
}
