package com.knowledgebox.api;

import java.util.List;

public record AgentConfigMcpBindingView(
        String mcpCode,
        List<String> enableTools,
        List<String> disableTools
) {
}
