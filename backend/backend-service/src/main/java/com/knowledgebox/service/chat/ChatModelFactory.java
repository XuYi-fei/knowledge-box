package com.knowledgebox.service.chat;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolExecutionContext;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

final class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    private final KnowledgeBoxProperties properties;
    private final String dashScopeApiKey;
    private final String dashScopeBaseUrl;

    ChatModelFactory(KnowledgeBoxProperties properties, String dashScopeApiKey, String dashScopeBaseUrl) {
        this.properties = properties;
        this.dashScopeApiKey = dashScopeApiKey;
        this.dashScopeBaseUrl = dashScopeBaseUrl;
    }

    ReActAgent createReActAgent(
            AgentProfileVersion profile,
            String chatModelCode,
            boolean enableKnowledgeBaseTool,
            Toolkit toolkit,
            SkillBox skillBox,
            List<Hook> hooks,
            ToolExecutionContext toolExecutionContext
    ) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("knowledge-box-react-agent")
                .description("Knowledge Box public chat agent")
                .sysPrompt(buildSystemPrompt(profile, enableKnowledgeBaseTool))
                .model(buildChatModel(profile, chatModelCode))
                .toolkit(toolkit == null ? new Toolkit() : toolkit)
                .maxIters(8);
        if (skillBox != null) {
            builder.skillBox(skillBox);
        }
        if (hooks != null && !hooks.isEmpty()) {
            builder.hooks(hooks);
        }
        if (toolExecutionContext != null) {
            builder.toolExecutionContext(toolExecutionContext);
        }
        return builder.build();
    }

    Model buildRoutingClassifierModel(String routingModelCode) {
        if (shouldUseDashScopeCompatibleEndpoint(routingModelCode)) {
            return OpenAIChatModel.builder()
                    .apiKey(resolveDashScopeApiKey())
                    .baseUrl(resolveDashScopeCompatibleBaseUrl())
                    .modelName(routingModelCode)
                    .stream(false)
                    .generateOptions(buildRoutingClassifierGenerateOptions(routingModelCode))
                    .build();
        }
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(resolveDashScopeApiKey())
                .modelName(routingModelCode)
                .stream(false)
                .enableThinking(Boolean.FALSE)
                .defaultOptions(buildRoutingClassifierGenerateOptions(routingModelCode));
        if (dashScopeBaseUrl != null && !dashScopeBaseUrl.isBlank()) {
            builder.baseUrl(dashScopeBaseUrl.trim());
        }
        return builder.build();
    }

    GenerateOptions buildRoutingClassifierGenerateOptions(String routingModelCode) {
        GenerateOptions.Builder options = GenerateOptions.builder()
                .modelName(routingModelCode)
                .stream(Boolean.FALSE)
                .temperature(0D)
                .maxTokens(8);
        if (shouldUseDashScopeCompatibleEndpoint(routingModelCode)) {
            options.additionalBodyParam("enable_thinking", false);
        }
        return options.build();
    }

    private Model buildChatModel(AgentProfileVersion profile, String chatModelCode) {
        if (shouldUseDashScopeCompatibleEndpoint(chatModelCode)) {
            log.info("Using DashScope compatible-mode endpoint for chat model: {}", chatModelCode);
            return buildDashScopeCompatibleModel(profile, chatModelCode);
        }
        return buildDashScopeModel(profile, chatModelCode);
    }

    private DashScopeChatModel buildDashScopeModel(AgentProfileVersion profile, String chatModelCode) {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(resolveDashScopeApiKey())
                .modelName(chatModelCode)
                .stream(true)
                .enableThinking(profile.getReasoningBudget() != null && profile.getReasoningBudget() > 0)
                .defaultOptions(buildGenerateOptions(profile, chatModelCode));
        if (dashScopeBaseUrl != null && !dashScopeBaseUrl.isBlank()) {
            builder.baseUrl(dashScopeBaseUrl.trim());
        }
        return builder.build();
    }

    private OpenAIChatModel buildDashScopeCompatibleModel(AgentProfileVersion profile, String chatModelCode) {
        return OpenAIChatModel.builder()
                .apiKey(resolveDashScopeApiKey())
                .baseUrl(resolveDashScopeCompatibleBaseUrl())
                .modelName(chatModelCode)
                .stream(true)
                .generateOptions(buildCompatibleGenerateOptions(profile, chatModelCode, true, false))
                .build();
    }

    private GenerateOptions buildGenerateOptions(AgentProfileVersion profile, String chatModelCode) {
        GenerateOptions.Builder options = GenerateOptions.builder()
                .modelName(chatModelCode)
                .stream(Boolean.TRUE)
                .temperature(profile.getTemperature());
        Integer reasoningBudget = profile.getReasoningBudget();
        if (reasoningBudget != null && reasoningBudget > 0) {
            options.thinkingBudget(reasoningBudget);
        }
        return options.build();
    }

    private GenerateOptions buildCompatibleGenerateOptions(
            AgentProfileVersion profile,
            String chatModelCode,
            boolean stream,
            boolean forceDisableThinking
    ) {
        GenerateOptions.Builder options = GenerateOptions.builder()
                .modelName(chatModelCode)
                .stream(stream ? Boolean.TRUE : Boolean.FALSE)
                .temperature(profile.getTemperature());
        Integer reasoningBudget = profile.getReasoningBudget();
        boolean enableThinking = !forceDisableThinking && reasoningBudget != null && reasoningBudget > 0;
        options.additionalBodyParam("enable_thinking", enableThinking);
        if (enableThinking && reasoningBudget != null) {
            options.additionalBodyParam("thinking_budget", reasoningBudget);
        }
        return options.build();
    }

    private String buildSystemPrompt(AgentProfileVersion profile, boolean enableKnowledgeBaseTool) {
        String profilePrompt = profile.getSystemPrompt() == null ? "" : profile.getSystemPrompt().trim();
        if (enableKnowledgeBaseTool) {
            return profilePrompt + """

                    You operate in ReAct mode.
                    - For factual or system-specific questions, call the searchKnowledgeBase tool before writing the final answer.
                    - Base the final answer strictly on retrieved evidence when available.
                    - If evidence is insufficient, state the uncertainty clearly instead of fabricating details.
                    - Do not expose hidden chain-of-thought.
                    - Keep the final answer concise and structured.
                    """;
        }
        return profilePrompt + """

                You operate in ReAct mode.
                - Knowledge base retrieval tools are intentionally disabled for this request to reduce latency.
                - Answer using general capabilities and current conversation context.
                - If the user asks for repository-specific facts, explicitly state that knowledge-base evidence is unavailable in this round.
                - Do not expose hidden chain-of-thought.
                - Keep the final answer concise and structured.
                """;
    }

    private String resolveDashScopeApiKey() {
        if (dashScopeApiKey == null || dashScopeApiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DASHSCOPE_API_KEY_MISSING",
                    "未配置 DashScope API Key，无法调用 AgentScope ReActAgent"
            );
        }
        return dashScopeApiKey.trim();
    }

    private String resolveDashScopeCompatibleBaseUrl() {
        String configured = properties.getChat().getDashScopeCompatible().getBaseUrl();
        if (configured == null || configured.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return configured.trim();
    }

    private boolean shouldUseDashScopeCompatibleEndpoint(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return false;
        }
        List<String> regexes = properties.getChat().getDashScopeCompatible().getForceModelRegexes();
        if (regexes == null || regexes.isEmpty()) {
            return false;
        }
        for (String regex : regexes) {
            Pattern pattern = safeCompilePattern(regex);
            if (pattern != null && pattern.matcher(modelCode).matches()) {
                return true;
            }
        }
        return false;
    }

    private Pattern safeCompilePattern(String patternText) {
        if (patternText == null || patternText.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(patternText);
        } catch (PatternSyntaxException exception) {
            log.warn("Skip invalid DashScope compatibility regex pattern: {}", patternText, exception);
            return null;
        }
    }
}
