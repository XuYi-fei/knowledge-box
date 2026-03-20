package com.knowledgebox.service.chat;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

final class CompatibleSubAgentTool implements AgentTool {

    private static final String PARAM_SESSION_ID = "session_id";
    private static final String PARAM_MESSAGE = "message";
    private static final String PARAM_QUERY = "query";

    private final SubAgentTool delegate;
    private final Map<String, Object> parameters;

    CompatibleSubAgentTool(SubAgentProvider<?> provider, SubAgentConfig config) {
        this.delegate = new SubAgentTool(provider, config);
        this.parameters = buildSchema();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam toolCallParam) {
        Map<String, Object> normalizedInput = normalizeInput(toolCallParam.getInput());
        if (!hasText(normalizedInput.get(PARAM_MESSAGE)) && hasText(normalizedInput.get(PARAM_QUERY))) {
            normalizedInput.put(PARAM_MESSAGE, ((String) normalizedInput.get(PARAM_QUERY)).trim());
        }
        if (!hasText(normalizedInput.get(PARAM_MESSAGE))) {
            return Mono.just(ToolResultBlock.error("Sub-agent tool requires a non-empty 'message' or 'query' parameter."));
        }
        return delegate.callAsync(ToolCallParam.builder()
                .toolUseBlock(toolCallParam.getToolUseBlock())
                .input(normalizedInput)
                .agent(toolCallParam.getAgent())
                .context(toolCallParam.getContext())
                .emitter(toolCallParam.getEmitter())
                .build());
    }

    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (input != null && !input.isEmpty()) {
            normalized.putAll(input);
        }
        return normalized;
    }

    private boolean hasText(Object value) {
        return value instanceof String text && StringUtils.hasText(text);
    }

    private Map<String, Object> buildSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PARAM_SESSION_ID, stringLikeProperty(
                "Session ID for multi-turn dialogue. Omit to start a new sub-agent session."
        ));
        properties.put(PARAM_MESSAGE, stringLikeProperty(
                "Primary message to send to the sub-agent. Prefer this field."
        ));
        properties.put(PARAM_QUERY, stringLikeProperty(
                "Backward-compatible alias for message. If message is absent, query will be forwarded as the sub-agent message."
        ));
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> stringLikeProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", List.of("string", "null"));
        property.put("description", description);
        return property;
    }
}
