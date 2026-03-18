package com.knowledgebox.repository;

import com.knowledgebox.domain.agent.AgentProfileVersionEnvVar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileVersionEnvVarRepository extends JpaRepository<AgentProfileVersionEnvVar, Long> {

    List<AgentProfileVersionEnvVar> findByProfileVersionIdOrderByEnvKeyAsc(Long profileVersionId);

    void deleteByProfileVersionId(Long profileVersionId);
}
