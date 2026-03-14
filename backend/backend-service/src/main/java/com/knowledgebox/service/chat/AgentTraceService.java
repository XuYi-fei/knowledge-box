package com.knowledgebox.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.domain.chat.AgentTrace;
import com.knowledgebox.repository.AgentTraceRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    private final AgentTraceRepository agentTraceRepository;
    private final ObjectMapper objectMapper;

    public AgentTraceService(AgentTraceRepository agentTraceRepository, ObjectMapper objectMapper) {
        this.agentTraceRepository = agentTraceRepository;
        this.objectMapper = objectMapper;
    }

    public void trace(String sessionCode, String stage, Map<String, ?> payload) {
        if (sessionCode == null || sessionCode.isBlank()) {
            log.warn("Skip agent trace persistence because sessionCode is blank. stage={}", stage);
            return;
        }
        AgentTrace trace = new AgentTrace();
        trace.setTraceCode("trace-" + UUID.randomUUID());
        trace.setSessionCode(sessionCode.trim());
        trace.setStage(stage);
        trace.setPayloadJson(toJson(payload == null ? Map.of() : payload));
        agentTraceRepository.save(trace);
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize trace payload", exception);
        }
    }
}
