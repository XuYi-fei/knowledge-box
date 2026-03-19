package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

public interface AgentExecutionTraceRepository extends JpaRepository<AgentExecutionTrace, Long>, JpaSpecificationExecutor<AgentExecutionTrace> {

    Optional<AgentExecutionTrace> findByAssistantMessageCode(String assistantMessageCode);

    Optional<AgentExecutionTrace> findByTraceId(String traceId);

    List<AgentExecutionTrace> findAllByEndedAtBefore(OffsetDateTime cutoff);

    boolean existsByProfileCodeAndStatus(String profileCode, AgentExecutionStatus status);

    @Modifying
    void deleteByProfileCode(String profileCode);

    long deleteByEndedAtBefore(OffsetDateTime cutoff);
}
