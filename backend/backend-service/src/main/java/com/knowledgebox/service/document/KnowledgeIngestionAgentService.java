package com.knowledgebox.service.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
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
import io.agentscope.core.tool.Toolkit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeIngestionAgentService {

    private static final String SYSTEM_PROMPT = """
            你是一个知识文档整理 Agent。
            你的任务是把用户提供的 Markdown、PDF 提取文本或直接输入内容，整理成适合知识库入库的 Markdown 文档。
            输出必须是 JSON，不要输出任何解释性文字，也不要输出 markdown 代码块。
            JSON 结构固定为：
            {
              "title": "建议标题",
              "category": "建议分类",
              "tags": ["标签1", "标签2"],
              "summary": "1-3 句摘要",
              "reasoning": "一句话说明标题/分类/标签的依据",
              "markdown": "整理后的 Markdown 正文"
            }

            约束：
            1. 若源内容本身就是高质量 Markdown，尽量保留原结构，只做轻量整理。
            2. 若源内容来自 PDF 文本提取，可以重组结构，但不要编造源文未出现的事实。
            3. title 必须简洁明确，避免文件扩展名。
            4. category 只输出一个字符串。
            5. tags 输出 0 到 5 个，去重。
            6. markdown 必须是完整知识文档正文，不能为空。
            7. 不要暴露思维链。
            """;

    private final KnowledgeBoxProperties properties;
    private final KnowledgeIngestionSourceTool sourceTool;
    private final ObjectMapper objectMapper;
    private final String dashScopeApiKey;
    private final String dashScopeBaseUrl;

    public KnowledgeIngestionAgentService(
            KnowledgeBoxProperties properties,
            KnowledgeIngestionSourceTool sourceTool,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:${spring.ai.alibaba.dashscope.api-key:}}") String dashScopeApiKey,
            @Value("${spring.ai.dashscope.base-url:}") String dashScopeBaseUrl
    ) {
        this.properties = properties;
        this.sourceTool = sourceTool;
        this.objectMapper = objectMapper;
        this.dashScopeApiKey = dashScopeApiKey;
        this.dashScopeBaseUrl = dashScopeBaseUrl;
    }

    public DraftAnalysisResult analyze(KnowledgeIngestionDraft draft) {
        String rawOutput;
        try {
            rawOutput = invokeAgent(draft);
        } catch (Exception exception) {
            return fallback(draft);
        }
        DraftAnalysisResult parsed = parse(rawOutput, draft);
        return parsed == null ? fallback(draft) : parsed;
    }

    private String invokeAgent(KnowledgeIngestionDraft draft) {
        Toolkit toolkit = new Toolkit();
        ensureToolGroup(toolkit, "knowledge-ingestion", "Knowledge ingestion source tools");
        toolkit.registration().tool(sourceTool).group("knowledge-ingestion").apply();

        ReActAgent agent = ReActAgent.builder()
                .name("knowledge-ingestion-entry-agent")
                .description("Prepare a knowledge document draft from user-provided source content")
                .sysPrompt(SYSTEM_PROMPT)
                .model(buildModel(resolveModelCode()))
                .toolkit(toolkit)
                .maxIters(4)
                .build();

        List<Event> events = agent.stream(
                        List.of(Msg.builder().role(MsgRole.USER).name("user").textContent(buildUserPrompt(draft)).build()),
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
        StringBuilder output = new StringBuilder();
        for (Event event : events) {
            if (event.getMessage() == null || !StringUtils.hasText(event.getMessage().getTextContent())) {
                continue;
            }
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(event.getMessage().getTextContent().strip());
        }
        return output.toString().trim();
    }

    private String buildUserPrompt(KnowledgeIngestionDraft draft) {
        String sourceFilename = StringUtils.hasText(draft.getSourceFilename()) ? draft.getSourceFilename().trim() : "inline-content.md";
        if (draft.getSourceType() == KnowledgeIngestionDraftSourceType.INLINE) {
            String content = draft.getSourceContent() == null ? "" : draft.getSourceContent().trim();
            if (content.length() > 5000) {
                content = content.substring(0, 5000);
            }
            return """
                    当前草稿 ID：%s
                    来源类型：INLINE
                    源文件名：%s

                    直接输入内容如下：
                    %s

                    请直接整理为知识库文档，并输出约定 JSON。
                    """.formatted(draft.getId(), sourceFilename, content);
        }
        String toolName = draft.getSourceType() == KnowledgeIngestionDraftSourceType.PDF ? "readPdfSource" : "readMarkdownSource";
        return """
                当前草稿 ID：%s
                来源类型：%s
                源文件名：%s

                你必须先调用 %s，参数 draftId=%s，读取源内容后再输出约定 JSON。
                如果读取到的是 Markdown，优先保留原有结构。
                如果读取到的是 PDF 文本提取结果，请整理为更适合阅读的 Markdown。
                """.formatted(
                draft.getId(),
                draft.getSourceType().name(),
                sourceFilename,
                toolName,
                draft.getId()
        );
    }

    private DraftAnalysisResult parse(String rawOutput, KnowledgeIngestionDraft draft) {
        if (!StringUtils.hasText(rawOutput)) {
            return null;
        }
        String candidate = stripCodeFence(rawOutput.trim());
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidate = candidate.substring(start, end + 1);
        }
        try {
            JsonNode root = objectMapper.readTree(candidate);
            if (root == null || !root.isObject()) {
                return null;
            }
            String title = normalizeTitle(text(root.get("title")), draft);
            String category = normalizeOptional(text(root.get("category")));
            List<String> tags = normalizeTags(readTags(root.get("tags")));
            String summary = normalizeOptional(text(root.get("summary")));
            String reasoning = normalizeOptional(text(root.get("reasoning")));
            String markdown = normalizeMarkdown(text(root.get("markdown")), draft, title);
            return new DraftAnalysisResult(title, category, tags, summary, reasoning, markdown);
        } catch (Exception exception) {
            return null;
        }
    }

    private DraftAnalysisResult fallback(KnowledgeIngestionDraft draft) {
        String title = deriveTitle(draft.getSourceFilename(), draft.getSourceContent());
        String markdown = normalizeMarkdown(null, draft, title);
        return new DraftAnalysisResult(
                title,
                "未分类",
                List.of(),
                buildSummary(markdown),
                "回退使用源文件名与正文内容生成默认文档建议。",
                markdown
        );
    }

    private String normalizeMarkdown(String candidate, KnowledgeIngestionDraft draft, String title) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        String source = draft.getSourceContent() == null ? "" : draft.getSourceContent().trim();
        if (draft.getSourceType() == KnowledgeIngestionDraftSourceType.MARKDOWN && StringUtils.hasText(source)) {
            return source;
        }
        if (!StringUtils.hasText(source)) {
            return "# " + title + "\n\n暂无可整理的正文内容。";
        }
        return buildMarkdownFromText(title, source);
    }

    private String buildMarkdownFromText(String title, String source) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String rawLine : source.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                if (paragraph.length() > 0) {
                    paragraphs.add(paragraph.toString().trim());
                    paragraph.setLength(0);
                }
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(line);
        }
        if (paragraph.length() > 0) {
            paragraphs.add(paragraph.toString().trim());
        }
        StringBuilder markdown = new StringBuilder("# ").append(title);
        for (String item : paragraphs) {
            markdown.append("\n\n").append(item);
        }
        return markdown.toString();
    }

    private String buildSummary(String markdown) {
        String normalized = markdown.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String deriveTitle(String sourceFilename, String sourceContent) {
        if (StringUtils.hasText(sourceContent)) {
            for (String line : sourceContent.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
            }
        }
        String fallback = StringUtils.hasText(sourceFilename) ? sourceFilename.trim() : "知识文档";
        return fallback.replaceAll("\\.(md|markdown|pdf|txt)$", "");
    }

    private String normalizeTitle(String candidate, KnowledgeIngestionDraft draft) {
        String resolved = normalizeOptional(candidate);
        if (!StringUtils.hasText(resolved)) {
            return deriveTitle(draft.getSourceFilename(), draft.getSourceContent());
        }
        return resolved.replaceAll("\\.(md|markdown|pdf|txt)$", "");
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeTags(List<String> tags) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                unique.add(tag.trim());
            }
        }
        return unique.stream().limit(5).toList();
    }

    private List<String> readTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : tagsNode) {
            if (tagNode.isTextual() && StringUtils.hasText(tagNode.asText())) {
                tags.add(tagNode.asText().trim());
            }
        }
        return tags;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private String stripCodeFence(String rawText) {
        String normalized = rawText == null ? "" : rawText.trim();
        if (normalized.startsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            if (firstLineEnd >= 0) {
                normalized = normalized.substring(firstLineEnd + 1);
            }
            int closingFence = normalized.lastIndexOf("```");
            if (closingFence >= 0) {
                normalized = normalized.substring(0, closingFence);
            }
        }
        return normalized.trim();
    }

    private String resolveModelCode() {
        String configured = properties.getDocument().getTaxonomy().getModel();
        return StringUtils.hasText(configured) ? configured.trim() : "qwen-max";
    }

    private Model buildModel(String modelCode) {
        if (!StringUtils.hasText(dashScopeApiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY_MISSING", "未配置 DashScope API Key");
        }
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder()
                .modelName(modelCode)
                .stream(Boolean.FALSE)
                .temperature(0.1D)
                .maxTokens(Math.max(1024, properties.getDocument().getTaxonomy().getMaxTokens()));

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
                // ignore invalid regex
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

    private void ensureToolGroup(Toolkit toolkit, String groupName, String description) {
        if (!StringUtils.hasText(groupName) || toolkit.getToolGroup(groupName) != null) {
            return;
        }
        toolkit.createToolGroup(groupName, description);
    }

    public record DraftAnalysisResult(
            String title,
            String categoryName,
            List<String> tagNames,
            String summary,
            String reasoning,
            String markdown
    ) {
    }
}
