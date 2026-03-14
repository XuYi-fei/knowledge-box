package com.knowledgebox.service.chat;

import java.util.List;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseSearchTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSearchTool.class);

    private final KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;
    private final AgentTraceService agentTraceService;

    public KnowledgeBaseSearchTool(
            KnowledgeBaseRetrievalService knowledgeBaseRetrievalService,
            AgentTraceService agentTraceService
    ) {
        this.knowledgeBaseRetrievalService = knowledgeBaseRetrievalService;
        this.agentTraceService = agentTraceService;
    }

    @Tool(
            name = "searchKnowledgeBase",
            description = "Search the internal knowledge base and return grounded snippets with document titles, headings and anchors before giving the final answer."
    )
    public String searchKnowledgeBase(
            @ToolParam(name = "query", description = "The user question or retrieval query rewritten for semantic search.") String query,
            @ToolParam(name = "topK", required = false, description = "Maximum number of snippets to retrieve. Use a small number such as 3 to 6.") Integer topK
    ) {
        List<RetrievedChunk> chunks = knowledgeBaseRetrievalService.search(query, topK);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("query", query);
        payload.put("topK", topK);
        payload.put("hits", chunks.size());
        String sessionCode = ChatExchangeContext.sessionCode();
        if (sessionCode != null && !sessionCode.isBlank()) {
            ChatExchangeContext.recordToolCall("searchKnowledgeBase");
            ChatExchangeContext.recordRetrievals(chunks);
            agentTraceService.trace(sessionCode, "RETRIEVAL_TOOL_EXECUTED", payload);
        } else {
            log.debug("Skip retrieval trace/context recording because sessionCode is missing in tool execution thread.");
        }
        return knowledgeBaseRetrievalService.renderToolPayload(chunks);
    }
}
