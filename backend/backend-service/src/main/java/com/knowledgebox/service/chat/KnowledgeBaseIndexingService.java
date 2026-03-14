package com.knowledgebox.service.chat;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseIndexingService implements org.springframework.boot.ApplicationRunner {
    private static final int DASHSCOPE_EMBEDDING_MAX_BATCH_SIZE = 10;

    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeBaseRetrievalService retrievalService;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final KnowledgeBoxProperties properties;

    public KnowledgeBaseIndexingService(
            DocumentChunkRepository documentChunkRepository,
            KnowledgeBaseRetrievalService retrievalService,
            ObjectProvider<VectorStore> vectorStoreProvider,
            KnowledgeBoxProperties properties
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.retrievalService = retrievalService;
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
    }

    public void syncAllChunks() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        List<DocumentChunk> chunks = documentChunkRepository.findAllWithDocument();
        if (chunks.isEmpty()) {
            return;
        }
        List<String> ids = chunks.stream()
                .map(DocumentChunk::getId)
                .map(retrievalService::vectorDocumentId)
                .toList();
        List<Document> documents = chunks.stream()
                .map(retrievalService::toVectorDocument)
                .toList();
        vectorStore.delete(ids);
        addDocumentsInBatches(vectorStore, documents);
    }

    @Transactional
    public void index(List<DocumentChunk> chunks) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || chunks.isEmpty()) {
            return;
        }
        addDocumentsInBatches(vectorStore, chunks.stream()
                .map(retrievalService::toVectorDocument)
                .toList());
    }

    public void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents) {
        if (vectorStore == null || documents == null || documents.isEmpty()) {
            return;
        }
        int configuredBatchSize = Math.max(1, properties.getRetrieval().getEmbeddingBatchSize());
        int batchSize = Math.min(configuredBatchSize, DASHSCOPE_EMBEDDING_MAX_BATCH_SIZE);
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            vectorStore.add(new ArrayList<>(documents.subList(start, end)));
        }
    }

    @Transactional
    public void delete(List<DocumentChunk> chunks) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || chunks.isEmpty()) {
            return;
        }
        vectorStore.delete(chunks.stream()
                .map(DocumentChunk::getId)
                .map(retrievalService::vectorDocumentId)
                .toList());
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        syncAllChunks();
    }
}
