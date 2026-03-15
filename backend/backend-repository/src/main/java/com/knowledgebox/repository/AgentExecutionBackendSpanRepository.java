package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.AgentExecutionBackendSpan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentExecutionBackendSpanRepository extends JpaRepository<AgentExecutionBackendSpan, Long> {

    Optional<AgentExecutionBackendSpan> findByTraceIdAndCallId(String traceId, String callId);

    List<AgentExecutionBackendSpan> findByTraceIdOrderBySequenceNoAscIdAsc(String traceId);

    @Query("select coalesce(max(span.sequenceNo), 0) from AgentExecutionBackendSpan span where span.traceId = :traceId")
    int findMaxSequenceNoByTraceId(@Param("traceId") String traceId);
}
