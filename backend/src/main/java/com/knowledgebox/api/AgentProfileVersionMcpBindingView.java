package com.knowledgebox.api;

import java.util.List;

public record AgentProfileVersionMcpBindingView(
        String mcpCode,
        List<String> enableTools,
        List<String> disableTools
) {
}
