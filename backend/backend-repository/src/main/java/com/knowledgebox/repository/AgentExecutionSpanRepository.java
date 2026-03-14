package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.AgentExecutionSpan;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentExecutionSpanRepository extends JpaRepository<AgentExecutionSpan, Long> {

    Optional<AgentExecutionSpan> findByTraceIdAndSpanId(String traceId, String spanId);

    List<AgentExecutionSpan> findByTraceIdOrderBySequenceNoAscIdAsc(String traceId);

    @Query("select coalesce(max(span.sequenceNo), 0) from AgentExecutionSpan span where span.traceId = :traceId")
    int findMaxSequenceNoByTraceId(@Param("traceId") String traceId);

    long deleteByTraceId(String traceId);

    long deleteByEndedAtBefore(OffsetDateTime cutoff);
}
