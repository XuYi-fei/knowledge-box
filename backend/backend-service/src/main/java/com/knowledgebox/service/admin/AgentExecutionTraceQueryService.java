package com.knowledgebox.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.knowledgebox.api.AgentExecutionBackendSpanView;
import com.knowledgebox.api.AgentExecutionEventView;
import com.knowledgebox.api.AgentExecutionReadableNodeView;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final ObjectMapper objectMapper;

    public AgentExecutionTraceQueryService(
            AgentExecutionTraceRepository traceRepository,
            AgentExecutionSpanRepository spanRepository,
            AgentExecutionEventRepository eventRepository,
            AgentExecutionBackendSpanRepository backendSpanRepository,
            ObjectMapper objectMapper
    ) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.eventRepository = eventRepository;
        this.backendSpanRepository = backendSpanRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentExecutionTracePageView traces(
            @Nullable String traceId,
            @Nullable String status,
            @Nullable String profileCode,
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
        if (StringUtils.hasText(profileCode)) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("profileCode"), profileCode.trim()));
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
                buildReadableAgentTimeline(spans, events),
                buildReadableBackendTimeline(backendSpans),
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
                .collect(Collectors.toMap(AgentExecutionSpan::getSpanId, span -> span, (left, right) -> left));
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
                .sorted(Comparator
                        .comparing(AgentExecutionTimelineItemView::sequenceNo)
                        .thenComparing(item -> item.relatedEventId() == null ? -1L : item.relatedEventId()))
                .toList();
    }

    private List<AgentExecutionReadableNodeView> buildReadableAgentTimeline(
            List<AgentExecutionSpan> spans,
            List<AgentExecutionEvent> events
    ) {
        List<AgentExecutionSpan> orderedSpans = spans.stream()
                .sorted(Comparator.comparing(AgentExecutionSpan::getSequenceNo))
                .toList();
        List<AgentExecutionEvent> orderedEvents = events.stream()
                .sorted(Comparator.comparing(AgentExecutionEvent::getSequenceNo).thenComparing(AgentExecutionEvent::getId))
                .toList();
        Map<String, ReadableNodeAccumulator> spanNodes = new LinkedHashMap<>();
        List<ReadableNodeAccumulator> roots = new ArrayList<>();

        AgentExecutionSpan requestSpan = orderedSpans.stream().filter(span -> span.getSpanType() != null && "REQUEST".equals(span.getSpanType().name())).findFirst().orElse(null);
        AgentExecutionSpan routingSpan = orderedSpans.stream()
                .filter(span -> (span.getSpanType() != null && "ROUTING".equals(span.getSpanType().name())) || "query.route".equals(span.getSpanName()))
                .findFirst()
                .orElse(null);
        AgentExecutionSpan streamSpan = orderedSpans.stream().filter(span -> span.getSpanType() != null && "STREAM".equals(span.getSpanType().name())).findFirst().orElse(null);
        AgentExecutionSpan finalizeSpan = orderedSpans.stream().filter(span -> span.getSpanType() != null && "FINALIZE".equals(span.getSpanType().name())).findFirst().orElse(null);

        for (AgentExecutionSpan span : orderedSpans) {
            spanNodes.put(span.getSpanId(), toReadableSpanNode(span));
        }
        for (AgentExecutionSpan span : orderedSpans) {
            ReadableNodeAccumulator current = spanNodes.get(span.getSpanId());
            if (current == null) {
                continue;
            }
            if (StringUtils.hasText(span.getParentSpanId()) && spanNodes.containsKey(span.getParentSpanId())) {
                spanNodes.get(span.getParentSpanId()).children.add(current);
            } else {
                roots.add(current);
            }
        }

        for (AgentExecutionEvent event : orderedEvents) {
            if (!shouldDisplayReadableAgentEvent(event.getEventType())) {
                continue;
            }
            String parentSpanId = event.getSpanId();
            switch (event.getEventType()) {
                case "request.received" -> parentSpanId = requestSpan == null ? event.getSpanId() : requestSpan.getSpanId();
                case "query.routed" -> parentSpanId = routingSpan != null ? routingSpan.getSpanId() : requestSpan == null ? event.getSpanId() : requestSpan.getSpanId();
                case "agent.call.start", "agent.call.end" -> parentSpanId = streamSpan != null ? streamSpan.getSpanId() : requestSpan == null ? event.getSpanId() : requestSpan.getSpanId();
                case "prompt.injected", "reasoning.chunk", "summary.chunk", "tool.result", "hint", "agent.result", "agent.error" -> parentSpanId = streamSpan != null ? streamSpan.getSpanId() : event.getSpanId();
                case "tool.start", "tool.chunk", "tool.end" -> parentSpanId = event.getSpanId();
                case "final.response" -> parentSpanId = finalizeSpan != null ? finalizeSpan.getSpanId() : requestSpan == null ? event.getSpanId() : requestSpan.getSpanId();
                default -> {
                }
            }
            ReadableNodeAccumulator node = toReadableEventNode(event);
            if (node == null) {
                continue;
            }
            if (StringUtils.hasText(parentSpanId) && spanNodes.containsKey(parentSpanId)) {
                spanNodes.get(parentSpanId).children.add(node);
            } else if (!roots.isEmpty()) {
                roots.get(0).children.add(node);
            } else {
                roots.add(node);
            }
        }

        sortReadableNodes(roots);
        return roots.stream().map(this::toReadableNodeView).toList();
    }

    private List<AgentExecutionReadableNodeView> buildReadableBackendTimeline(List<AgentExecutionBackendSpan> spans) {
        List<AgentExecutionBackendSpan> ordered = spans.stream()
                .sorted(Comparator.comparing(AgentExecutionBackendSpan::getSequenceNo).thenComparing(AgentExecutionBackendSpan::getId))
                .toList();
        Map<String, ReadableNodeAccumulator> nodeById = new LinkedHashMap<>();
        List<ReadableNodeAccumulator> roots = new ArrayList<>();
        for (AgentExecutionBackendSpan span : ordered) {
            nodeById.put(span.getCallId(), toReadableBackendNode(span));
        }
        for (AgentExecutionBackendSpan span : ordered) {
            ReadableNodeAccumulator current = nodeById.get(span.getCallId());
            if (current == null) {
                continue;
            }
            if (StringUtils.hasText(span.getParentCallId()) && nodeById.containsKey(span.getParentCallId())) {
                nodeById.get(span.getParentCallId()).children.add(current);
            } else {
                roots.add(current);
            }
        }
        sortReadableNodes(roots);
        return roots.stream().map(this::toReadableNodeView).toList();
    }

    private void sortReadableNodes(List<ReadableNodeAccumulator> nodes) {
        nodes.sort(Comparator.comparing(ReadableNodeAccumulator::sequenceNo));
        for (ReadableNodeAccumulator node : nodes) {
            sortReadableNodes(node.children);
        }
    }

    private AgentExecutionReadableNodeView toReadableNodeView(ReadableNodeAccumulator node) {
        return new AgentExecutionReadableNodeView(
                node.nodeId,
                node.nodeType,
                node.title,
                node.badge,
                node.technicalLabel,
                node.plainSummary,
                node.inputExplanation,
                node.outputExplanation,
                node.status,
                node.sequenceNo,
                node.startedAt,
                node.endedAt,
                node.durationMs,
                node.rawRefType,
                node.rawRefId,
                node.children.stream().map(this::toReadableNodeView).toList()
        );
    }

    private ReadableNodeAccumulator toReadableSpanNode(AgentExecutionSpan span) {
        JsonNode input = readJson(span.getInputJson());
        JsonNode output = readJson(span.getOutputJson());
        JsonNode error = readJson(span.getErrorJson());
        ReadableExplanation explanation = explainSpan(span, input, output, error);
        return new ReadableNodeAccumulator(
                "span:" + span.getSpanId(),
                "SPAN",
                explanation.title(),
                span.getSpanType() == null ? "SPAN" : span.getSpanType().name(),
                span.getSpanName(),
                explanation.plainSummary(),
                explanation.inputExplanation(),
                explanation.outputExplanation(),
                span.getStatus().name(),
                span.getSequenceNo(),
                span.getStartedAt(),
                span.getEndedAt(),
                span.getDurationMs(),
                "SPAN",
                span.getSpanId()
        );
    }

    private ReadableNodeAccumulator toReadableEventNode(AgentExecutionEvent event) {
        JsonNode payload = readJson(event.getPayloadJson());
        ReadableExplanation explanation = explainEvent(event, payload);
        if (explanation == null) {
            return null;
        }
        return new ReadableNodeAccumulator(
                "event:" + event.getId(),
                "EVENT",
                explanation.title(),
                "EVENT",
                event.getEventType(),
                explanation.plainSummary(),
                explanation.inputExplanation(),
                explanation.outputExplanation(),
                timelineStatus(event.getEventType()),
                event.getSequenceNo(),
                event.getOccurredAt(),
                event.getOccurredAt(),
                null,
                "EVENT",
                String.valueOf(event.getId())
        );
    }

    private ReadableNodeAccumulator toReadableBackendNode(AgentExecutionBackendSpan span) {
        JsonNode input = readJson(span.getInputJson());
        JsonNode output = readJson(span.getOutputJson());
        JsonNode error = readJson(span.getErrorJson());
        ReadableExplanation explanation = explainBackendSpan(span, input, output, error);
        return new ReadableNodeAccumulator(
                "backend:" + span.getCallId(),
                "BACKEND_CALL",
                explanation.title(),
                span.getCallType(),
                span.getCallName(),
                explanation.plainSummary(),
                explanation.inputExplanation(),
                explanation.outputExplanation(),
                span.getStatus().name(),
                span.getSequenceNo(),
                span.getStartedAt(),
                span.getEndedAt(),
                span.getDurationMs(),
                "BACKEND_CALL",
                span.getCallId()
        );
    }

    private ReadableExplanation explainSpan(AgentExecutionSpan span, JsonNode input, JsonNode output, JsonNode error) {
        String spanType = span.getSpanType() == null ? "" : span.getSpanType().name();
        return switch (spanType) {
            case "REQUEST" -> new ReadableExplanation(
                    "接收用户问题",
                    "系统收到一次新的对话请求，并为后续步骤建立 trace/span 上下文。",
                    joinParts(
                            prefixed("问题", truncate(display(input.path("query")), 120)),
                            prefixed("会话", display(input.path("sessionCode"))),
                            prefixed("模型", display(input.path("chatModelCode"))),
                            prefixed("Profile", display(input.path("profileCode")))
                    ),
                    "这一步本身不产出回答，它负责把后续路由、Agent 执行、工具调用和最终响应都挂到同一条链路上。"
            );
            case "ROUTING" -> new ReadableExplanation(
                    "判断是否需要知识库",
                    boolText(output, "enableKnowledgeBase") == null
                            ? "系统正在分析这个问题是否需要知识库参与。"
                            : "系统已完成路由判断，本次" + (booleanValue(output, "enableKnowledgeBase") ? "需要" : "不需要") + "调用知识库。",
                    prefixed("原始问题", truncate(display(input.path("query")), 120)),
                    joinParts(
                            prefixed("知识库", booleanValue(output, "enableKnowledgeBase") == null ? null : booleanValue(output, "enableKnowledgeBase") ? "开启" : "跳过"),
                            prefixed("判定来源", humanizeRoutingSource(display(output.path("source")))),
                            prefixed("判定依据", humanizeRoutingReason(display(output.path("reason")))),
                            prefixed("路由模型", display(output.path("routingModel")))
                    )
            );
            case "STREAM" -> new ReadableExplanation(
                    "执行 ReActAgent 并生成回答",
                    "系统正在运行 ReActAgent，让模型结合历史上下文、Prompt 和工具调用结果逐步生成最终回答。",
                    joinParts(
                            prefixed("模型", display(input.path("chatModel"))),
                            prefixed("知识库", booleanValue(input, "enableKnowledgeBase") == null ? null : booleanValue(input, "enableKnowledgeBase") ? "已启用" : "未启用"),
                            prefixed("历史消息", numberText(input, "historyTurns", " 条"))
                    ),
                    joinParts(
                            prefixed("回答长度", numberText(output, "answerLength", " 字符")),
                            prefixed("推理步骤", numberText(output, "reasoningStepCount", " 个")),
                            prefixed("工具调用", arraySummary(output.path("toolCalls"), "个工具"))
                    )
            );
            case "TOOL" -> new ReadableExplanation(
                    toolReadableTitle(display(input.path("toolName"))),
                    display(input.path("toolName")) == null
                            ? "Agent 决定调用一个工具来补充信息。"
                            : "Agent 决定调用工具 `" + display(input.path("toolName")) + "` 获取更多信息。",
                    joinParts(
                            prefixed("工具", display(input.path("toolName"))),
                            prefixed("调用 ID", display(input.path("toolCallId"))),
                            prefixed("参数", truncate(display(input.path("toolInput")), 120))
                    ),
                    joinParts(
                            prefixed("结果", truncate(toolResultSummary(output.path("toolResult")), 140)),
                            error == null || error.isNull() ? null : prefixed("异常", truncate(display(error), 120))
                    )
            );
            case "FINALIZE" -> new ReadableExplanation(
                    "整理最终响应并准备返回",
                    "系统正在把最终回答、引用和工具记录整理成前端可消费的响应结构。",
                    joinParts(
                            prefixed("工具数", arraySummary(input.path("toolCalls"), "个工具")),
                            prefixed("引用数", numberText(input, "citationCount", " 条")),
                            prefixed("推理步骤", numberText(input, "reasoningStepCount", " 个"))
                    ),
                    joinParts(
                            prefixed("最终回答", truncate(display(output.path("answer")), 140)),
                            prefixed("工具调用", arraySummary(output.path("toolCalls"), "个工具")),
                            prefixed("引用数", arraySummary(output.path("citations"), "条引用"))
                    )
            );
            default -> new ReadableExplanation(
                    span.getSpanName() == null ? "执行阶段" : span.getSpanName(),
                    "这是链路中的一个内部执行阶段。",
                    prefixed("输入", truncate(display(input), 140)),
                    joinParts(
                            prefixed("输出", truncate(display(output), 140)),
                            prefixed("异常", truncate(display(error), 140))
                    )
            );
        };
    }

    private ReadableExplanation explainEvent(AgentExecutionEvent event, JsonNode payload) {
        String eventType = event.getEventType();
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        return switch (eventType) {
            case "request.received" -> new ReadableExplanation(
                    "确认已接收请求",
                    "系统已经读到用户问题，并拿到了会话上下文的基础信息。",
                    joinParts(
                            prefixed("问题", truncate(display(payload.path("query")), 120)),
                            prefixed("历史消息", numberText(payload, "historyTurns", " 条")),
                            prefixed("Profile", display(payload.path("profile"))),
                            prefixed("模型", display(payload.path("chatModel")))
                    ),
                    "后续会继续进行查询路由和 Agent 执行。"
            );
            case "query.routed" -> new ReadableExplanation(
                    "得到路由结论",
                    booleanValue(payload, "enableKnowledgeBase") == null
                            ? "系统已经完成路由，但当前未记录是否启用知识库。"
                            : "系统判定本次" + (booleanValue(payload, "enableKnowledgeBase") ? "需要" : "不需要") + "使用知识库。",
                    prefixed("原始问题", truncate(display(payload.path("query")), 120)),
                    joinParts(
                            prefixed("知识库", booleanValue(payload, "enableKnowledgeBase") == null ? null : booleanValue(payload, "enableKnowledgeBase") ? "开启" : "跳过"),
                            prefixed("判定来源", humanizeRoutingSource(display(payload.path("source")))),
                            prefixed("判定依据", humanizeRoutingReason(display(payload.path("reason")))),
                            prefixed("路由模型输出", display(payload.path("routingModelOutput")))
                    )
            );
            case "agent.call.start" -> new ReadableExplanation(
                    "开始执行 ReActAgent",
                    "Agent 已开始本轮推理和工具决策。",
                    prefixed("输入消息", arraySummary(payload.path("inputMessages"), "条消息")),
                    "接下来会看到 Prompt 注入、模型思考、工具调用或最终结果。"
            );
            case "agent.call.end" -> new ReadableExplanation(
                    "Agent 本轮执行结束",
                    "本轮 Agent 执行已经结束，并产出了最终消息对象。",
                    null,
                    prefixed("最终消息", truncate(messagePreview(payload.path("finalMessage")), 140))
            );
            case "prompt.injected" -> {
                String phase = display(payload.path("phase"));
                yield new ReadableExplanation(
                        "summary".equals(phase) ? "向模型注入总结 Prompt" : "向模型注入推理 Prompt",
                        "系统把当前上下文、规则、工具结果和历史消息整理成 Prompt，再发送给模型。",
                        joinParts(
                                prefixed("阶段", "summary".equals(phase) ? "总结" : "推理"),
                                prefixed("模型", display(payload.path("modelName"))),
                                prefixed("输入消息", arraySummary(payload.path("inputMessages"), "条消息"))
                        ),
                        joinParts(
                                prefixed("当前轮次", numberText(payload, "currentIteration", "")),
                                prefixed("最大轮次", numberText(payload, "maxIterations", ""))
                        )
                );
            }
            case "reasoning.chunk" -> new ReadableExplanation(
                    "模型输出了一段思考",
                    "这是 Agent 在最终作答前暴露出来的一小段 thinking 片段。",
                    prefixed("思考内容", truncate(display(payload.path("text")), 140)),
                    null
            );
            case "summary.chunk" -> new ReadableExplanation(
                    "模型输出了一段回答正文",
                    "这是最终回答在流式输出过程中的一段正文片段。",
                    prefixed("正文片段", truncate(display(payload.path("text")), 140)),
                    null
            );
            case "tool.start" -> new ReadableExplanation(
                    "开始执行工具",
                    "Agent 已经决定调用工具，并把参数发给工具执行层。",
                    joinParts(
                            prefixed("工具", display(payload.path("toolName"))),
                            prefixed("调用 ID", display(payload.path("toolCallId"))),
                            prefixed("参数", truncate(display(payload.path("toolInput")), 120))
                    ),
                    null
            );
            case "tool.chunk" -> new ReadableExplanation(
                    "工具返回了一段流式结果",
                    "这个工具支持边执行边返回片段结果。",
                    prefixed("结果片段", truncate(display(payload.path("chunk")), 140)),
                    null
            );
            case "tool.end" -> new ReadableExplanation(
                    "工具执行结束",
                    "工具已经完成本次调用，并把结果返回给 Agent。",
                    prefixed("工具", display(payload.path("toolName"))),
                    prefixed("返回结果", truncate(toolResultSummary(payload.path("toolResult")), 140))
            );
            case "tool.result" -> new ReadableExplanation(
                    "把工具结果回注给模型",
                    "Agent 已拿到工具结果，接下来会继续推理或直接生成回答。",
                    prefixed("工具", display(payload.path("toolName"))),
                    null
            );
            case "hint" -> new ReadableExplanation(
                    "系统补充上下文提示",
                    "这是系统额外提供给 Agent 的上下文提示信息。",
                    prefixed("提示内容", truncate(display(payload.path("text")), 140)),
                    null
            );
            case "agent.result" -> new ReadableExplanation(
                    "Agent 产出最终回答",
                    "Agent 已经形成最终回答消息，接下来会整理成前端可消费的响应。",
                    prefixed("工具调用", arraySummary(payload.path("toolCalls"), "个工具")),
                    prefixed("回答预览", truncate(display(payload.path("message")), 140))
            );
            case "final.response" -> new ReadableExplanation(
                    "生成最终响应",
                    "系统已经把最终答案、引用和工具记录整理成返回给前端的结构。",
                    joinParts(
                            prefixed("工具调用", arraySummary(payload.path("toolCalls"), "个工具")),
                            prefixed("引用数", arraySummary(payload.path("citations"), "条引用"))
                    ),
                    prefixed("最终回答", truncate(display(payload.path("answer")), 160))
            );
            case "agent.error" -> new ReadableExplanation(
                    "Agent 发生异常",
                    "执行链路在这一点发生了错误，后续通常会转入失败收尾流程。",
                    prefixed("异常类型", display(payload.path("exceptionClass"))),
                    joinParts(
                            prefixed("错误信息", truncate(display(payload.path("message")), 140)),
                            prefixed("错误码", display(payload.path("errorCode")))
                    )
            );
            default -> new ReadableExplanation(
                    eventType,
                    "这是一个尚未单独语义化的内部事件。",
                    prefixed("输入", truncate(display(payload), 140)),
                    null
            );
        };
    }

    private ReadableExplanation explainBackendSpan(
            AgentExecutionBackendSpan span,
            JsonNode input,
            JsonNode output,
            JsonNode error
    ) {
        String callName = span.getCallName();
        return switch (callName == null ? "" : callName) {
            case "ChatOrchestrator.generate" -> new ReadableExplanation(
                    "编排整条对话生成链路",
                    "这是本次问题在后端的根调用，后面的历史读取、路由判断、Agent 执行、落库和 SSE 推送都挂在它下面。",
                    joinParts(
                            prefixed("会话", display(input.path("sessionCode"))),
                            prefixed("消息", display(input.path("assistantMessageId"))),
                            prefixed("问题", truncate(display(input.path("query")), 120))
                    ),
                    prefixed("状态", display(output.path("status")))
            );
            case "ConversationMemoryService.history" -> new ReadableExplanation(
                    "读取最近会话历史",
                    "系统先把最近的会话内容读出来，补给 Agent 作为上下文。",
                    joinParts(
                            prefixed("会话", display(input.path("sessionCode"))),
                            prefixed("读取上限", numberText(input, "historyTurns", " 条"))
                    ),
                    prefixed("实际返回", numberText(output, "turnCount", " 条"))
            );
            case "KnowledgeBaseRoutingService.routeQuery" -> new ReadableExplanation(
                    "执行知识库路由判断",
                    "系统调用专门的路由逻辑，判断这次问题要不要查知识库。",
                    prefixed("问题", truncate(display(input.path("query")), 120)),
                    joinParts(
                            prefixed("知识库", booleanValue(output, "enableKnowledgeBase") == null ? null : booleanValue(output, "enableKnowledgeBase") ? "开启" : "跳过"),
                            prefixed("判定来源", humanizeRoutingSource(display(output.path("source")))),
                            prefixed("判定依据", humanizeRoutingReason(display(output.path("reason"))))
                    )
            );
            case "ChatOrchestrator.createReActAgent" -> new ReadableExplanation(
                    "创建 ReActAgent 实例",
                    "系统根据当前 profile、模型和工具开关，组装出这次请求要用的 ReActAgent。",
                    joinParts(
                            prefixed("模型", display(input.path("chatModel"))),
                            prefixed("知识库", booleanValue(input, "enableKnowledgeBase") == null ? null : booleanValue(input, "enableKnowledgeBase") ? "启用" : "关闭")
                    ),
                    prefixed("实例类型", display(output.path("agentType")))
            );
            case "ReActAgent.stream" -> new ReadableExplanation(
                    "以流式方式运行 ReActAgent",
                    "Agent 在这里真正开始流式推理、调用工具并产出回答。",
                    joinParts(
                            prefixed("模型", display(input.path("chatModel"))),
                            prefixed("历史消息", numberText(input, "historyTurns", " 条"))
                    ),
                    prefixed("事件统计", summarizeEventTypeCounts(output.path("eventTypeCounts")))
            );
            case "KnowledgeBaseSearchTool.searchKnowledgeBase" -> new ReadableExplanation(
                    "调用知识库搜索工具",
                    "这是 Agent 主动发起的一次知识库工具调用。",
                    joinParts(
                            prefixed("查询词", truncate(display(input.path("query")), 120)),
                            prefixed("TopK", numberText(input, "topK", ""))
                    ),
                    joinParts(
                            prefixed("命中数", numberText(output, "hits", " 条")),
                            prefixed("工具名", display(output.path("toolName")))
                    )
            );
            case "KnowledgeBaseRetrievalService.search" -> new ReadableExplanation(
                    "执行知识库检索",
                    "系统实际到向量检索层取回候选知识片段。",
                    joinParts(
                            prefixed("查询词", truncate(display(input.path("query")), 120)),
                            prefixed("TopK", numberText(input, "topK", ""))
                    ),
                    joinParts(
                            prefixed("检索策略", display(output.path("strategy"))),
                            prefixed("命中数", numberText(output, "hits", " 条")),
                            prefixed("模式", display(output.path("mode")))
                    )
            );
            case "ConversationMemoryService.completeAssistantMessage" -> new ReadableExplanation(
                    "持久化最终回答",
                    "系统把本轮对话的最终回答、引用和工具调用结果写回会话存储。",
                    prefixed("消息 ID", display(input.path("assistantMessageId"))),
                    joinParts(
                            prefixed("状态", display(output.path("status"))),
                            prefixed("回答长度", numberText(output, "answerLength", " 字符"))
                    )
            );
            case "ConversationMemoryService.failAssistantMessage" -> new ReadableExplanation(
                    "把回答标记为失败",
                    "本轮回答失败后，系统把消息状态更新为失败，便于前端感知。",
                    prefixed("消息 ID", display(input.path("assistantMessageId"))),
                    prefixed("状态", display(output.path("status")))
            );
            case "ChatStreamBroker.publish" -> new ReadableExplanation(
                    "向前端推送一条 SSE 事件",
                    "系统把一个流式事件推送给前端，例如 done 或 error。",
                    prefixed("事件名", display(input.path("eventName"))),
                    prefixed("已推送", display(output.path("eventName")))
            );
            case "ChatStreamBroker.complete" -> new ReadableExplanation(
                    "结束 SSE 推送",
                    "系统主动关闭本次对话的 SSE 输出流，表示链路已经收尾。",
                    prefixed("消息 ID", display(input.path("assistantMessageId"))),
                    prefixed("完成", display(output.path("completed")))
            );
            default -> new ReadableExplanation(
                    readableBackendTitle(span),
                    "这是后端调用链中的一个内部方法调用。",
                    joinParts(
                            prefixed("服务", span.getServiceClass()),
                            prefixed("方法", span.getMethodName()),
                            prefixed("输入", truncate(display(input), 140))
                    ),
                    joinParts(
                            prefixed("输出", truncate(display(output), 140)),
                            prefixed("异常", truncate(display(error), 140))
                    )
            );
        };
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

    private boolean shouldDisplayReadableAgentEvent(String eventType) {
        return StringUtils.hasText(eventType) && List.of(
                "request.received",
                "query.routed",
                "agent.call.start",
                "agent.call.end",
                "prompt.injected",
                "reasoning.chunk",
                "summary.chunk",
                "tool.start",
                "tool.chunk",
                "tool.end",
                "tool.result",
                "hint",
                "agent.result",
                "final.response",
                "agent.error"
        ).contains(eventType);
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return NullNode.getInstance();
        }
    }

    private String display(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return compact(node.asText());
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode child : node) {
                String value = display(child);
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
            return values.isEmpty() ? null : String.join("、", values);
        }
        if (node.isObject()) {
            for (String key : List.of("text", "textContent", "query", "answer", "message", "name", "status", "source")) {
                String value = display(node.path(key));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return compact(node.toString());
        }
        return compact(node.toString());
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value) || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }

    private String prefixed(String label, String value) {
        return StringUtils.hasText(value) ? label + "：" + value : null;
    }

    private String joinParts(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                values.add(part);
            }
        }
        return values.isEmpty() ? null : String.join(" | ", values);
    }

    private Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return null;
    }

    private String boolText(JsonNode node, String field) {
        Boolean value = booleanValue(node, field);
        if (value == null) {
            return null;
        }
        return value ? "true" : "false";
    }

    private String numberText(JsonNode node, String field, String suffix) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText() + suffix;
    }

    private String arraySummary(JsonNode node, String suffix) {
        if (node == null || !node.isArray()) {
            return null;
        }
        return node.size() + " " + suffix;
    }

    private String toolResultSummary(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String outputText = display(node.path("output"));
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }
        return display(node);
    }

    private String messagePreview(JsonNode messageNode) {
        if (messageNode == null || messageNode.isNull() || messageNode.isMissingNode()) {
            return null;
        }
        String textContent = display(messageNode.path("textContent"));
        if (StringUtils.hasText(textContent)) {
            return textContent;
        }
        return display(messageNode);
    }

    private String humanizeRoutingSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        return switch (source) {
            case "model" -> "路由模型";
            case "rule" -> "规则";
            case "stub-mode" -> "测试桩模式";
            default -> source;
        };
    }

    private String humanizeRoutingReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        return switch (reason) {
            case "routing-model-classifier" -> "由路由模型判定";
            case "stub-responses-enabled" -> "启用了测试桩模式";
            default -> reason;
        };
    }

    private String toolReadableTitle(String toolName) {
        return StringUtils.hasText(toolName) ? "调用工具 `" + toolName + "`" : "调用工具";
    }

    private String readableBackendTitle(AgentExecutionBackendSpan span) {
        if (StringUtils.hasText(span.getCallName())) {
            return "执行 " + span.getCallName();
        }
        if (StringUtils.hasText(span.getMethodName())) {
            return "执行 " + span.getMethodName();
        }
        return "执行后端调用";
    }

    private String summarizeEventTypeCounts(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        node.fieldNames().forEachRemaining(field -> {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                parts.add(field + ":" + value.asText());
            }
        });
        return parts.isEmpty() ? null : String.join("，", parts);
    }

    private record ReadableExplanation(
            String title,
            String plainSummary,
            String inputExplanation,
            String outputExplanation
    ) {
    }

    private static final class ReadableNodeAccumulator {

        private final String nodeId;
        private final String nodeType;
        private final String title;
        private final String badge;
        private final String technicalLabel;
        private final String plainSummary;
        private final String inputExplanation;
        private final String outputExplanation;
        private final String status;
        private final Integer sequenceNo;
        private final OffsetDateTime startedAt;
        private final OffsetDateTime endedAt;
        private final Long durationMs;
        private final String rawRefType;
        private final String rawRefId;
        private final List<ReadableNodeAccumulator> children = new ArrayList<>();

        private ReadableNodeAccumulator(
                String nodeId,
                String nodeType,
                String title,
                String badge,
                String technicalLabel,
                String plainSummary,
                String inputExplanation,
                String outputExplanation,
                String status,
                Integer sequenceNo,
                OffsetDateTime startedAt,
                OffsetDateTime endedAt,
                Long durationMs,
                String rawRefType,
                String rawRefId
        ) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.title = title;
            this.badge = badge;
            this.technicalLabel = technicalLabel;
            this.plainSummary = plainSummary;
            this.inputExplanation = inputExplanation;
            this.outputExplanation = outputExplanation;
            this.status = status;
            this.sequenceNo = sequenceNo;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.durationMs = durationMs;
            this.rawRefType = rawRefType;
            this.rawRefId = rawRefId;
        }

        private Integer sequenceNo() {
            return sequenceNo == null ? Integer.MAX_VALUE : sequenceNo;
        }
    }
}
