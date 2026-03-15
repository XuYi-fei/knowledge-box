package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;

record QueryRoutingDecision(
        boolean enableKnowledgeBase,
        String matchedRule,
        String reason,
        String source,
        String routingModel,
        String routingModelOutput,
        KnowledgeBoxProperties.RetrievalTriggerMode retrievalTriggerMode
) {
}
