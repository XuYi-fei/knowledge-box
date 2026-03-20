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
            Toolkit toolkit,
            SkillBox skillBox,
            List<Hook> hooks,
            ToolExecutionContext toolExecutionContext
    ) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("knowledge-box-react-agent")
                .description("Knowledge Box public chat agent")
                .sysPrompt(buildSystemPrompt(profile))
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

    private String buildSystemPrompt(AgentProfileVersion profile) {
        return profile.getSystemPrompt() == null ? "" : profile.getSystemPrompt().trim();
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
