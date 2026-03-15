package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.repository.DocumentChunkRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRetrievalService.class);
    private static final Pattern ALPHANUMERIC_TOKEN_PATTERN = Pattern.compile("(?i)[a-z][a-z0-9._-]{1,31}");
    private static final Set<String> QUERY_NOISE_TERMS = Set.of(
            "请",
            "请问",
            "帮我",
            "麻烦",
            "讲",
            "讲讲",
            "讲一下",
            "介绍",
            "介绍下",
            "介绍一下",
            "解释",
            "解释下",
            "解释一下",
            "说明",
            "说明下",
            "说明一下",
            "说",
            "说下",
            "说一下",
            "告诉我",
            "我想知道",
            "想知道",
            "这个",
            "一下",
            "一下子",
            "什么",
            "什么是",
            "是什么",
            "有哪些",
            "有哪一些",
            "有哪几种",
            "怎么",
            "如何",
            "为何",
            "为什么",
            "是否",
            "吗",
            "呢",
            "啊",
            "吧",
            "的",
            "了",
            "模型"
    );
    private static final Set<String> LOW_SIGNAL_TOKENS = Set.of(
            "系统",
            "功能",
            "能力",
            "问题",
            "方法",
            "介绍",
            "说明",
            "项目",
            "知识库",
            "文档"
    );

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeBoxProperties properties;
    private final AgentExecutionTraceService agentExecutionTraceService;

    public KnowledgeBaseRetrievalService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            DocumentChunkRepository documentChunkRepository,
            KnowledgeBoxProperties properties,
            AgentExecutionTraceService agentExecutionTraceService
    ) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.documentChunkRepository = documentChunkRepository;
        this.properties = properties;
        this.agentExecutionTraceService = agentExecutionTraceService;
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(String query, Integer topK) {
        return search(query, topK, null, null);
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(
            String query,
            Integer topK,
            @Nullable AgentExecutionTraceContext traceContext,
            @Nullable String parentCallId
    ) {
        int size = topK == null || topK <= 0 ? properties.getRetrieval().getTopK() : topK;
        List<String> queryVariants = buildQueryVariants(query);
        String callId = null;
        if (traceContext != null) {
            callId = agentExecutionTraceService.nextBackendSpanIdValue();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("topK", size);
            input.put("queryVariants", queryVariants);
            agentExecutionTraceService.startBackendSpan(
                    traceContext,
                    parentCallId,
                    callId,
                    "KnowledgeBaseRetrievalService.search",
                    "SERVICE",
                    getClass().getSimpleName(),
                    "search",
                    input,
                    null
            );
        }
        try {
            List<RetrievedChunk> hits = fusedHits(queryVariants, size);
            completeBackendSpan(traceContext, callId, "fused", hits.size(), queryVariants);
            return hits;
        } catch (RuntimeException exception) {
            if (traceContext != null && callId != null) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("exceptionClass", exception.getClass().getName());
                error.put("message", exception.getMessage());
                agentExecutionTraceService.endBackendSpan(
                        traceContext,
                        callId,
                        AgentExecutionStatus.FAILED,
                        Map.of(),
                        error
                );
            }
            throw exception;
        }
    }

    private void completeBackendSpan(
            AgentExecutionTraceContext traceContext,
            String callId,
            String strategy,
            int hits,
            List<String> queryVariants
    ) {
        if (traceContext == null || callId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("strategy", strategy);
        output.put("hits", hits);
        output.put("queryVariants", queryVariants);
        agentExecutionTraceService.endBackendSpan(
                traceContext,
                callId,
                AgentExecutionStatus.COMPLETED,
                output,
                null
        );
    }

    private List<RetrievedChunk> fusedHits(List<String> queryVariants, int topK) {
        if (queryVariants.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, RankedChunk> ranked = new LinkedHashMap<>();
        for (String vectorQuery : vectorQueries(queryVariants)) {
            mergeHits(ranked, safeVectorHits(vectorQuery, topK), "vector", vectorQuery);
        }
        for (String textQuery : queryVariants) {
            mergeHits(ranked, safeTextHits(textQuery, topK), "text", textQuery);
        }
        mergeHits(ranked, inMemoryHits(queryVariants, topK), "memory", queryVariants.get(0));
        return ranked.values().stream()
                .filter(RankedChunk::publicVisible)
                .filter(RankedChunk::relevant)
                .sorted(Comparator
                        .comparingDouble(RankedChunk::fusedScore).reversed()
                        .thenComparing(RankedChunk::documentTitle)
                        .thenComparing(RankedChunk::anchor))
                .limit(topK)
                .map(RankedChunk::toRetrievedChunk)
                .toList();
    }

    private void mergeHits(
            Map<String, RankedChunk> ranked,
            List<RetrievedChunk> hits,
            String strategy,
            String queryVariant
    ) {
        for (RetrievedChunk hit : hits) {
            String key = hit.documentId() + "::" + hit.anchor();
            RankedChunk rankedChunk = ranked.computeIfAbsent(key, ignored -> new RankedChunk(hit));
            rankedChunk.merge(hit, strategy, queryVariant, scoreContribution(hit, strategy, queryVariant));
        }
    }

    private double scoreContribution(RetrievedChunk hit, String strategy, String queryVariant) {
        double lexicalSignal = lexicalSignalScore(hit, queryVariant);
        return switch (strategy) {
            case "vector" -> 120D + Math.max(hit.score(), 0D) * 100D + lexicalSignal;
            case "text" -> 95D + lexicalSignal;
            case "memory" -> 60D + Math.max(hit.score(), 0D) * 18D + lexicalSignal;
            default -> lexicalSignal;
        };
    }

    private List<RetrievedChunk> safeVectorHits(String query, int topK) {
        try {
            return vectorHits(query, topK);
        } catch (RuntimeException exception) {
            log.warn("Vector retrieval failed, falling back to text search. query={}", query, exception);
            return List.of();
        }
    }

    private List<RetrievedChunk> safeTextHits(String query, int topK) {
        try {
            return documentChunkRepository.searchByText(query, topK).stream()
                    .map(chunk -> toRetrievedChunk(chunk, 0.0D))
                    .filter(RetrievedChunk::publicVisible)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("PostgreSQL text retrieval failed, falling back to in-memory search. query={}", query, exception);
            return List.of();
        }
    }

    private List<RetrievedChunk> vectorHits(String query, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(properties.getRetrieval().getSimilarityThreshold())
                .build());
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .map(document -> {
                    Map<String, Object> metadata = document.getMetadata();
                    return new RetrievedChunk(
                            longValue(metadata.get("documentId")),
                            String.valueOf(metadata.getOrDefault("documentTitle", "知识文档")),
                            String.valueOf(metadata.getOrDefault("headingPath", "未分节")),
                            String.valueOf(metadata.getOrDefault("anchor", document.getId())),
                            snippet(document.getText()),
                            document.getScore() == null ? 0.0D : document.getScore(),
                            !"AGENT_ONLY".equals(String.valueOf(metadata.getOrDefault("visibilityType", "PUBLIC")))
                    );
                })
                .filter(RetrievedChunk::publicVisible)
                .toList();
    }

    public String renderToolPayload(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "未检索到匹配的知识片段。";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            builder.append(index + 1)
                    .append(". [")
                    .append(chunk.documentTitle())
                    .append("] ")
                    .append(chunk.headingPath())
                    .append(" #")
                    .append(chunk.anchor())
                    .append('\n')
                    .append(chunk.snippet())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    public org.springframework.ai.document.Document toVectorDocument(DocumentChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", chunk.getDocument().getId());
        metadata.put("documentTitle", chunk.getDocument().getTitle());
        metadata.put("headingPath", chunk.getHeadingPath());
        metadata.put("anchor", chunk.getAnchor());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        DocumentVisibilityType visibilityType = chunk.getDocument().getVisibilityType() == null
                ? DocumentVisibilityType.PUBLIC
                : chunk.getDocument().getVisibilityType();
        metadata.put("visibilityType", visibilityType.name());
        return org.springframework.ai.document.Document.builder()
                .id(vectorDocumentId(chunk.getId()))
                .text(chunk.getContent())
                .metadata(metadata)
                .build();
    }

    public String vectorDocumentId(Long chunkId) {
        return "chunk-" + chunkId;
    }

    private List<RetrievedChunk> inMemoryHits(List<String> queryVariants, int topK) {
        List<String> keywords = keywords(queryVariants);
        if (keywords.isEmpty()) {
            return List.of();
        }
        return documentChunkRepository.findAllWithDocument().stream()
                .filter(chunk -> visibilityType(chunk) != DocumentVisibilityType.AGENT_ONLY)
                .map(chunk -> new ScoredChunk(chunk, score(chunk, keywords)))
                .filter(scored -> scored.score() > 0D)
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(topK)
                .map(scored -> toRetrievedChunk(scored.chunk(), scored.score()))
                .toList();
    }

    private List<String> keywords(List<String> queryVariants) {
        Set<String> words = new HashSet<>();
        for (String variant : queryVariants) {
            String normalized = normalizeQuery(variant);
            if (normalized.isBlank()) {
                continue;
            }
            Arrays.stream(normalized.split("\\s+"))
                    .map(String::trim)
                    .filter(token -> !token.isBlank())
                    .filter(this::isSignalToken)
                    .forEach(token -> {
                        words.add(token);
                        if (isChineseToken(token) && token.length() > 4) {
                            words.addAll(bigrams(token));
                        }
                    });
        }
        return words.stream().sorted().toList();
    }

    private List<String> bigrams(String token) {
        List<String> grams = new java.util.ArrayList<>();
        for (int index = 0; index < token.length() - 1; index++) {
            grams.add(token.substring(index, index + 2));
        }
        return grams;
    }

    private double score(DocumentChunk chunk, List<String> keywords) {
        String title = lower(chunk.getDocument().getTitle());
        String heading = lower(chunk.getHeadingPath());
        String content = lower(chunk.getContent());
        double score = 0D;
        for (String keyword : keywords) {
            String normalizedKeyword = keyword.toLowerCase();
            if (containsToken(title, normalizedKeyword)) {
                score += isChineseToken(normalizedKeyword) ? 5D : 7D;
            }
            if (containsToken(heading, normalizedKeyword)) {
                score += isChineseToken(normalizedKeyword) ? 4D : 6D;
            }
            if (containsToken(content, normalizedKeyword)) {
                score += isChineseToken(normalizedKeyword) ? 2D : 3D;
            }
        }
        return score;
    }

    private RetrievedChunk toRetrievedChunk(DocumentChunk chunk, double score) {
        return new RetrievedChunk(
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getHeadingPath(),
                chunk.getAnchor(),
                snippet(chunk.getContent()),
                score,
                visibilityType(chunk) != DocumentVisibilityType.AGENT_ONLY
        );
    }

    private DocumentVisibilityType visibilityType(DocumentChunk chunk) {
        return chunk.getDocument().getVisibilityType() == null
                ? DocumentVisibilityType.PUBLIC
                : chunk.getDocument().getVisibilityType();
    }

    private List<String> buildQueryVariants(String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> variants = new ArrayList<>();
        addVariant(variants, normalized);
        String stripped = stripQueryNoise(normalized);
        addVariant(variants, stripped);
        Matcher matcher = ALPHANUMERIC_TOKEN_PATTERN.matcher(normalized);
        List<String> asciiTokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (isSignalToken(token)) {
                asciiTokens.add(token);
                addVariant(variants, token);
            }
        }
        if (!asciiTokens.isEmpty()) {
            addVariant(variants, String.join(" ", asciiTokens));
        }
        return variants;
    }

    private List<String> vectorQueries(List<String> queryVariants) {
        if (queryVariants.isEmpty()) {
            return List.of();
        }
        List<String> vectorQueries = new ArrayList<>();
        addVariant(vectorQueries, queryVariants.get(0));
        queryVariants.stream()
                .filter(variant -> variant.indexOf(' ') >= 0 || isAsciiLike(variant))
                .forEach(variant -> addVariant(vectorQueries, variant));
        return vectorQueries;
    }

    private String stripQueryNoise(String query) {
        String stripped = query;
        for (String term : QUERY_NOISE_TERMS) {
            stripped = stripped.replace(term, " ");
        }
        return normalizeQuery(stripped);
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private void addVariant(List<String> variants, String candidate) {
        String normalized = normalizeQuery(candidate);
        if (normalized.isBlank()) {
            return;
        }
        if (variants.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
            variants.add(normalized);
        }
    }

    private double lexicalSignalScore(RetrievedChunk hit, String queryVariant) {
        String normalized = normalizeQuery(queryVariant);
        if (normalized.isBlank()) {
            return 0D;
        }
        double score = 0D;
        for (String token : normalized.split("\\s+")) {
            if (!isSignalToken(token)) {
                continue;
            }
            String loweredToken = token.toLowerCase();
            if (containsToken(lower(hit.documentTitle()), loweredToken)) {
                score += isChineseToken(loweredToken) ? 18D : 28D;
            }
            if (containsToken(lower(hit.headingPath()), loweredToken)) {
                score += isChineseToken(loweredToken) ? 12D : 22D;
            }
            if (containsToken(lower(hit.snippet()), loweredToken)) {
                score += isChineseToken(loweredToken) ? 6D : 12D;
            }
        }
        return score;
    }

    private boolean isSignalToken(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase();
        if (normalized.isBlank() || QUERY_NOISE_TERMS.contains(normalized)) {
            return false;
        }
        if (isChineseToken(normalized)) {
            return normalized.length() >= 2 && !LOW_SIGNAL_TOKENS.contains(normalized);
        }
        return normalized.length() >= 2;
    }

    private boolean containsToken(String haystack, String token) {
        if (haystack == null || haystack.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        if (isChineseToken(token)) {
            return haystack.contains(token);
        }
        return Pattern.compile("(?i)(^|[^a-z0-9])" + Pattern.quote(token) + "([^a-z0-9]|$)")
                .matcher(haystack)
                .find();
    }

    private boolean isChineseToken(String token) {
        return token != null && !token.isBlank() && token.codePoints().allMatch(code -> code >= 0x4E00 && code <= 0x9FA5);
    }

    private boolean isAsciiLike(String token) {
        return token != null && !token.isBlank() && token.codePoints().allMatch(code -> code < 128 && !Character.isWhitespace(code));
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String snippet(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }

    private static final class RankedChunk {
        private final Long documentId;
        private final String documentTitle;
        private final String headingPath;
        private final String anchor;
        private final String snippet;
        private final boolean publicVisible;
        private double fusedScore;
        private double bestVectorScore;
        private double bestLexicalScore;

        private RankedChunk(RetrievedChunk chunk) {
            this.documentId = chunk.documentId();
            this.documentTitle = chunk.documentTitle();
            this.headingPath = chunk.headingPath();
            this.anchor = chunk.anchor();
            this.snippet = chunk.snippet();
            this.publicVisible = chunk.publicVisible();
        }

        private void merge(RetrievedChunk chunk, String strategy, String queryVariant, double contribution) {
            this.fusedScore = Math.max(this.fusedScore, contribution);
            this.bestLexicalScore = Math.max(this.bestLexicalScore, contribution - baseScore(strategy, chunk.score()));
            if ("vector".equals(strategy)) {
                this.bestVectorScore = Math.max(this.bestVectorScore, chunk.score());
            }
        }

        private boolean relevant() {
            boolean strongLexical = bestLexicalScore >= 10D;
            boolean strongVector = bestVectorScore >= 0.55D;
            return strongLexical || strongVector;
        }

        private Long documentId() {
            return documentId;
        }

        private String documentTitle() {
            return documentTitle == null ? "" : documentTitle;
        }

        private String anchor() {
            return anchor == null ? "" : anchor;
        }

        private boolean publicVisible() {
            return publicVisible;
        }

        private double fusedScore() {
            return fusedScore;
        }

        private RetrievedChunk toRetrievedChunk() {
            return new RetrievedChunk(documentId, documentTitle, headingPath, anchor, snippet, fusedScore, publicVisible);
        }

        private double baseScore(String strategy, double modelScore) {
            return switch (strategy) {
                case "vector" -> 120D + Math.max(modelScore, 0D) * 100D;
                case "text" -> 95D;
                case "memory" -> 60D + Math.max(modelScore, 0D) * 18D;
                default -> 0D;
            };
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {
    }
}
