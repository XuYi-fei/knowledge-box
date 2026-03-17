package com.knowledgebox.api;

import java.util.List;

public record AgentProfileVersionBindingsView(
        Long profileVersionId,
        List<String> toolCodes,
        List<String> skillCodes,
        List<AgentProfileVersionMcpBindingView> mcpBindings,
        List<AgentProfileVersionAgentBindingView> childAgentBindings
) {
}
