package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatCitationView;
import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.chat.ChatTurn;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class ChatKnowledgeBasePlanService {

    private final AgentCapabilityAssemblyService agentCapabilityAssemblyService;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;

    ChatKnowledgeBasePlanService(
            AgentCapabilityAssemblyService agentCapabilityAssemblyService,
            AgentExecutionTraceService agentExecutionTraceService,
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService
    ) {
        this.agentCapabilityAssemblyService = agentCapabilityAssemblyService;
        this.agentExecutionTraceService = agentExecutionTraceService;
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
    }

    QueryExecutionPlan prepareExecutionPlan(StreamTask task, AgentExecutionTraceContext traceContext) {
        boolean knowledgeBaseToolBound = agentCapabilityAssemblyService.hasKnowledgeBaseToolBound(task.profile().getId());
        if (!knowledgeBaseToolBound) {
            return prepareKnowledgeBaseDisabledPlan(task, traceContext);
        }
        return prepareKnowledgeBaseToolEnabledPlan(task, traceContext, true);
    }

    List<Msg> toAgentScopeHistory(List<ChatTurn> history, QueryExecutionPlan executionPlan) {
        List<Msg> messages = new ArrayList<>();
        if (executionPlan.retrievalAttempted()) {
            messages.add(Msg.builder()
                    .name("knowledge-base-context")
                    .role(MsgRole.SYSTEM)
                    .textContent(renderInjectedKnowledgeContext(executionPlan))
                    .build());
        }
        for (ChatTurn turn : history) {
            MsgRole role = switch (turn.getRole()) {
                case "user" -> MsgRole.USER;
                case "assistant" -> MsgRole.ASSISTANT;
                default -> null;
            };
            if (role == null) {
                continue;
            }
            messages.add(Msg.builder()
                    .role(role)
                    .textContent(turn.getContent() == null ? "" : turn.getContent())
                    .build());
        }
        return messages;
    }

    ChatResponse stubbedAnswer(String sessionId, AgentProfileVersion profile, String query, String chatModelCode, boolean knowledgeBaseToolBound) {
        if (!knowledgeBaseToolBound) {
            return new ChatResponse(
                    sessionId,
                    "当前测试模式下该 Agent 未绑定知识库检索工具，因此不会执行知识库查询。",
                    List.of(),
                    List.of(),
                    chatModelCode
            );
        }
        List<RetrievedChunk> retrievedChunks = knowledgeBaseRetrievalService.search(query, profile.getRetrievalTopK());
        List<String> toolCalls = retrievedChunks.isEmpty() ? List.of() : List.of("searchKnowledgeBase");
        String answer;
        if (retrievedChunks.isEmpty()) {
            answer = "当前测试模式未检索到匹配知识片段，因此无法给出基于知识库的正式回答。";
        } else {
            StringBuilder builder = new StringBuilder("当前运行在测试桩模式，但检索链路已生效。命中的知识片段如下：\n");
            for (RetrievedChunk chunk : retrievedChunks) {
                builder.append("- ")
                        .append(chunk.documentTitle())
                        .append(" / ")
                        .append(chunk.headingPath())
                        .append("：")
                        .append(chunk.snippet())
                        .append('\n');
            }
            answer = builder.toString().trim();
        }
        return new ChatResponse(sessionId, answer, toCitations(retrievedChunks), toolCalls, chatModelCode);
    }

    List<RetrievedChunk> mergeRetrievedChunks(List<RetrievedChunk> preRetrieved, List<RetrievedChunk> runtimeRetrieved) {
        LinkedHashMap<String, RetrievedChunk> merged = new LinkedHashMap<>();
        if (preRetrieved != null) {
            for (RetrievedChunk chunk : preRetrieved) {
                merged.put(chunk.documentId() + "::" + chunk.anchor(), chunk);
            }
        }
        if (runtimeRetrieved != null) {
            for (RetrievedChunk chunk : runtimeRetrieved) {
                merged.put(chunk.documentId() + "::" + chunk.anchor(), chunk);
            }
        }
        return new ArrayList<>(merged.values());
    }

    boolean shouldRunFallbackRetrieval(QueryExecutionPlan executionPlan, List<RetrievedChunk> runtimeRetrievedChunks) {
        return false;
    }

    String finalizeAnswer(String answer, QueryExecutionPlan executionPlan, List<RetrievedChunk> retrievedChunks) {
        if (answer == null) {
            return "";
        }
        if (!executionPlan.retrievalAttempted() || (retrievedChunks != null && !retrievedChunks.isEmpty())) {
            return answer;
        }
        String normalized = answer.replaceAll("\\s+", "");
        if (normalized.contains("知识库") && (normalized.contains("未检索到") || normalized.contains("证据不足") || normalized.contains("未提供支持"))) {
            return answer;
        }
        return "当前知识库未检索到足够相关的公开文档，以下回答基于通用知识给出，仅供参考。\n\n" + answer;
    }

    List<ChatCitationView> toCitations(List<RetrievedChunk> chunks) {
        record CitationAggregate(
                Long documentId,
                String documentTitle,
                LinkedHashSet<String> headingPaths,
                String firstAnchor,
                LinkedHashSet<String> snippets
        ) {
        }

        Map<Long, CitationAggregate> aggregates = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (!chunk.publicVisible()) {
                continue;
            }
            CitationAggregate aggregate = aggregates.computeIfAbsent(
                    chunk.documentId(),
                    ignored -> new CitationAggregate(
                            chunk.documentId(),
                            chunk.documentTitle(),
                            new LinkedHashSet<>(),
                            chunk.anchor(),
                            new LinkedHashSet<>()
                    )
            );
            if (hasText(chunk.headingPath())) {
                aggregate.headingPaths().add(chunk.headingPath().trim());
            }
            if (hasText(chunk.snippet())) {
                aggregate.snippets().add(chunk.snippet().trim());
            }
        }

        return aggregates.values().stream()
                .map(aggregate -> new ChatCitationView(
                        aggregate.documentId(),
                        aggregate.documentTitle(),
                        summarizeCitationText(aggregate.headingPaths(), "未分节"),
                        aggregate.firstAnchor(),
                        summarizeCitationText(aggregate.snippets(), "")
                ))
                .toList();
    }

    private QueryExecutionPlan prepareKnowledgeBaseDisabledPlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext
    ) {
        QueryRoutingDecision routingDecision = new QueryRoutingDecision(
                false,
                "TOOL_NOT_BOUND",
                "searchKnowledgeBase tool is not bound to the current agent version",
                "binding"
        );
        Map<String, Object> routePayload = routingPayload(routingDecision);
        routePayload.put("query", task.query());
        routePayload.put("knowledgeBaseToolBound", false);
        routePayload.put("retrievalAttempted", false);
        agentExecutionTraceService.recordEvent(traceContext, traceContext.requestSpanId(), "query.routed", routePayload);
        return new QueryExecutionPlan(routingDecision, List.of(), false, false, false);
    }

    private QueryExecutionPlan prepareKnowledgeBaseToolEnabledPlan(
            StreamTask task,
            AgentExecutionTraceContext traceContext,
            boolean knowledgeBaseToolBound
    ) {
        QueryRoutingDecision routingDecision = new QueryRoutingDecision(
                true,
                "TOOL_BOUND",
                "searchKnowledgeBase tool is bound and enabled for this round",
                "binding"
        );
        Map<String, Object> routePayload = routingPayload(routingDecision);
        routePayload.put("query", task.query());
        routePayload.put("knowledgeBaseToolBound", knowledgeBaseToolBound);
        routePayload.put("retrievalAttempted", false);
        agentExecutionTraceService.recordEvent(traceContext, traceContext.requestSpanId(), "query.routed", routePayload);
        return new QueryExecutionPlan(routingDecision, List.of(), true, false, knowledgeBaseToolBound);
    }

    private String renderInjectedKnowledgeContext(QueryExecutionPlan executionPlan) {
        if (!executionPlan.retrievalAttempted()) {
            return "";
        }
        if (executionPlan.retrievedChunks().isEmpty()) {
            return """
                    A knowledge-base retrieval was executed for the current user request before model generation.
                    Result: no sufficiently relevant public snippets were found.
                    If you answer from general knowledge, explicitly mention that the current knowledge base did not provide supporting evidence in this round.
                    """;
        }
        StringBuilder builder = new StringBuilder("""
                The following knowledge-base snippets were retrieved before model generation.
                Use them as the primary evidence for repository-specific facts.

                """);
        for (int index = 0; index < executionPlan.retrievedChunks().size(); index++) {
            RetrievedChunk chunk = executionPlan.retrievedChunks().get(index);
            builder.append(index + 1)
                    .append(". [")
                    .append(chunk.documentTitle())
                    .append("] ")
                    .append(chunk.headingPath())
                    .append(" #")
                    .append(chunk.anchor())
                    .append('\n')
                    .append(chunk.snippet())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String summarizeCitationText(LinkedHashSet<String> values, String fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        List<String> orderedValues = new ArrayList<>(values);
        if (orderedValues.size() == 1) {
            return orderedValues.getFirst();
        }
        StringBuilder builder = new StringBuilder(orderedValues.getFirst());
        int previewCount = Math.min(orderedValues.size(), 2);
        for (int index = 1; index < previewCount; index++) {
            builder.append(" / ").append(orderedValues.get(index));
        }
        if (orderedValues.size() > previewCount) {
            builder.append(" 等").append(orderedValues.size()).append("处");
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> routingPayload(QueryRoutingDecision routingDecision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enableKnowledgeBase", routingDecision.enableKnowledgeBase());
        payload.put("matchedRule", routingDecision.matchedRule());
        payload.put("reason", routingDecision.reason());
        payload.put("source", routingDecision.source());
        return payload;
    }
}
