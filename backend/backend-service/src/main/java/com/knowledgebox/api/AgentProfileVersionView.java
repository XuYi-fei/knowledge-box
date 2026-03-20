package com.knowledgebox.api;

import com.knowledgebox.domain.agent.ProfileStatus;
import com.knowledgebox.domain.agent.AgentProfileVersionType;

public record AgentProfileVersionView(
        Long id,
        String profileCode,
        String profileName,
        int versionNumber,
        ProfileStatus status,
        boolean published,
        boolean publicDebug,
        AgentProfileVersionType agentType,
        String chatModel,
        String embeddingModel,
        String rerankModel,
        double temperature,
        int retrievalTopK,
        int reasoningBudget,
        String systemPrompt
) {
}
