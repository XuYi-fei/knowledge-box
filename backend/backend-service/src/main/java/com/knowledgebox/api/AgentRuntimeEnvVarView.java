package com.knowledgebox.api;

import com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource;

public record AgentRuntimeEnvVarView(
        String key,
        String description,
        boolean secret,
        AgentRuntimeEnvValueSource valueSource,
        String sourceRef,
        String value,
        boolean hasValue
) {
}
