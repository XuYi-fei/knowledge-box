package com.knowledgebox.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentChunk;
import com.knowledgebox.domain.document.KnowledgeDocument;
import com.knowledgebox.repository.DocumentChunkRepository;
import com.knowledgebox.repository.IngestionJobRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.service.chat.KnowledgeBaseIndexingService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentIngestionServiceTests {

    private DocumentIngestionService documentIngestionService;

    @BeforeEach
    void setUp() {
        StorageService storageService = new StorageService() {
            @Override
            public StoredObject store(String category, org.springframework.web.multipart.MultipartFile file) {
                return new StoredObject(
                        "local",
                        category + "/" + file.getOriginalFilename(),
                        "/uploads/" + category + "/" + file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize()
                );
            }

            @Override
            public byte[] read(String objectKey) {
                return new byte[0];
            }
        };
        KnowledgeDocumentRepository knowledgeDocumentRepository = mock(KnowledgeDocumentRepository.class);
        DocumentChunkRepository documentChunkRepository = mock(DocumentChunkRepository.class);
        IngestionJobRepository ingestionJobRepository = mock(IngestionJobRepository.class);
        KnowledgeBaseIndexingService knowledgeBaseIndexingService = mock(KnowledgeBaseIndexingService.class);
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        ObjectMapper objectMapper = new ObjectMapper();

        when(knowledgeDocumentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingestionJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        documentIngestionService = new DocumentIngestionService(
                storageService,
                knowledgeDocumentRepository,
                documentChunkRepository,
                ingestionJobRepository,
                knowledgeBaseIndexingService,
                properties,
                objectMapper
        );
    }

    @Test
    void shouldRewriteLocalAssetLinks() throws Exception {
        MockMultipartFile markdown = new MockMultipartFile(
                "markdown",
                "guide.md",
                "text/markdown",
                """
                # Upload Guide

                ![diagram](images/architecture.png)
                """.getBytes()
        );
        MockMultipartFile asset = new MockMultipartFile(
                "assets",
                "architecture.png",
                "image/png",
                "png".getBytes()
        );

        DocumentUploadResult result = documentIngestionService.ingest(markdown, List.of(asset));

        assertThat(result.title()).isEqualTo("Upload Guide");
        assertThat(result.rewrittenAssets()).contains("/uploads/assets/architecture.png");
        Path normalized = Path.of("backend/uploads/docs/guide.normalized.md");
        assertThat(Files.readString(normalized)).contains("/uploads/assets/architecture.png");
        Files.deleteIfExists(normalized);
    }
}
