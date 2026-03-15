package com.knowledgebox.service.chat;

import java.util.List;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseSearchTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSearchTool.class);

    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;
    private final AgentTraceService agentTraceService;
    private final AgentExecutionTraceService agentExecutionTraceService;

    public KnowledgeBaseSearchTool(
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
            AgentTraceService agentTraceService,
            AgentExecutionTraceService agentExecutionTraceService
    ) {
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
        this.agentTraceService = agentTraceService;
        this.agentExecutionTraceService = agentExecutionTraceService;
    }

    @Tool(
            name = "searchKnowledgeBase",
            description = "Search the internal knowledge base and return grounded snippets with document titles, headings and anchors before giving the final answer."
    )
    public String searchKnowledgeBase(
            @ToolParam(name = "query", description = "The user question or retrieval query rewritten for semantic search.") String query,
            @ToolParam(name = "topK", required = false, description = "Maximum number of snippets to retrieve. Use a small number such as 3 to 6.") Integer topK,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime
    ) {
        String callId = null;
        if (traceContext != null) {
            callId = agentExecutionTraceService.nextBackendSpanIdValue();
            java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
            input.put("query", query);
            input.put("topK", topK);
            agentExecutionTraceService.startBackendSpan(
                    traceContext,
                    traceContext.currentActiveBackendSpanId(),
                    callId,
                    "KnowledgeBaseSearchTool.searchKnowledgeBase",
                    "TOOL",
                    getClass().getSimpleName(),
                    "searchKnowledgeBase",
                    input,
                    null
            );
        }
        List<RetrievedChunk> chunks;
        try {
            chunks = knowledgeBaseRetrievalService.search(query, topK, traceContext, callId);
        } catch (RuntimeException exception) {
            if (traceContext != null && callId != null) {
                java.util.Map<String, Object> error = new java.util.LinkedHashMap<>();
                error.put("exceptionClass", exception.getClass().getName());
                error.put("message", exception.getMessage());
                agentExecutionTraceService.endBackendSpan(
                        traceContext,
                        callId,
                        AgentExecutionStatus.FAILED,
                        java.util.Map.of(),
                        error
                );
            }
            throw exception;
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("query", query);
        payload.put("topK", topK);
        payload.put("hits", chunks.size());
        String sessionCode = exchangeRuntime == null ? null : exchangeRuntime.sessionCode();
        if (sessionCode != null && !sessionCode.isBlank()) {
            exchangeRuntime.recordToolCall("searchKnowledgeBase");
            exchangeRuntime.recordRetrievals(chunks);
            agentTraceService.trace(sessionCode, "RETRIEVAL_TOOL_EXECUTED", payload);
        } else {
            log.debug("Skip retrieval trace/context recording because sessionCode is missing in tool execution context.");
        }
        if (traceContext != null && callId != null) {
            agentExecutionTraceService.endBackendSpan(
                    traceContext,
                    callId,
                    AgentExecutionStatus.COMPLETED,
                    java.util.Map.of("hits", chunks.size(), "toolName", "searchKnowledgeBase"),
                    null
            );
        }
        return knowledgeBaseRetrievalService.renderToolPayload(chunks);
    }
}
