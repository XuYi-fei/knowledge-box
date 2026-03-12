package com.knowledgebox.service.chat;

import com.knowledgebox.domain.agent.AgentProfileVersion;

record StreamTask(
        Long userId,
        String sessionId,
        String clientMessageId,
        String query,
        String assistantMessageId,
        String profileCode,
        AgentProfileVersion profile,
        String chatModelCode
) {
}
