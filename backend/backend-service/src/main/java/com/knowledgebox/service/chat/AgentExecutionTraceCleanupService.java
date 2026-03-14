package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.repository.AgentExecutionEventRepository;
import com.knowledgebox.repository.AgentExecutionSpanRepository;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionTraceCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionTraceCleanupService.class);

    private final KnowledgeBoxProperties properties;
    private final AgentExecutionTraceRepository traceRepository;
    private final AgentExecutionSpanRepository spanRepository;
    private final AgentExecutionEventRepository eventRepository;

    public AgentExecutionTraceCleanupService(
            KnowledgeBoxProperties properties,
            AgentExecutionTraceRepository traceRepository,
            AgentExecutionSpanRepository spanRepository,
            AgentExecutionEventRepository eventRepository
    ) {
        this.properties = properties;
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
    }

    @Scheduled(fixedDelayString = "${knowledge-box.observability.agent-trace.cleanup-interval:24h}")
    @Transactional
    public void cleanupExpiredTraces() {
        if (!properties.getObservability().getAgentTrace().isEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.getObservability().getAgentTrace().getRetention());
        List<AgentExecutionTrace> expired = traceRepository.findAllByEndedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        for (AgentExecutionTrace trace : expired) {
            eventRepository.deleteByTraceId(trace.getTraceId());
            spanRepository.deleteByTraceId(trace.getTraceId());
            traceRepository.delete(trace);
        }
        log.info("Cleaned {} expired agent execution traces before {}", expired.size(), cutoff);
    }
}
