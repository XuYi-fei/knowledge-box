package com.knowledgebox.service.chat;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class AgentExecutionTraceContext {

    private final String traceId;
    private final String sessionCode;
    private final int attemptNo;
    private final AtomicInteger sequenceCounter;
    private final AtomicInteger toolCallCounter = new AtomicInteger(0);
    private final Map<String, Long> spanRecordIds = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> spanStartedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> backendSpanRecordIds = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> backendSpanStartedAt = new ConcurrentHashMap<>();
    private final Map<String, String> toolSpanIdsByToolCallId = new ConcurrentHashMap<>();
    private final Deque<String> activeSpans = new ArrayDeque<>();
    private final Deque<String> activeBackendSpans = new ArrayDeque<>();
    private final String requestSpanId;
    private volatile String answerStreamSpanId;

    AgentExecutionTraceContext(String traceId, String sessionCode, int attemptNo, int sequenceSeed, String requestSpanId) {
        this.traceId = traceId;
        this.sessionCode = sessionCode;
        this.attemptNo = attemptNo;
        this.sequenceCounter = new AtomicInteger(Math.max(sequenceSeed, 0));
        this.requestSpanId = requestSpanId;
    }

    String traceId() {
        return traceId;
    }

    String sessionCode() {
        return sessionCode;
    }

    int attemptNo() {
        return attemptNo;
    }

    int nextSequenceNo() {
        return sequenceCounter.incrementAndGet();
    }

    int nextToolCallIndex() {
        return toolCallCounter.incrementAndGet();
    }

    String requestSpanId() {
        return requestSpanId;
    }

    String answerStreamSpanId() {
        return answerStreamSpanId;
    }

    void setAnswerStreamSpanId(String answerStreamSpanId) {
        this.answerStreamSpanId = answerStreamSpanId;
    }

    void bindSpanRecord(String spanId, Long recordId, OffsetDateTime startedAt) {
        spanRecordIds.put(spanId, recordId);
        spanStartedAt.put(spanId, startedAt);
        synchronized (activeSpans) {
            activeSpans.push(spanId);
        }
    }

    Long spanRecordId(String spanId) {
        return spanRecordIds.get(spanId);
    }

    OffsetDateTime spanStartedAt(String spanId) {
        return spanStartedAt.get(spanId);
    }

    void bindBackendSpanRecord(String callId, Long recordId, OffsetDateTime startedAt) {
        backendSpanRecordIds.put(callId, recordId);
        backendSpanStartedAt.put(callId, startedAt);
        synchronized (activeBackendSpans) {
            activeBackendSpans.push(callId);
        }
    }

    Long backendSpanRecordId(String callId) {
        return backendSpanRecordIds.get(callId);
    }

    OffsetDateTime backendSpanStartedAt(String callId) {
        return backendSpanStartedAt.get(callId);
    }

    void markBackendSpanClosed(String callId) {
        synchronized (activeBackendSpans) {
            activeBackendSpans.remove(callId);
        }
    }

    Deque<String> snapshotActiveBackendSpans() {
        synchronized (activeBackendSpans) {
            return new ArrayDeque<>(activeBackendSpans);
        }
    }

    String currentActiveBackendSpanId() {
        synchronized (activeBackendSpans) {
            return activeBackendSpans.peek();
        }
    }

    void markSpanClosed(String spanId) {
        synchronized (activeSpans) {
            activeSpans.remove(spanId);
        }
    }

    Deque<String> snapshotActiveSpans() {
        synchronized (activeSpans) {
            return new ArrayDeque<>(activeSpans);
        }
    }

    void bindToolSpan(String toolCallId, String spanId) {
        if (toolCallId != null && !toolCallId.isBlank()) {
            toolSpanIdsByToolCallId.put(toolCallId, spanId);
        }
    }

    String findToolSpan(String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return null;
        }
        return toolSpanIdsByToolCallId.get(toolCallId);
    }

    String removeToolSpan(String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return null;
        }
        return toolSpanIdsByToolCallId.remove(toolCallId);
    }
}
