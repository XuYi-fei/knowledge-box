package com.knowledgebox.api;

import com.knowledgebox.domain.agent.ProfileStatus;

public record AgentProfileVersionView(
        Long id,
        String profileCode,
        int versionNumber,
        ProfileStatus status,
        boolean published,
        String chatModel,
        String routingModel,
        String embeddingModel,
        String rerankModel,
        double temperature,
        int retrievalTopK,
        int reasoningBudget
) {
}
