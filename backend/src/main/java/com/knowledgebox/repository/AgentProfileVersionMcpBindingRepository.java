package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileVersionMcpBindingRepository extends JpaRepository<AgentProfileVersionMcpBinding, Long> {

    List<AgentProfileVersionMcpBinding> findByProfileVersionId(Long profileVersionId);

    long countByMcpId(Long mcpId);

    void deleteByProfileVersionId(Long profileVersionId);
}
