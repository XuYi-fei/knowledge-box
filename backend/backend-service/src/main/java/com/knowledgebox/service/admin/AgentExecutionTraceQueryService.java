package com.knowledgebox.service.admin;

import com.knowledgebox.api.AgentExecutionBackendSpanView;
import com.knowledgebox.api.AgentExecutionEventView;
import com.knowledgebox.api.AgentExecutionSpanView;
import com.knowledgebox.api.AgentExecutionTimelineItemView;
import com.knowledgebox.api.AgentExecutionTraceDetailView;
import com.knowledgebox.api.AgentExecutionTracePageView;
import com.knowledgebox.api.AgentExecutionTraceSummaryView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.AgentExecutionBackendSpan;
import com.knowledgebox.domain.chat.AgentExecutionEvent;
import com.knowledgebox.domain.chat.AgentExecutionSpan;
import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.repository.AgentExecutionBackendSpanRepository;
import com.knowledgebox.repository.AgentExecutionEventRepository;
import com.knowledgebox.repository.AgentExecutionSpanRepository;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentExecutionTraceQueryService {

    private final AgentExecutionTraceRepository traceRepository;
    private final AgentExecutionSpanRepository spanRepository;
    private final AgentExecutionEventRepository eventRepository;
    private final AgentExecutionBackendSpanRepository backendSpanRepository;

    public AgentExecutionTraceQueryService(
            AgentExecutionTraceRepository traceRepository,
            AgentExecutionSpanRepository spanRepository,
            AgentExecutionEventRepository eventRepository,
            AgentExecutionBackendSpanRepository backendSpanRepository
    ) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
        this.backendSpanRepository = backendSpanRepository;
    }

    @Transactional(readOnly = true)
    public AgentExecutionTracePageView traces(
            @Nullable String traceId,
            @Nullable String status,
            @Nullable String sessionCode,
            @Nullable Long userId,
            @Nullable String queryKeyword,
            int page,
            int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))
        );
        Specification<AgentExecutionTrace> specification = Specification.where(null);
        if (StringUtils.hasText(traceId)) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("traceId"), traceId.trim()));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status").as(String.class), status.trim()));
        }
        if (StringUtils.hasText(sessionCode)) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("sessionCode"), sessionCode.trim()));
        }
        if (userId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("userId"), userId));
        }
        if (StringUtils.hasText(queryKeyword)) {
            String keyword = "%" + queryKeyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.like(builder.lower(root.get("requestQueryMasked")), keyword));
        }
        Page<AgentExecutionTrace> tracePage = traceRepository.findAll(specification, pageable);
        return new AgentExecutionTracePageView(
                tracePage.getContent().stream().map(this::toSummaryView).toList(),
                tracePage.getTotalElements(),
                safePage,
                safePageSize
        );
    }

    @Transactional(readOnly = true)
    public AgentExecutionTraceDetailView traceDetail(String traceId) {
        AgentExecutionTrace trace = traceRepository.findByTraceId(traceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRACE_NOT_FOUND", "执行链路不存在"));
        List<AgentExecutionSpan> spans = spanRepository.findByTraceIdOrderBySequenceNoAscIdAsc(traceId);
        List<AgentExecutionEvent> events = eventRepository.findByTraceIdOrderBySequenceNoAscIdAsc(traceId);
        List<AgentExecutionBackendSpan> backendSpans = backendSpanRepository.findByTraceIdOrderBySequenceNoAscIdAsc(traceId);
        return new AgentExecutionTraceDetailView(
                toSummaryView(trace),
                buildAgentTimeline(spans, events),
                backendSpans.stream().map(this::toBackendSpanView).toList(),
                spans.stream().map(this::toSpanView).toList(),
                events.stream().map(this::toEventView).toList()
        );
    }

    public long traceCount() {
        return traceRepository.count();
    }

    private AgentExecutionTraceSummaryView toSummaryView(AgentExecutionTrace trace) {
        return new AgentExecutionTraceSummaryView(
                trace.getTraceId(),
                trace.getUserId(),
                trace.getSessionCode(),
                trace.getAssistantMessageCode(),
                trace.getClientMessageId(),
                trace.getProfileCode(),
                trace.getChatModelCode(),
                trace.getRequestQueryMasked(),
                trace.getStatus().name(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getDurationMs(),
                trace.getAttemptCount(),
                trace.getErrorCode(),
                trace.getErrorMessage()
        );
    }

    private AgentExecutionSpanView toSpanView(AgentExecutionSpan span) {
        return new AgentExecutionSpanView(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getSpanName(),
                span.getSpanType().name(),
                span.getStatus().name(),
                span.getSequenceNo(),
                span.getAttemptNo(),
                span.getStartedAt(),
                span.getEndedAt(),
                span.getDurationMs(),
                span.getInputJson(),
                span.getOutputJson(),
                span.getTagsJson(),
                span.getErrorJson()
        );
    }

    private AgentExecutionEventView toEventView(AgentExecutionEvent event) {
        return new AgentExecutionEventView(
                event.getId(),
                event.getSpanId(),
                event.getEventType(),
                event.getSequenceNo(),
                event.getOccurredAt(),
                event.getPayloadJson()
        );
    }

    private AgentExecutionBackendSpanView toBackendSpanView(AgentExecutionBackendSpan span) {
        return new AgentExecutionBackendSpanView(
                span.getCallId(),
                span.getParentCallId(),
                span.getCallName(),
                span.getCallType(),
                span.getServiceClass(),
                span.getMethodName(),
                span.getStatus().name(),
                span.getSequenceNo(),
                span.getAttemptNo(),
                span.getStartedAt(),
                span.getEndedAt(),
                span.getDurationMs(),
                span.getInputJson(),
                span.getOutputJson(),
                span.getErrorJson(),
                span.getRelatedSpanId()
        );
    }

    private List<AgentExecutionTimelineItemView> buildAgentTimeline(
            List<AgentExecutionSpan> spans,
            List<AgentExecutionEvent> events
    ) {
        List<AgentExecutionTimelineItemView> items = new ArrayList<>();
        Map<String, AgentExecutionSpan> spanById = spans.stream()
                .collect(java.util.stream.Collectors.toMap(AgentExecutionSpan::getSpanId, span -> span, (left, right) -> left));
        for (AgentExecutionSpan span : spans) {
            if ("query.route".equals(span.getSpanName())) {
                items.add(new AgentExecutionTimelineItemView(
                        "span:" + span.getSpanId(),
                        "ROUTING",
                        "SPAN",
                        "查询路由",
                        span.getStatus().name(),
                        span.getSequenceNo(),
                        span.getStartedAt(),
                        span.getEndedAt(),
                        span.getDurationMs(),
                        span.getInputJson(),
                        span.getOutputJson(),
                        null,
                        span.getSpanId(),
                        null
                ));
            } else if (span.getSpanType() != null && "TOOL".equals(span.getSpanType().name())) {
                items.add(new AgentExecutionTimelineItemView(
                        "span:" + span.getSpanId(),
                        "TOOL_CALL",
                        "SPAN",
                        "工具调用",
                        span.getStatus().name(),
                        span.getSequenceNo(),
                        span.getStartedAt(),
                        span.getEndedAt(),
                        span.getDurationMs(),
                        span.getInputJson(),
                        span.getOutputJson(),
                        null,
                        span.getSpanId(),
                        null
                ));
            }
        }
        for (AgentExecutionEvent event : events) {
            String title = timelineTitle(event.getEventType(), event.getPayloadJson());
            String itemType = timelineType(event.getEventType());
            if (title == null || itemType == null) {
                continue;
            }
            AgentExecutionSpan relatedSpan = event.getSpanId() == null ? null : spanById.get(event.getSpanId());
            items.add(new AgentExecutionTimelineItemView(
                    "event:" + event.getId(),
                    itemType,
                    "EVENT",
                    title,
                    timelineStatus(event.getEventType()),
                    event.getSequenceNo(),
                    event.getOccurredAt(),
                    event.getOccurredAt(),
                    relatedSpan != null && "query.route".equals(relatedSpan.getSpanName()) ? relatedSpan.getDurationMs() : null,
                    null,
                    null,
                    event.getPayloadJson(),
                    event.getSpanId(),
                    event.getId()
            ));
        }
        return items.stream()
                .sorted(java.util.Comparator
                        .comparing(AgentExecutionTimelineItemView::sequenceNo)
                        .thenComparing(item -> item.relatedEventId() == null ? -1L : item.relatedEventId()))
                .toList();
    }

    private String timelineTitle(String eventType, String payloadJson) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "request.received" -> "接收请求";
            case "agent.call.start" -> "Agent 开始执行";
            case "agent.call.end" -> "Agent 执行结束";
            case "prompt.injected" -> payloadJson != null && payloadJson.contains("\"phase\":\"summary\"")
                    ? "注入总结 Prompt"
                    : "注入推理 Prompt";
            case "tool.result" -> "工具结果回注";
            case "hint" -> "上下文提示";
            case "agent.result" -> "Agent 产出最终消息";
            case "final.response" -> "最终响应";
            case "agent.error" -> "Agent 异常";
            default -> null;
        };
    }

    private String timelineType(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "request.received" -> "REQUEST";
            case "agent.call.start", "agent.call.end" -> "AGENT_CALL";
            case "prompt.injected" -> "PROMPT";
            case "tool.result" -> "TOOL_RESULT";
            case "hint" -> "HINT";
            case "agent.result" -> "AGENT_RESULT";
            case "final.response" -> "FINAL_RESPONSE";
            case "agent.error" -> "ERROR";
            default -> null;
        };
    }

    private String timelineStatus(String eventType) {
        if ("agent.error".equals(eventType)) {
            return "FAILED";
        }
        return null;
    }
}
