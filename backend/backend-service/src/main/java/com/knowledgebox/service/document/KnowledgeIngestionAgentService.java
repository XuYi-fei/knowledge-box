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
import java.util.Comparator;
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

    private static final String DRAFT_SYSTEM_PROMPT = """
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

    private static final String LARGE_PDF_PLAN_SYSTEM_PROMPT = """
            你是一个大型 PDF 拆解规划 Agent。
            你会收到 PDF 文件名与逐页摘要，目标是规划出适合知识库入库的多个知识文档。
            输出必须是 JSON，不要输出任何解释文字，不要输出 markdown 代码块。
            JSON 结构固定为：
            {
              "documents": [
                {
                  "pageFrom": 1,
                  "pageTo": 12,
                  "title": "文档标题",
                  "category": "建议分类",
                  "tags": ["标签1", "标签2"],
                  "summary": "1-2 句摘要",
                  "reasoning": "一句话说明为什么这样切分"
                }
              ]
            }

            约束：
            1. 必须覆盖全部页码，且各分段连续、不重叠。
            2. 文档数量控制在合理范围内，尽量按主题切分；同主题文档分类尽量一致。
            3. title 要简洁明确，不要带文件扩展名。
            4. category 只输出一个字符串，可为空字符串。
            5. tags 每篇 0 到 5 个，去重。
            6. 若预览显示内容整体属于同一主题，可以减少拆分数量。
            7. 不要编造页码范围之外的信息。
            """;

    private static final String LARGE_PDF_GENERATE_SYSTEM_PROMPT = """
            你是一个知识文档整理 Agent。
            你的任务是把大型 PDF 中的某一段页码内容整理为适合知识库入库的 Markdown 文档。
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
            1. 只能基于提供的页码文本整理，不要编造未出现的事实。
            2. 尽量保留原有层次结构，并转换为更适合阅读的 Markdown。
            3. title 必须简洁明确，避免文件扩展名。
            4. category 只输出一个字符串，可为空字符串。
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
        try {
            GeneratedDocument generated = parseGeneratedDocument(
                    invokeAgent(DRAFT_SYSTEM_PROMPT, buildDraftUserPrompt(draft), buildDraftToolkit()),
                    draft.getSourceFilename(),
                    draft.getSourceContent(),
                    draft.getSourceType() == KnowledgeIngestionDraftSourceType.MARKDOWN,
                    null
            );
            if (generated != null) {
                return new DraftAnalysisResult(
                        generated.title(),
                        generated.categoryName(),
                        generated.tagNames(),
                        generated.summary(),
                        generated.reasoning(),
                        generated.markdown()
                );
            }
        } catch (Exception ignore) {
            // fall back below
        }
        GeneratedDocument fallback = fallbackGeneratedDocument(
                draft.getSourceFilename(),
                draft.getSourceContent(),
                draft.getSourceType() == KnowledgeIngestionDraftSourceType.MARKDOWN,
                null
        );
        return new DraftAnalysisResult(
                fallback.title(),
                fallback.categoryName(),
                fallback.tagNames(),
                fallback.summary(),
                fallback.reasoning(),
                fallback.markdown()
        );
    }

    public List<PlannedDocument> planLargePdfDocuments(String sourceFilename, List<LargePdfPagePreview> pagePreviews) {
        try {
            List<PlannedDocument> plans = parsePlans(
                    invokeAgent(LARGE_PDF_PLAN_SYSTEM_PROMPT, buildLargePdfPlanPrompt(sourceFilename, pagePreviews), null),
                    sourceFilename,
                    pagePreviews.size()
            );
            if (!plans.isEmpty()) {
                return plans;
            }
        } catch (Exception ignore) {
            // fall back below
        }
        return fallbackPlans(sourceFilename, pagePreviews.size());
    }

    public GeneratedDocument generateLargePdfDocument(
            String sourceFilename,
            Integer pageFromNumber,
            Integer pageToNumber,
            String plannedTitle,
            String segmentText
    ) {
        try {
            GeneratedDocument generated = parseGeneratedDocument(
                    invokeAgent(
                            LARGE_PDF_GENERATE_SYSTEM_PROMPT,
                            buildLargePdfGeneratePrompt(sourceFilename, pageFromNumber, pageToNumber, plannedTitle, segmentText),
                            null
                    ),
                    sourceFilename,
                    segmentText,
                    false,
                    plannedTitle
            );
            if (generated != null) {
                return generated;
            }
        } catch (Exception ignore) {
            // fall back below
        }
        return fallbackGeneratedDocument(sourceFilename, segmentText, false, plannedTitle);
    }

    private Toolkit buildDraftToolkit() {
        Toolkit toolkit = new Toolkit();
        ensureToolGroup(toolkit, "knowledge-ingestion", "Knowledge ingestion source tools");
        toolkit.registration().tool(sourceTool).group("knowledge-ingestion").apply();
        return toolkit;
    }

    private String invokeAgent(String systemPrompt, String userPrompt, Toolkit toolkit) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("knowledge-ingestion-agent")
                .description("Prepare knowledge ingestion output")
                .sysPrompt(systemPrompt)
                .model(buildModel(resolveModelCode()))
                .maxIters(toolkit == null ? 1 : 4);
        if (toolkit != null) {
            builder.toolkit(toolkit);
        }
        ReActAgent agent = builder.build();
        List<Event> events = agent.stream(
                        List.of(Msg.builder().role(MsgRole.USER).name("user").textContent(userPrompt).build()),
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

    private String buildDraftUserPrompt(KnowledgeIngestionDraft draft) {
        String sourceFilename = StringUtils.hasText(draft.getSourceFilename()) ? draft.getSourceFilename().trim() : "inline-content.md";
        if (draft.getSourceType() == KnowledgeIngestionDraftSourceType.INLINE) {
            String content = abbreviate(draft.getSourceContent(), 5000);
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

    private String buildLargePdfPlanPrompt(String sourceFilename, List<LargePdfPagePreview> pagePreviews) {
        StringBuilder previewBuilder = new StringBuilder();
        for (LargePdfPagePreview preview : pagePreviews) {
            previewBuilder.append("- 第 ").append(preview.pageNumber()).append(" 页：")
                    .append(StringUtils.hasText(preview.preview()) ? preview.preview() : "（无明显文本）")
                    .append('\n');
        }
        return """
                文件名：%s
                总页数：%s
                最多建议生成文档数：%s

                各页摘要如下：
                %s

                请输出约定 JSON。
                """.formatted(
                StringUtils.hasText(sourceFilename) ? sourceFilename.trim() : "document.pdf",
                pagePreviews.size(),
                Math.max(1, properties.getDocument().getIngestion().getMaxGeneratedDocuments()),
                previewBuilder.toString().trim()
        );
    }

    private String buildLargePdfGeneratePrompt(
            String sourceFilename,
            Integer pageFromNumber,
            Integer pageToNumber,
            String plannedTitle,
            String segmentText
    ) {
        return """
                文件名：%s
                页码范围：%s - %s
                规划标题：%s

                当前页码文本如下：
                %s

                请将这段内容整理为知识库 Markdown 文档，并输出约定 JSON。
                """.formatted(
                StringUtils.hasText(sourceFilename) ? sourceFilename.trim() : "document.pdf",
                pageFromNumber == null ? 1 : pageFromNumber,
                pageToNumber == null ? pageFromNumber : pageToNumber,
                StringUtils.hasText(plannedTitle) ? plannedTitle.trim() : "（可自行优化标题）",
                abbreviate(segmentText, 40000)
        );
    }

    private GeneratedDocument parseGeneratedDocument(
            String rawOutput,
            String sourceFilename,
            String sourceContent,
            boolean preserveMarkdown,
            String preferredTitle
    ) {
        JsonNode root = parseJsonRoot(rawOutput);
        if (root == null || !root.isObject()) {
            return null;
        }
        String title = normalizeTitle(text(root.get("title")), sourceFilename, sourceContent, preferredTitle);
        String category = normalizeOptional(text(root.get("category")));
        List<String> tags = normalizeTags(readTags(root.get("tags")));
        String summary = normalizeOptional(text(root.get("summary")));
        String reasoning = normalizeOptional(text(root.get("reasoning")));
        String markdown = normalizeMarkdown(text(root.get("markdown")), sourceFilename, sourceContent, preserveMarkdown, title);
        return new GeneratedDocument(title, category, tags, summary, reasoning, markdown);
    }

    private List<PlannedDocument> parsePlans(String rawOutput, String sourceFilename, int totalPages) {
        JsonNode root = parseJsonRoot(rawOutput);
        if (root == null) {
            return List.of();
        }
        JsonNode documentsNode = root.isArray() ? root : root.get("documents");
        if (documentsNode == null || !documentsNode.isArray()) {
            return List.of();
        }
        List<PlannedDocument> plans = new ArrayList<>();
        int maxDocuments = Math.max(1, properties.getDocument().getIngestion().getMaxGeneratedDocuments());
        for (JsonNode item : documentsNode) {
            if (!item.isObject()) {
                continue;
            }
            Integer pageFrom = intValue(item.get("pageFrom"));
            Integer pageTo = intValue(item.get("pageTo"));
            if (pageFrom == null || pageTo == null || pageFrom < 1 || pageTo < pageFrom || pageTo > totalPages) {
                return List.of();
            }
            String title = normalizeTitle(text(item.get("title")), sourceFilename, null, null);
            String category = normalizeOptional(text(item.get("category")));
            List<String> tags = normalizeTags(readTags(item.get("tags")));
            String summary = normalizeOptional(text(item.get("summary")));
            String reasoning = normalizeOptional(text(item.get("reasoning")));
            plans.add(new PlannedDocument(0, pageFrom, pageTo, title, category, tags, summary, reasoning));
            if (plans.size() > maxDocuments) {
                return List.of();
            }
        }
        if (plans.isEmpty()) {
            return List.of();
        }
        plans.sort(Comparator.comparingInt(PlannedDocument::pageFromNumber));
        List<PlannedDocument> normalized = new ArrayList<>(plans.size());
        int expectedFrom = 1;
        for (int index = 0; index < plans.size(); index++) {
            PlannedDocument plan = plans.get(index);
            if (plan.pageFromNumber() != expectedFrom) {
                return List.of();
            }
            normalized.add(new PlannedDocument(
                    index + 1,
                    plan.pageFromNumber(),
                    plan.pageToNumber(),
                    plan.title(),
                    plan.categoryName(),
                    plan.tagNames(),
                    plan.summary(),
                    plan.reasoning()
            ));
            expectedFrom = plan.pageToNumber() + 1;
        }
        return expectedFrom == totalPages + 1 ? normalized : List.of();
    }

    private JsonNode parseJsonRoot(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            return null;
        }
        String candidate = stripCodeFence(rawOutput.trim());
        int objectStart = candidate.indexOf('{');
        int objectEnd = candidate.lastIndexOf('}');
        int arrayStart = candidate.indexOf('[');
        int arrayEnd = candidate.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart && (arrayStart < 0 || objectStart < arrayStart)) {
            candidate = candidate.substring(objectStart, objectEnd + 1);
        } else if (arrayStart >= 0 && arrayEnd > arrayStart) {
            candidate = candidate.substring(arrayStart, arrayEnd + 1);
        }
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<PlannedDocument> fallbackPlans(String sourceFilename, int totalPages) {
        int maxDocuments = Math.max(1, properties.getDocument().getIngestion().getMaxGeneratedDocuments());
        int preferredBatchSize = Math.max(1, properties.getDocument().getIngestion().getPageBatchSize());
        int chunkSize = Math.max(preferredBatchSize, (int) Math.ceil(totalPages / (double) maxDocuments));
        String baseTitle = stripFileExtension(sourceFilename);
        List<PlannedDocument> plans = new ArrayList<>();
        int segmentIndex = 1;
        for (int pageFrom = 1; pageFrom <= totalPages; pageFrom += chunkSize) {
            int pageTo = Math.min(totalPages, pageFrom + chunkSize - 1);
            String title = baseTitle + "（第 " + pageFrom + "-" + pageTo + " 页）";
            plans.add(new PlannedDocument(
                    segmentIndex++,
                    pageFrom,
                    pageTo,
                    title,
                    null,
                    List.of(),
                    "整理第 " + pageFrom + " 到第 " + pageTo + " 页的核心内容。",
                    "回退按页码区间均匀拆分大 PDF。"
            ));
        }
        return plans;
    }

    private GeneratedDocument fallbackGeneratedDocument(
            String sourceFilename,
            String sourceContent,
            boolean preserveMarkdown,
            String preferredTitle
    ) {
        String title = normalizeTitle(null, sourceFilename, sourceContent, preferredTitle);
        String markdown = normalizeMarkdown(null, sourceFilename, sourceContent, preserveMarkdown, title);
        return new GeneratedDocument(
                title,
                "未分类",
                List.of(),
                buildSummary(markdown),
                "回退使用文件名与正文内容生成默认文档建议。",
                markdown
        );
    }

    private String normalizeMarkdown(
            String candidate,
            String sourceFilename,
            String sourceContent,
            boolean preserveMarkdown,
            String title
    ) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        String source = sourceContent == null ? "" : sourceContent.trim();
        if (preserveMarkdown && StringUtils.hasText(source)) {
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

    private String normalizeTitle(String candidate, String sourceFilename, String sourceContent, String preferredTitle) {
        String resolved = normalizeOptional(candidate);
        if (!StringUtils.hasText(resolved) && StringUtils.hasText(preferredTitle)) {
            resolved = preferredTitle.trim();
        }
        if (!StringUtils.hasText(resolved)) {
            resolved = deriveTitle(sourceFilename, sourceContent);
        }
        return stripFileExtension(resolved);
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
        return stripFileExtension(StringUtils.hasText(sourceFilename) ? sourceFilename.trim() : "知识文档");
    }

    private String stripFileExtension(String value) {
        String resolved = StringUtils.hasText(value) ? value.trim() : "知识文档";
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

    private Integer intValue(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.asInt() : null;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private String abbreviate(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
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

    public record LargePdfPagePreview(
            int pageNumber,
            String preview
    ) {
    }

    public record PlannedDocument(
            int segmentIndex,
            int pageFromNumber,
            int pageToNumber,
            String title,
            String categoryName,
            List<String> tagNames,
            String summary,
            String reasoning
    ) {
    }

    public record GeneratedDocument(
            String title,
            String categoryName,
            List<String> tagNames,
            String summary,
            String reasoning,
            String markdown
    ) {
    }
}
