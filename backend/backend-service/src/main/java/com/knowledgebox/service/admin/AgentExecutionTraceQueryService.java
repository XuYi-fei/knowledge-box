package com.knowledgebox.service.admin;

import com.knowledgebox.api.AgentExecutionEventView;
import com.knowledgebox.api.AgentExecutionSpanView;
import com.knowledgebox.api.AgentExecutionTraceDetailView;
import com.knowledgebox.api.AgentExecutionTracePageView;
import com.knowledgebox.api.AgentExecutionTraceSummaryView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.repository.AgentExecutionEventRepository;
import com.knowledgebox.repository.AgentExecutionSpanRepository;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import jakarta.annotation.Nullable;
import java.util.List;
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

    public AgentExecutionTraceQueryService(
            AgentExecutionTraceRepository traceRepository,
            AgentExecutionSpanRepository spanRepository,
            AgentExecutionEventRepository eventRepository
    ) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
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
        return new AgentExecutionTraceDetailView(
                toSummaryView(trace),
                spanRepository.findByTraceIdOrderBySequenceNoAscIdAsc(traceId).stream()
                        .map(span -> new AgentExecutionSpanView(
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
                        ))
                        .toList(),
                eventRepository.findByTraceIdOrderBySequenceNoAscIdAsc(traceId).stream()
                        .map(event -> new AgentExecutionEventView(
                                event.getId(),
                                event.getSpanId(),
                                event.getEventType(),
                                event.getSequenceNo(),
                                event.getOccurredAt(),
                                event.getPayloadJson()
                        ))
                        .toList()
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
}
