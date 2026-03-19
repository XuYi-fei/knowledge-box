package com.knowledgebox.service.chat;

import com.knowledgebox.domain.chat.AgentExecutionBackendSpan;
import com.knowledgebox.domain.chat.AgentExecutionEvent;
import com.knowledgebox.domain.chat.AgentExecutionSpan;
import com.knowledgebox.domain.chat.AgentExecutionSpanType;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.repository.AgentExecutionBackendSpanRepository;
import com.knowledgebox.repository.AgentExecutionEventRepository;
import com.knowledgebox.repository.AgentExecutionSpanRepository;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionTraceService {

    private final AgentExecutionTraceRepository traceRepository;
    private final AgentExecutionSpanRepository spanRepository;
    private final AgentExecutionEventRepository eventRepository;
    private final AgentExecutionBackendSpanRepository backendSpanRepository;
    private final AgentExecutionLogSanitizer sanitizer;

    public AgentExecutionTraceService(
            AgentExecutionTraceRepository traceRepository,
            AgentExecutionSpanRepository spanRepository,
            AgentExecutionEventRepository eventRepository,
            AgentExecutionBackendSpanRepository backendSpanRepository,
            AgentExecutionLogSanitizer sanitizer
    ) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
        this.backendSpanRepository = backendSpanRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public AgentExecutionTraceContext startOrResumeTrace(StreamTask task) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<AgentExecutionTrace> existingOptional = traceRepository.findByAssistantMessageCode(task.assistantMessageId());
        if (existingOptional.isPresent()) {
            AgentExecutionTrace existing = existingOptional.get();
            existing.setStatus(AgentExecutionStatus.RUNNING);
            existing.setEndedAt(null);
            existing.setDurationMs(null);
            existing.setErrorCode(null);
            existing.setErrorMessage(null);
            existing.setAttemptCount((existing.getAttemptCount() == null ? 0 : existing.getAttemptCount()) + 1);
            traceRepository.save(existing);
            int sequenceSeed = Math.max(
                    Math.max(
                            spanRepository.findMaxSequenceNoByTraceId(existing.getTraceId()),
                            eventRepository.findMaxSequenceNoByTraceId(existing.getTraceId())
                    ),
                    backendSpanRepository.findMaxSequenceNoByTraceId(existing.getTraceId())
            );
            AgentExecutionTraceContext context = new AgentExecutionTraceContext(
                    existing.getTraceId(),
                    existing.getSessionCode(),
                    existing.getAttemptCount(),
                    sequenceSeed,
                    existing.getRootSpanId()
            );
            if (existing.getRootSpanId() != null) {
                spanRepository.findByTraceIdAndSpanId(existing.getTraceId(), existing.getRootSpanId())
                        .ifPresent(span -> context.bindSpanRecord(existing.getRootSpanId(), span.getId(), span.getStartedAt()));
            }
            recordEvent(context, existing.getRootSpanId(), "resume.requested", Map.of(
                    "attemptNo", existing.getAttemptCount(),
                    "sessionCode", task.sessionId(),
                    "assistantMessageCode", task.assistantMessageId()
            ));
            return context;
        }

        String traceId = nextTraceId();
        String requestSpanId = nextSpanId();
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.setTraceId(traceId);
        trace.setUserId(task.userId());
        trace.setSessionCode(task.sessionId());
        trace.setAssistantMessageCode(task.assistantMessageId());
        trace.setClientMessageId(task.clientMessageId());
        trace.setProfileCode(task.profileCode());
        trace.setChatModelCode(task.chatModelCode());
        trace.setRequestQueryMasked(sanitizer.sanitizeText(task.query()));
        trace.setStatus(AgentExecutionStatus.RUNNING);
        trace.setStartedAt(now);
        trace.setRootSpanId(requestSpanId);
        trace.setAttemptCount(1);
        traceRepository.save(trace);

        AgentExecutionTraceContext context = new AgentExecutionTraceContext(traceId, task.sessionId(), 1, 0, requestSpanId);
        startSpan(
                context,
                null,
                requestSpanId,
                "chat.request",
                AgentExecutionSpanType.REQUEST,
                requestInput(task),
                Map.of("phase", "request")
        );
        return context;
    }

    @Transactional
    public void startSpan(
            AgentExecutionTraceContext context,
            String parentSpanId,
            String spanId,
            String spanName,
            AgentExecutionSpanType spanType,
            Map<String, ?> input,
            Map<String, ?> tags
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        AgentExecutionSpan span = new AgentExecutionSpan();
        span.setTraceId(context.traceId());
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setSpanName(spanName);
        span.setSpanType(spanType);
        span.setStatus(AgentExecutionStatus.RUNNING);
        span.setSequenceNo(context.nextSequenceNo());
        span.setAttemptNo(context.attemptNo());
        span.setStartedAt(now);
        span.setInputJson(sanitizeJson(input));
        span.setTagsJson(sanitizeJson(tags));
        span = spanRepository.save(span);
        context.bindSpanRecord(spanId, span.getId(), now);
    }

    @Transactional
    public void endSpan(
            AgentExecutionTraceContext context,
            String spanId,
            AgentExecutionStatus status,
            Map<String, ?> output,
            Map<String, ?> tags,
            Map<String, ?> error
    ) {
        if (spanId == null || spanId.isBlank()) {
            return;
        }
        AgentExecutionSpan span = resolveSpan(context, spanId);
        if (span == null || span.getEndedAt() != null) {
            context.markSpanClosed(spanId);
            return;
        }
        OffsetDateTime endedAt = OffsetDateTime.now();
        span.setStatus(status);
        span.setEndedAt(endedAt);
        OffsetDateTime startedAt = context.spanStartedAt(spanId);
        if (startedAt == null) {
            startedAt = span.getStartedAt();
        }
        if (startedAt != null) {
            span.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        }
        if (output != null && !output.isEmpty()) {
            span.setOutputJson(sanitizeJson(output));
        }
        if (tags != null && !tags.isEmpty()) {
            span.setTagsJson(sanitizeJson(tags));
        }
        if (error != null && !error.isEmpty()) {
            span.setErrorJson(sanitizeJson(error));
        }
        spanRepository.save(span);
        context.markSpanClosed(spanId);
    }

    @Transactional
    public void recordEvent(AgentExecutionTraceContext context, String spanId, String eventType, Map<String, ?> payload) {
        if (context == null || spanId == null || spanId.isBlank() || eventType == null || eventType.isBlank()) {
            return;
        }
        AgentExecutionEvent event = new AgentExecutionEvent();
        event.setTraceId(context.traceId());
        event.setSpanId(spanId);
        event.setEventType(eventType);
        event.setSequenceNo(context.nextSequenceNo());
        event.setOccurredAt(OffsetDateTime.now());
        event.setPayloadJson(sanitizeJson(payload == null ? Map.of() : payload));
        eventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startBackendSpan(
            AgentExecutionTraceContext context,
            String parentCallId,
            String callId,
            String callName,
            String callType,
            String serviceClass,
            String methodName,
            Map<String, ?> input,
            String relatedSpanId
    ) {
        if (context == null || callId == null || callId.isBlank()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        AgentExecutionBackendSpan span = new AgentExecutionBackendSpan();
        span.setTraceId(context.traceId());
        span.setCallId(callId);
        span.setParentCallId(parentCallId);
        span.setCallName(callName);
        span.setCallType(callType);
        span.setServiceClass(serviceClass);
        span.setMethodName(methodName);
        span.setStatus(AgentExecutionStatus.RUNNING);
        span.setSequenceNo(context.nextSequenceNo());
        span.setAttemptNo(context.attemptNo());
        span.setStartedAt(now);
        span.setInputJson(sanitizeJson(input));
        span.setRelatedSpanId(relatedSpanId);
        span = backendSpanRepository.save(span);
        context.bindBackendSpanRecord(callId, span.getId(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void endBackendSpan(
            AgentExecutionTraceContext context,
            String callId,
            AgentExecutionStatus status,
            Map<String, ?> output,
            Map<String, ?> error
    ) {
        if (context == null || callId == null || callId.isBlank()) {
            return;
        }
        AgentExecutionBackendSpan span = resolveBackendSpan(context, callId);
        if (span == null || span.getEndedAt() != null) {
            context.markBackendSpanClosed(callId);
            return;
        }
        OffsetDateTime endedAt = OffsetDateTime.now();
        span.setStatus(status);
        span.setEndedAt(endedAt);
        OffsetDateTime startedAt = context.backendSpanStartedAt(callId);
        if (startedAt == null) {
            startedAt = span.getStartedAt();
        }
        if (startedAt != null) {
            span.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        }
        if (output != null && !output.isEmpty()) {
            span.setOutputJson(sanitizeJson(output));
        }
        if (error != null && !error.isEmpty()) {
            span.setErrorJson(sanitizeJson(error));
        }
        backendSpanRepository.save(span);
        context.markBackendSpanClosed(callId);
    }

    @Transactional
    public void completeTrace(AgentExecutionTraceContext context, Map<String, ?> summary) {
        closeRemainingSpans(context, AgentExecutionStatus.COMPLETED, Map.of());
        updateTraceTerminalState(context, AgentExecutionStatus.COMPLETED, summary, null, null);
    }

    @Transactional
    public void failTrace(AgentExecutionTraceContext context, Throwable error) {
        Map<String, Object> errorPayload = errorPayload(error);
        closeRemainingSpans(context, AgentExecutionStatus.FAILED, errorPayload);
        updateTraceTerminalState(context, AgentExecutionStatus.FAILED, Map.of(), errorCode(error), sanitizer.sanitizeText(errorMessage(error)));
    }

    @Transactional
    public void cancelTrace(AgentExecutionTraceContext context) {
        cancelTrace(context, "CHAT_CANCELLED", "generation cancelled");
    }

    @Transactional
    public void cancelTrace(AgentExecutionTraceContext context, String errorCode, String message) {
        String resolvedCode = sanitizer.sanitizeText(errorCode == null || errorCode.isBlank() ? "CHAT_CANCELLED" : errorCode);
        String resolvedMessage = sanitizer.sanitizeText(message == null || message.isBlank() ? "generation cancelled" : message);
        closeRemainingSpans(context, AgentExecutionStatus.CANCELLED, Map.of("message", resolvedMessage));
        updateTraceTerminalState(context, AgentExecutionStatus.CANCELLED, Map.of(), resolvedCode, resolvedMessage);
    }

    public String nextBackendSpanIdValue() {
        return "call-" + UUID.randomUUID();
    }

    public void bindMdc(AgentExecutionTraceContext context, String spanId) {
        if (context == null) {
            return;
        }
        MDC.put("traceId", context.traceId());
        if (spanId != null && !spanId.isBlank()) {
            MDC.put("spanId", spanId);
        } else {
            MDC.remove("spanId");
        }
    }

    public void clearMdc() {
        MDC.remove("traceId");
        MDC.remove("spanId");
    }

    private void closeRemainingSpans(AgentExecutionTraceContext context, AgentExecutionStatus status, Map<String, ?> errorPayload) {
        if (context == null) {
            return;
        }
        Deque<String> activeSpans = context.snapshotActiveSpans();
        List<String> spanIds = new ArrayList<>(activeSpans);
        for (String spanId : spanIds) {
            endSpan(
                    context,
                    spanId,
                    status,
                    Map.of(),
                    Map.of(),
                    errorPayload == null || errorPayload.isEmpty() ? null : errorPayload
            );
        }
        Deque<String> activeBackendSpans = context.snapshotActiveBackendSpans();
        List<String> backendSpanIds = new ArrayList<>(activeBackendSpans);
        for (String callId : backendSpanIds) {
            endBackendSpan(
                    context,
                    callId,
                    status,
                    Map.of(),
                    errorPayload == null || errorPayload.isEmpty() ? null : errorPayload
            );
        }
    }

    private void updateTraceTerminalState(
            AgentExecutionTraceContext context,
            AgentExecutionStatus status,
            Map<String, ?> summary,
            String errorCode,
            String errorMessage
    ) {
        if (context == null) {
            return;
        }
        AgentExecutionTrace trace = traceRepository.findByTraceId(context.traceId()).orElse(null);
        if (trace == null) {
            return;
        }
        OffsetDateTime endedAt = OffsetDateTime.now();
        trace.setStatus(status);
        trace.setEndedAt(endedAt);
        if (trace.getStartedAt() != null) {
            trace.setDurationMs(Duration.between(trace.getStartedAt(), endedAt).toMillis());
        }
        trace.setErrorCode(errorCode);
        trace.setErrorMessage(errorMessage);
        if (summary != null && !summary.isEmpty()) {
            trace.setSummaryJson(sanitizeJson(summary));
        }
        traceRepository.save(trace);
    }

    private AgentExecutionSpan resolveSpan(AgentExecutionTraceContext context, String spanId) {
        Long recordId = context.spanRecordId(spanId);
        if (recordId != null) {
            return spanRepository.findById(recordId).orElse(null);
        }
        return spanRepository.findByTraceIdAndSpanId(context.traceId(), spanId).orElse(null);
    }

    private AgentExecutionBackendSpan resolveBackendSpan(AgentExecutionTraceContext context, String callId) {
        Long recordId = context.backendSpanRecordId(callId);
        if (recordId != null) {
            return backendSpanRepository.findById(recordId).orElse(null);
        }
        return backendSpanRepository.findByTraceIdAndCallId(context.traceId(), callId).orElse(null);
    }

    private Map<String, Object> errorPayload(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", errorCode(error));
        payload.put("errorMessage", errorMessage(error));
        payload.put("exceptionClass", error == null ? null : error.getClass().getName());
        return payload;
    }

    private String errorCode(Throwable error) {
        if (error == null) {
            return null;
        }
        return error.getClass().getSimpleName();
    }

    private String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return null;
        }
        return error.getMessage();
    }

    private String sanitizeJson(Object value) {
        return sanitizer.sanitizeToJson(value);
    }

    private String nextTraceId() {
        return "trace-" + UUID.randomUUID();
    }

    private String nextSpanId() {
        return "span-" + UUID.randomUUID();
    }

    public String nextSpanIdValue() {
        return nextSpanId();
    }

    private Map<String, Object> requestInput(StreamTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionCode", task.sessionId());
        payload.put("assistantMessageCode", task.assistantMessageId());
        payload.put("clientMessageId", task.clientMessageId());
        payload.put("query", task.query());
        payload.put("profileCode", task.profileCode());
        payload.put("chatModelCode", task.chatModelCode());
        return payload;
    }
}
