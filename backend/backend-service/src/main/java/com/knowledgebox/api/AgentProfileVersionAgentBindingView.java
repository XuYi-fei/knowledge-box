package com.knowledgebox.api;

import com.knowledgebox.domain.agent.AgentProfileVersionType;

public record AgentProfileVersionAgentBindingView(
        Long profileVersionId,
        String profileCode,
        String profileName,
        int versionNumber,
        AgentProfileVersionType agentType,
        boolean published
) {
}
