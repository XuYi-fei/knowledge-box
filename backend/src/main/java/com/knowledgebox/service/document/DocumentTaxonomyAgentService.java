package com.knowledgebox.service.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentTaxonomyAgentService {

    private static final String TAXONOMY_SYSTEM_PROMPT = """
            你是一个知识文档分类与标签助手。
            只输出 JSON，不要输出任何解释性文本。
            JSON 结构固定为：
            {"category":"分类名","tags":["标签1","标签2"],"reasoning":"一句话理由"}
            要求：
            1) category 只能输出一个字符串。
            2) tags 输出 0~5 个标签，去重。
            3) 若现有分类/标签不合适，可以提出新分类/新标签名称。
            4) 不要输出 markdown 代码块。
            """;

    private final KnowledgeBoxProperties properties;
    private final ObjectMapper objectMapper;
    private final String dashScopeApiKey;
    private final String dashScopeBaseUrl;

    public DocumentTaxonomyAgentService(
            KnowledgeBoxProperties properties,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:${spring.ai.alibaba.dashscope.api-key:}}") String dashScopeApiKey,
            @Value("${spring.ai.dashscope.base-url:}") String dashScopeBaseUrl
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dashScopeApiKey = dashScopeApiKey;
        this.dashScopeBaseUrl = dashScopeBaseUrl;
    }

    public TaxonomySuggestion suggest(
            String markdown,
            List<String> existingCategories,
            List<String> existingTags
    ) {
        if (!StringUtils.hasText(markdown)) {
            return fallback(existingCategories, existingTags);
        }
        String modelCode = properties.getDocument().getTaxonomy().getModel();
        String userPrompt = buildUserPrompt(markdown, existingCategories, existingTags);
        String raw;
        try {
            raw = invokeAgent(modelCode, userPrompt);
        } catch (Exception exception) {
            return fallback(existingCategories, existingTags);
        }
        TaxonomySuggestion parsed = parse(raw, existingCategories, existingTags);
        if (parsed != null) {
            return parsed;
        }
        return fallback(existingCategories, existingTags);
    }

    private String invokeAgent(String modelCode, String userPrompt) {
        ReActAgent agent = ReActAgent.builder()
                .name("document-taxonomy-agent")
                .description("Suggest one category and multiple tags for a document")
                .sysPrompt(TAXONOMY_SYSTEM_PROMPT)
                .model(buildModel(modelCode))
                .maxIters(2)
                .build();

        List<Event> events = agent.stream(
                        List.of(
                                Msg.builder().role(MsgRole.USER).name("user").textContent(userPrompt).build()
                        ),
                        StreamOptions.builder()
                                .eventTypes(EventType.ALL, EventType.AGENT_RESULT, EventType.SUMMARY)
                                .incremental(true)
                                .includeSummaryChunk(true)
                                .includeSummaryResult(true)
                                .build()
                )
                .collectList()
                .block();
        if (events == null || events.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Event event : events) {
            if (event.getMessage() == null) {
                continue;
            }
            String text = event.getMessage().getTextContent();
            if (StringUtils.hasText(text)) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text.strip());
            }
        }
        return builder.toString().trim();
    }

    private Model buildModel(String modelCode) {
        if (!StringUtils.hasText(dashScopeApiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY_MISSING", "未配置 DashScope API Key");
        }
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder()
                .modelName(modelCode)
                .stream(Boolean.FALSE)
                .temperature(0D)
                .maxTokens(properties.getDocument().getTaxonomy().getMaxTokens());

        if (shouldUseDashScopeCompatible(modelCode)) {
            optionsBuilder.additionalBodyParam("enable_thinking", false);
            return OpenAIChatModel.builder()
                    .apiKey(dashScopeApiKey)
                    .baseUrl(resolveCompatibleBaseUrl())
                    .modelName(modelCode)
                    .stream(false)
                    .generateOptions(optionsBuilder.build())
                    .build();
        }

        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelCode)
                .stream(false)
                .enableThinking(Boolean.FALSE)
                .defaultOptions(optionsBuilder.build());
        if (StringUtils.hasText(dashScopeBaseUrl)) {
            builder.baseUrl(dashScopeBaseUrl.trim());
        }
        return builder.build();
    }

    private boolean shouldUseDashScopeCompatible(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            return false;
        }
        List<String> regexes = properties.getChat().getDashScopeCompatible().getForceModelRegexes();
        if (regexes == null || regexes.isEmpty()) {
            return false;
        }
        for (String regex : regexes) {
            if (!StringUtils.hasText(regex)) {
                continue;
            }
            try {
                if (Pattern.compile(regex).matcher(modelCode).matches()) {
                    return true;
                }
            } catch (PatternSyntaxException ignore) {
                // ignore invalid pattern and continue
            }
        }
        return false;
    }

    private String resolveCompatibleBaseUrl() {
        String configured = properties.getChat().getDashScopeCompatible().getBaseUrl();
        if (!StringUtils.hasText(configured)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return configured.trim();
    }

    private String buildUserPrompt(String markdown, List<String> existingCategories, List<String> existingTags) {
        String content = markdown.strip();
        if (content.length() > 4500) {
            content = content.substring(0, 4500);
        }
        return """
                文档内容：
                %s

                当前已有分类：
                %s

                当前已有标签：
                %s
                """.formatted(
                content,
                String.join(" | ", existingCategories == null ? List.of() : existingCategories),
                String.join(" | ", existingTags == null ? List.of() : existingTags)
        );
    }

    private TaxonomySuggestion parse(String raw, List<String> existingCategories, List<String> existingTags) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = extractJson(raw);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String category = text(node.get("category"));
            String reasoning = text(node.get("reasoning"));
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            JsonNode tagsNode = node.get("tags");
            if (tagsNode != null && tagsNode.isArray()) {
                for (JsonNode item : tagsNode) {
                    String tag = text(item);
                    if (StringUtils.hasText(tag)) {
                        tags.add(tag);
                    }
                }
            }
            if (!StringUtils.hasText(category)) {
                category = chooseFallbackCategory(existingCategories);
            }
            int maxTags = Math.max(1, properties.getDocument().getTaxonomy().getMaxTags());
            List<String> trimmedTags = new ArrayList<>(tags).stream()
                    .limit(maxTags)
                    .toList();
            if (trimmedTags.isEmpty()) {
                trimmedTags = chooseFallbackTags(existingTags);
            }
            return new TaxonomySuggestion(category, trimmedTags, reasoning, raw);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String extractJson(String text) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace("```json", "").replace("```", "").strip();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return cleaned.substring(start, end + 1);
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private TaxonomySuggestion fallback(List<String> existingCategories, List<String> existingTags) {
        return new TaxonomySuggestion(
                chooseFallbackCategory(existingCategories),
                chooseFallbackTags(existingTags),
                "fallback",
                ""
        );
    }

    private String chooseFallbackCategory(List<String> existingCategories) {
        if (existingCategories != null) {
            for (String category : existingCategories) {
                if (StringUtils.hasText(category)) {
                    return category.trim();
                }
            }
        }
        return "未分类";
    }

    private List<String> chooseFallbackTags(List<String> existingTags) {
        if (existingTags != null && !existingTags.isEmpty()) {
            return existingTags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(Math.max(1, properties.getDocument().getTaxonomy().getMaxTags()))
                    .toList();
        }
        return List.of("待确认");
    }

    public record TaxonomySuggestion(
            String categoryName,
            List<String> tagNames,
            String reasoning,
            String rawOutput
    ) {
    }
}
