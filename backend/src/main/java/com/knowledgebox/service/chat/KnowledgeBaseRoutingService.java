package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KnowledgeBaseRoutingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRoutingService.class);

    private static final String ROUTING_DECISION_NEED_KB = "NEED_KB";
    private static final String ROUTING_DECISION_NO_KB = "NO_KB";
    private static final String ROUTING_CLASSIFIER_SYSTEM_PROMPT = """
            You are a strict query router for knowledge-base retrieval.
            Decide whether the user query needs private/project knowledge-base retrieval.
            Output exactly one enum token and nothing else:
            - NEED_KB
            - NO_KB
            """;

    private final KnowledgeBoxProperties properties;
    private final ChatModelFactory chatModelFactory;

    KnowledgeBaseRoutingService(KnowledgeBoxProperties properties, ChatModelFactory chatModelFactory) {
        this.properties = properties;
        this.chatModelFactory = chatModelFactory;
    }

    QueryRoutingDecision routeQuery(
            String query,
            AgentProfileVersion profile,
            BiFunction<String, String, String> routingModelInvoker
    ) {
        String routingModelCode = resolveRoutingModel(profile);
        KnowledgeBoxProperties.KnowledgeBaseRouting routing = properties.getChat().getKnowledgeBaseRouting();
        if (!routing.isEnabled()) {
            return new QueryRoutingDecision(
                    false,
                    "kb-routing-disabled",
                    "chat.knowledge-base-routing.enabled=false",
                    "config",
                    routingModelCode,
                    null
            );
        }
        String normalized = query == null ? "" : query.strip();
        List<String> forceEnableRegexes = routing.getForceEnableRegexes() == null ? List.of() : routing.getForceEnableRegexes();
        List<String> forceDisableRegexes = routing.getForceDisableRegexes() == null ? List.of() : routing.getForceDisableRegexes();
        for (String patternText : forceEnableRegexes) {
            Pattern pattern = safeCompilePattern(patternText);
            if (pattern != null && pattern.matcher(normalized).matches()) {
                return new QueryRoutingDecision(
                        true,
                        patternText,
                        "force-enable-regex",
                        "rule",
                        routingModelCode,
                        null
                );
            }
        }
        for (String patternText : forceDisableRegexes) {
            Pattern pattern = safeCompilePattern(patternText);
            if (pattern != null && pattern.matcher(normalized).matches()) {
                return new QueryRoutingDecision(
                        false,
                        patternText,
                        "force-disable-regex",
                        "rule",
                        routingModelCode,
                        null
                );
            }
        }
        return routeByLightweightModel(normalized, routingModelCode, routingModelInvoker);
    }

    String invokeRoutingModel(String query, String routingModelCode) {
        try {
            Model classifierModel = chatModelFactory.buildRoutingClassifierModel(routingModelCode);
            List<ChatResponse> responses = classifierModel.stream(
                            buildRoutingClassifierMessages(query),
                            List.of(),
                            buildRoutingClassifierGenerateOptions(routingModelCode)
                    )
                    .collectList()
                    .block();
            return extractRoutingClassifierOutput(responses);
        } catch (Exception exception) {
            log.warn("Routing model classification failed, fallback to NEED_KB. model={}", routingModelCode, exception);
            return "";
        }
    }

    GenerateOptions buildRoutingClassifierGenerateOptions(String routingModelCode) {
        return chatModelFactory.buildRoutingClassifierGenerateOptions(routingModelCode);
    }

    private QueryRoutingDecision routeByLightweightModel(
            String query,
            String routingModelCode,
            BiFunction<String, String, String> routingModelInvoker
    ) {
        String rawOutput = routingModelInvoker.apply(query, routingModelCode);
        if (ROUTING_DECISION_NEED_KB.equals(rawOutput)) {
            return new QueryRoutingDecision(
                    true,
                    "routing-model",
                    "routing-model-classifier",
                    "model",
                    routingModelCode,
                    rawOutput
            );
        }
        if (ROUTING_DECISION_NO_KB.equals(rawOutput)) {
            return new QueryRoutingDecision(
                    false,
                    "routing-model",
                    "routing-model-classifier",
                    "model",
                    routingModelCode,
                    rawOutput
            );
        }
        log.warn(
                "Routing model produced invalid output, fallback to NEED_KB. model={}, rawOutput={}",
                routingModelCode,
                rawOutput
        );
        return new QueryRoutingDecision(
                true,
                "routing-model-fallback",
                "routing-model-invalid-output",
                "model-fallback",
                routingModelCode,
                rawOutput
        );
    }

    private List<Msg> buildRoutingClassifierMessages(String query) {
        return List.of(
                Msg.builder()
                        .name("system")
                        .role(MsgRole.SYSTEM)
                        .textContent(ROUTING_CLASSIFIER_SYSTEM_PROMPT)
                        .build(),
                Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .textContent(buildRoutingClassifierUserPrompt(query))
                        .build()
        );
    }

    private String buildRoutingClassifierUserPrompt(String query) {
        String normalizedQuery = query == null ? "" : query.strip();
        return """
                Query:
                %s

                Classification policy:
                - NEED_KB: The answer needs repository-specific, project-private, or internal factual evidence from knowledge base retrieval.
                - NO_KB: The answer can be completed with generic model capability and conversation context only.

                Output exactly one token: NEED_KB or NO_KB.
                """.formatted(normalizedQuery);
    }

    private String extractRoutingClassifierOutput(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }
        ChatResponse lastResponse = responses.get(responses.size() - 1);
        if (lastResponse.getContent() == null || lastResponse.getContent().isEmpty()) {
            return "";
        }
        StringBuilder textBuilder = new StringBuilder();
        for (var block : lastResponse.getContent()) {
            if (block instanceof TextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isBlank()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append(' ');
                    }
                    textBuilder.append(text.strip());
                }
            }
        }
        return textBuilder.toString().trim();
    }

    String resolveRoutingModel(AgentProfileVersion profile) {
        if (profile == null) {
            throw new IllegalStateException("Agent profile version is required for routing model resolution");
        }
        if (profile.getRoutingModel() == null || profile.getRoutingModel().isBlank()) {
            String profileCode = profile.getProfile() == null ? "unknown" : profile.getProfile().getCode();
            Integer versionNumber = profile.getVersionNumber();
            throw new IllegalStateException(
                    "Agent profile routingModel is blank: profile=" + profileCode + ", version=" + versionNumber
            );
        }
        return profile.getRoutingModel().trim();
    }

    private Pattern safeCompilePattern(String patternText) {
        if (patternText == null || patternText.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(patternText);
        } catch (PatternSyntaxException exception) {
            log.warn("Skip invalid routing regex pattern: {}", patternText, exception);
            return null;
        }
    }
}
