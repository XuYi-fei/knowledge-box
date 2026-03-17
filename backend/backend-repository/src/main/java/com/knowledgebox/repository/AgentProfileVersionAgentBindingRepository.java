package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionAgentBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProfileVersionAgentBindingRepository extends JpaRepository<AgentProfileVersionAgentBinding, Long> {

    List<AgentProfileVersionAgentBinding> findByParentProfileVersionId(Long parentProfileVersionId);

    List<AgentProfileVersionAgentBinding> findByChildProfileVersionId(Long childProfileVersionId);

    boolean existsByParentProfileVersionId(Long parentProfileVersionId);

    boolean existsByChildProfileVersionId(Long childProfileVersionId);

    @Modifying
    @Query("delete from AgentProfileVersionAgentBinding binding where binding.parentProfileVersionId = :profileVersionId")
    int deleteByParentProfileVersionId(@Param("profileVersionId") Long profileVersionId);
}
