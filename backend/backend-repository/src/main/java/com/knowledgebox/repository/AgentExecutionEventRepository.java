package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.AgentExecutionEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentExecutionEventRepository extends JpaRepository<AgentExecutionEvent, Long> {

    List<AgentExecutionEvent> findByTraceIdOrderBySequenceNoAscIdAsc(String traceId);

    @Query("select coalesce(max(event.sequenceNo), 0) from AgentExecutionEvent event where event.traceId = :traceId")
    int findMaxSequenceNoByTraceId(@Param("traceId") String traceId);

    long deleteByTraceId(String traceId);

    long deleteByOccurredAtBefore(OffsetDateTime cutoff);
}
