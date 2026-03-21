package com.knowledgebox.service.chat;

import java.util.List;

record QueryExecutionPlan(
        QueryRoutingDecision routingDecision,
        List<RetrievedChunk> retrievedChunks,
        boolean enableKnowledgeBaseTool,
        boolean retrievalAttempted,
        boolean knowledgeBaseToolBound
) {
}
