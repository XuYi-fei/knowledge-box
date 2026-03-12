package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionMcpBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileVersionMcpBindingRepository extends JpaRepository<AgentProfileVersionMcpBinding, Long> {

    List<AgentProfileVersionMcpBinding> findByProfileVersionId(Long profileVersionId);

    long countByMcpCode(String mcpCode);

    void deleteByProfileVersionId(Long profileVersionId);
}
