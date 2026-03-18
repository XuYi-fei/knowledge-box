package com.knowledgebox.api;

import java.util.List;

public record UpdateAgentProfileVersionBindingsRequest(
        List<String> toolCodes,
        List<String> skillCodes,
        List<AgentProfileVersionMcpBindingView> mcpBindings,
        List<Long> childAgentVersionIds,
        List<AgentRuntimeEnvVarView> envVars
) {
}
