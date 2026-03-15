package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.repository.DocumentChunkRepository;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class KnowledgeBaseRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRetrievalService.class);

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
        String callId = null;
        if (traceContext != null) {
            callId = agentExecutionTraceService.nextBackendSpanIdValue();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("topK", size);
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
            List<RetrievedChunk> vectorHits = safeVectorHits(query, size);
            if (!vectorHits.isEmpty()) {
                completeBackendSpan(traceContext, callId, "vector", vectorHits.size());
                return vectorHits;
            }
            List<RetrievedChunk> textHits = safeTextHits(query, size);
            if (!textHits.isEmpty()) {
                completeBackendSpan(traceContext, callId, "text", textHits.size());
                return textHits;
            }
            List<RetrievedChunk> memoryHits = inMemoryHits(query, size);
            completeBackendSpan(traceContext, callId, "memory", memoryHits.size());
            return memoryHits;
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

    private void completeBackendSpan(AgentExecutionTraceContext traceContext, String callId, String strategy, int hits) {
        if (traceContext == null || callId == null) {
            return;
        }
        agentExecutionTraceService.endBackendSpan(
                traceContext,
                callId,
                AgentExecutionStatus.COMPLETED,
                Map.of("strategy", strategy, "hits", hits),
                null
        );
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
                    .map(chunk -> new RetrievedChunk(
                            chunk.getDocument().getId(),
                            chunk.getDocument().getTitle(),
                            chunk.getHeadingPath(),
                            chunk.getAnchor(),
                            snippet(chunk.getContent()),
                            0.0D,
                            chunk.getDocument().getVisibilityType() == null
                                    || chunk.getDocument().getVisibilityType() != DocumentVisibilityType.AGENT_ONLY
                    ))
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

    private List<RetrievedChunk> inMemoryHits(String query, int topK) {
        List<String> keywords = keywords(query);
        return documentChunkRepository.findAll().stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, keywords)))
                .filter(scored -> scored.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(topK)
                .map(scored -> new RetrievedChunk(
                        scored.chunk().getDocument().getId(),
                        scored.chunk().getDocument().getTitle(),
                        scored.chunk().getHeadingPath(),
                        scored.chunk().getAnchor(),
                        snippet(scored.chunk().getContent()),
                        scored.score(),
                        scored.chunk().getDocument().getVisibilityType() == null
                                || scored.chunk().getDocument().getVisibilityType() != DocumentVisibilityType.AGENT_ONLY
                ))
                .toList();
    }

    private List<String> keywords(String query) {
        String normalized = query == null ? "" : query.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> words = java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .flatMap(token -> token.length() > 6 && token.codePoints().allMatch(code -> code >= 0x4E00 && code <= 0x9FA5)
                        ? bigrams(token).stream()
                        : java.util.stream.Stream.of(token))
                .distinct()
                .toList();
        return words.isEmpty() ? List.of(normalized) : words;
    }

    private List<String> bigrams(String token) {
        List<String> grams = new java.util.ArrayList<>();
        for (int index = 0; index < token.length() - 1; index++) {
            grams.add(token.substring(index, index + 2));
        }
        return grams;
    }

    private int score(DocumentChunk chunk, List<String> keywords) {
        String haystack = (chunk.getHeadingPath() + " " + chunk.getContent()).toLowerCase();
        int score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase())) {
                score++;
            }
        }
        return score;
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

    private record ScoredChunk(DocumentChunk chunk, int score) {
    }
}
