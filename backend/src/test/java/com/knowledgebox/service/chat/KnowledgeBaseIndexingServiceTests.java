package com.knowledgebox.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseIndexingServiceTests {

    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private KnowledgeBaseRetrievalService retrievalService;
    @Mock
    private ObjectProvider<VectorStore> vectorStoreProvider;
    @Mock
    private VectorStore vectorStore;

    @Test
    void shouldBatchAddWhenIndexingChunks() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getRetrieval().setEmbeddingBatchSize(2);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);

        List<DocumentChunk> chunks = mockChunks(5);
        for (int index = 0; index < chunks.size(); index++) {
            when(retrievalService.toVectorDocument(chunks.get(index))).thenReturn(mockDocument(index + 1));
        }

        KnowledgeBaseIndexingService service = new KnowledgeBaseIndexingService(
                documentChunkRepository,
                retrievalService,
                vectorStoreProvider,
                properties
        );
        service.index(chunks);

        ArgumentCaptor<List<Document>> batches = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(batches.capture());
        assertThat(batches.getAllValues()).hasSize(3);
        assertThat(batches.getAllValues().get(0)).hasSize(2);
        assertThat(batches.getAllValues().get(1)).hasSize(2);
        assertThat(batches.getAllValues().get(2)).hasSize(1);
    }

    @Test
    void shouldBatchAddWhenSyncingAllChunks() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getRetrieval().setEmbeddingBatchSize(2);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);

        List<DocumentChunk> chunks = mockChunks(5);
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            Long chunkId = (long) (index + 1);
            when(chunks.get(index).getId()).thenReturn(chunkId);
            when(retrievalService.vectorDocumentId(chunkId)).thenReturn("chunk-" + chunkId);
            when(retrievalService.toVectorDocument(chunks.get(index))).thenReturn(mockDocument(index + 1));
            ids.add("chunk-" + chunkId);
        }
        when(documentChunkRepository.findAllWithDocument()).thenReturn(chunks);

        KnowledgeBaseIndexingService service = new KnowledgeBaseIndexingService(
                documentChunkRepository,
                retrievalService,
                vectorStoreProvider,
                properties
        );
        service.syncAllChunks();

        verify(vectorStore).delete(ids);
        ArgumentCaptor<List<Document>> batches = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(batches.capture());
        assertThat(batches.getAllValues()).hasSize(3);
        assertThat(batches.getAllValues().get(0)).hasSize(2);
        assertThat(batches.getAllValues().get(1)).hasSize(2);
        assertThat(batches.getAllValues().get(2)).hasSize(1);
    }

    @Test
    void shouldClampBatchSizeToDashScopeLimit() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getRetrieval().setEmbeddingBatchSize(25);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);

        List<DocumentChunk> chunks = mockChunks(21);
        for (int index = 0; index < chunks.size(); index++) {
            when(retrievalService.toVectorDocument(chunks.get(index))).thenReturn(mockDocument(index + 1));
        }

        KnowledgeBaseIndexingService service = new KnowledgeBaseIndexingService(
                documentChunkRepository,
                retrievalService,
                vectorStoreProvider,
                properties
        );
        service.index(chunks);

        ArgumentCaptor<List<Document>> batches = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(batches.capture());
        assertThat(batches.getAllValues()).hasSize(3);
        assertThat(batches.getAllValues().get(0)).hasSize(10);
        assertThat(batches.getAllValues().get(1)).hasSize(10);
        assertThat(batches.getAllValues().get(2)).hasSize(1);
    }

    private List<DocumentChunk> mockChunks(int size) {
        List<DocumentChunk> chunks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            chunks.add(mock(DocumentChunk.class));
        }
        return chunks;
    }

    private Document mockDocument(int index) {
        return Document.builder()
                .id("doc-" + index)
                .text("text-" + index)
                .build();
    }
}
