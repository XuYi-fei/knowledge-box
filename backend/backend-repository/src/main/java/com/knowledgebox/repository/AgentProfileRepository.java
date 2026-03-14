package com.knowledgebox.repository;

import com.knowledgebox.domain.agent.AgentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {

    Optional<AgentProfile> findByCode(String code);
}

