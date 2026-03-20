package com.knowledgebox.api;

import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.domain.agent.ProfileStatus;
import java.util.List;

public record AgentConfigSnapshotView(
        String profileCode,
        String profileName,
        String description,
        AgentProfileVersionType agentType,
        ProfileStatus status,
        boolean published,
        boolean publicDebug,
        String chatModel,
        String routingModel,
        String embeddingModel,
        String rerankModel,
        double temperature,
        int retrievalTopK,
        int reasoningBudget,
        String systemPrompt,
        String knowledgeBaseToolPromptTemplate,
        String knowledgeBaseInjectedContextPromptTemplate,
        String knowledgeBaseNoEvidencePromptTemplate,
        String knowledgeBaseDisabledPromptTemplate,
        List<String> toolCodes,
        List<String> skillCodes,
        List<AgentConfigMcpBindingView> mcpBindings,
        List<String> childAgentProfileCodes,
        List<AgentRuntimeEnvVarView> envVars
) {
}
