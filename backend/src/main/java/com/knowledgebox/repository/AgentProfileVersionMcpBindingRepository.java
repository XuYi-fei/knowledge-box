package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProfileVersionMcpBindingRepository extends JpaRepository<AgentProfileVersionMcpBinding, Long> {

    List<AgentProfileVersionMcpBinding> findByProfileVersionId(Long profileVersionId);

    long countByMcpId(Long mcpId);

    @Modifying
    @Query("delete from AgentProfileVersionMcpBinding binding where binding.profileVersionId = :profileVersionId")
    int deleteByProfileVersionId(@Param("profileVersionId") Long profileVersionId);
}
