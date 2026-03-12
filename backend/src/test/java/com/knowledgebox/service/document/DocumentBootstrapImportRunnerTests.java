package com.knowledgebox.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.CreateDocumentReviewRequest;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.service.admin.AdminOperatorService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
class DocumentBootstrapImportRunnerTests {

    @Mock
    private ResourceLoader resourceLoader;
    @Mock
    private AdminOperatorService adminOperatorService;
    @Mock
    private DocumentGovernanceService documentGovernanceService;
    @Mock
    private DocumentReviewRequestRepository documentReviewRequestRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Test
    void shouldImportSeedItemIntoReviewFlow() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getDocument().getBootstrap().setOperatorUsername("admin-seed");
        String seedJson = """
                [
                  {
                    "importKey": "yuque:51241102:238740054",
                    "title": "Spring面试题",
                    "sourceFilename": "spring-interview.md",
                    "visibilityType": "PUBLIC",
                    "sourceMarkdown": "# Spring 面试",
                    "extensionJson": {
                      "yuqueSource": {
                        "docId": 238740054
                      }
                    }
                  }
                ]
                """;
        Resource seedResource = new ByteArrayResource(seedJson.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "seed-resource";
            }
        };
        when(resourceLoader.getResource("classpath:bootstrap-seed.json")).thenReturn(seedResource);
        when(adminOperatorService.resolveOperatorId("admin-seed")).thenReturn(1L);
        when(documentReviewRequestRepository.existsByImportKey("yuque:51241102:238740054")).thenReturn(false);
        when(knowledgeDocumentRepository.existsByImportKey("yuque:51241102:238740054")).thenReturn(false);

        DocumentBootstrapImportRunner runner = new DocumentBootstrapImportRunner(
                properties,
                new ObjectMapper(),
                resourceLoader,
                adminOperatorService,
                documentGovernanceService,
                documentReviewRequestRepository,
                knowledgeDocumentRepository
        );

        DocumentBootstrapImportRunner.BootstrapRunSummary summary = runner.importFromSeedFile("classpath:bootstrap-seed.json", true);

        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isEqualTo(0);
        assertThat(summary.failedCount()).isEqualTo(0);

        ArgumentCaptor<CreateDocumentReviewRequest> requestCaptor = ArgumentCaptor.forClass(CreateDocumentReviewRequest.class);
        verify(documentGovernanceService).createUploadReview(requestCaptor.capture(), eq(1L));
        CreateDocumentReviewRequest captured = requestCaptor.getValue();
        assertThat(captured.title()).isEqualTo("Spring面试题");
        assertThat(captured.sourceFilename()).isEqualTo("spring-interview.md");
        assertThat(captured.sourceMarkdown()).isEqualTo("# Spring 面试");
        assertThat(captured.extensionJson()).contains("\"importKey\":\"yuque:51241102:238740054\"");
    }

    @Test
    void shouldSkipItemWhenImportKeyAlreadyExists() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        String seedJson = """
                [
                  {
                    "importKey": "yuque:51241102:238740054",
                    "title": "Spring面试题",
                    "sourceFilename": "spring-interview.md",
                    "visibilityType": "PUBLIC",
                    "sourceMarkdown": "# Spring 面试"
                  }
                ]
                """;
        Resource seedResource = new ByteArrayResource(seedJson.getBytes(StandardCharsets.UTF_8));
        when(resourceLoader.getResource("classpath:bootstrap-seed.json")).thenReturn(seedResource);
        when(adminOperatorService.resolveOperatorId(any())).thenReturn(1L);
        when(documentReviewRequestRepository.existsByImportKey("yuque:51241102:238740054")).thenReturn(true);

        DocumentBootstrapImportRunner runner = new DocumentBootstrapImportRunner(
                properties,
                new ObjectMapper(),
                resourceLoader,
                adminOperatorService,
                documentGovernanceService,
                documentReviewRequestRepository,
                knowledgeDocumentRepository
        );

        DocumentBootstrapImportRunner.BootstrapRunSummary summary = runner.importFromSeedFile("classpath:bootstrap-seed.json", true);

        assertThat(summary.createdCount()).isEqualTo(0);
        assertThat(summary.skippedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(0);
    }

    @Test
    void shouldResolveRelativeMarkdownPathFromSeedAncestorDirectory(@TempDir Path tempDir) throws Exception {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        Path markdownPath = tempDir.resolve("tmp/bootstrap-relative-check/content.md");
        Files.createDirectories(markdownPath.getParent());
        Files.writeString(markdownPath, "# Relative markdown", StandardCharsets.UTF_8);

        String relativeMarkdownPath = "tmp/bootstrap-relative-check/content.md";
        String seedJson = """
                [
                  {
                    "importKey": "yuque:bootstrap:relative-path",
                    "title": "Relative path test",
                    "sourceFilename": "relative.md",
                    "visibilityType": "PUBLIC",
                    "sourceMarkdownPath": "%s"
                  }
                ]
                """.formatted(relativeMarkdownPath);

        Path seedPath = tempDir.resolve("backend/bootstrap/document-seed.json");
        Files.createDirectories(seedPath.getParent());
        Files.writeString(seedPath, seedJson, StandardCharsets.UTF_8);

        when(adminOperatorService.resolveOperatorId(any())).thenReturn(1L);
        when(documentReviewRequestRepository.existsByImportKey("yuque:bootstrap:relative-path")).thenReturn(false);
        when(knowledgeDocumentRepository.existsByImportKey("yuque:bootstrap:relative-path")).thenReturn(false);

        DocumentBootstrapImportRunner runner = new DocumentBootstrapImportRunner(
                properties,
                new ObjectMapper(),
                new DefaultResourceLoader(),
                adminOperatorService,
                documentGovernanceService,
                documentReviewRequestRepository,
                knowledgeDocumentRepository
        );

        DocumentBootstrapImportRunner.BootstrapRunSummary summary = runner.importFromSeedFile(seedPath.toString(), true);

        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(0);
        ArgumentCaptor<CreateDocumentReviewRequest> requestCaptor = ArgumentCaptor.forClass(CreateDocumentReviewRequest.class);
        verify(documentGovernanceService).createUploadReview(requestCaptor.capture(), eq(1L));
        assertThat(requestCaptor.getValue().sourceMarkdown()).isEqualTo("# Relative markdown");
    }

    @Test
    void shouldImportAllSeedFilesFromDirectory(@TempDir Path tempDir) throws Exception {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        KnowledgeBoxProperties.Bootstrap bootstrap = properties.getDocument().getBootstrap();
        Path seedDir = tempDir.resolve("bootstrap-seeds");
        Files.createDirectories(seedDir);
        Path seedFile1 = seedDir.resolve("001-first.json");
        Path seedFile2 = seedDir.resolve("002-second.json");
        Files.writeString(
                seedFile1,
                """
                        [
                          {
                            "importKey": "yuque:batch:1",
                            "title": "Batch 1",
                            "sourceFilename": "batch-1.md",
                            "visibilityType": "PUBLIC",
                            "sourceMarkdown": "# Batch 1"
                          }
                        ]
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                seedFile2,
                """
                        [
                          {
                            "importKey": "yuque:batch:2",
                            "title": "Batch 2",
                            "sourceFilename": "batch-2.md",
                            "visibilityType": "PUBLIC",
                            "sourceMarkdown": "# Batch 2"
                          }
                        ]
                        """,
                StandardCharsets.UTF_8
        );

        bootstrap.setSeedDirectory(seedDir.toString());
        bootstrap.setSeedDirectoryPattern("*.json");
        bootstrap.setSeedDirectoryRecursive(false);
        when(adminOperatorService.resolveOperatorId(any())).thenReturn(1L);
        when(documentReviewRequestRepository.existsByImportKey("yuque:batch:1")).thenReturn(false);
        when(documentReviewRequestRepository.existsByImportKey("yuque:batch:2")).thenReturn(false);
        when(knowledgeDocumentRepository.existsByImportKey("yuque:batch:1")).thenReturn(false);
        when(knowledgeDocumentRepository.existsByImportKey("yuque:batch:2")).thenReturn(false);

        DocumentBootstrapImportRunner runner = new DocumentBootstrapImportRunner(
                properties,
                new ObjectMapper(),
                new DefaultResourceLoader(),
                adminOperatorService,
                documentGovernanceService,
                documentReviewRequestRepository,
                knowledgeDocumentRepository
        );

        DocumentBootstrapImportRunner.BootstrapRunSummary summary = runner.importConfiguredSeeds(bootstrap);

        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isEqualTo(0);
        assertThat(summary.failedCount()).isEqualTo(0);
        verify(documentGovernanceService, times(2)).createUploadReview(any(CreateDocumentReviewRequest.class), eq(1L));
    }

    @Test
    void shouldSkipDuplicateSeedFileWhenAlsoConfiguredInDirectory(@TempDir Path tempDir) throws Exception {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        KnowledgeBoxProperties.Bootstrap bootstrap = properties.getDocument().getBootstrap();
        Path seedDir = tempDir.resolve("bootstrap-seeds");
        Files.createDirectories(seedDir);
        Path seedFile = seedDir.resolve("single.json");
        Files.writeString(
                seedFile,
                """
                        [
                          {
                            "importKey": "yuque:single:1",
                            "title": "Single",
                            "sourceFilename": "single.md",
                            "visibilityType": "PUBLIC",
                            "sourceMarkdown": "# Single"
                          }
                        ]
                        """,
                StandardCharsets.UTF_8
        );

        bootstrap.setSeedFile(seedFile.toString());
        bootstrap.setSeedDirectory(seedDir.toString());
        bootstrap.setSeedDirectoryPattern("*.json");
        bootstrap.setSeedDirectoryRecursive(false);
        when(adminOperatorService.resolveOperatorId(any())).thenReturn(1L);
        when(documentReviewRequestRepository.existsByImportKey("yuque:single:1")).thenReturn(false);
        when(knowledgeDocumentRepository.existsByImportKey("yuque:single:1")).thenReturn(false);

        DocumentBootstrapImportRunner runner = new DocumentBootstrapImportRunner(
                properties,
                new ObjectMapper(),
                new DefaultResourceLoader(),
                adminOperatorService,
                documentGovernanceService,
                documentReviewRequestRepository,
                knowledgeDocumentRepository
        );

        DocumentBootstrapImportRunner.BootstrapRunSummary summary = runner.importConfiguredSeeds(bootstrap);

        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isEqualTo(0);
        assertThat(summary.failedCount()).isEqualTo(0);
        verify(documentGovernanceService, times(1)).createUploadReview(any(CreateDocumentReviewRequest.class), eq(1L));
    }
}
