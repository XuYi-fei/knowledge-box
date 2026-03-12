package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.AgentTrace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTraceRepository extends JpaRepository<AgentTrace, Long> {
}
