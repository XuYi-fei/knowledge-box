package com.knowledgebox.service.chat;

record QueryRoutingDecision(
        boolean enableKnowledgeBase,
        String matchedRule,
        String reason,
        String source
) {
}
