package com.knowledgebox.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ConfirmKnowledgeIngestionDraftRequest;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.api.KnowledgeIngestionTaskSummaryView;
import com.knowledgebox.api.KnowledgeIngestionUploadMode;
import com.knowledgebox.api.KnowledgeIngestionUploadResultView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.common.BaseEntity;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentAssetRole;
import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.DocumentReviewStatus;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftStatus;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStatus;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeIngestionDraftRepository;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

class KnowledgeIngestionServiceTests {

    private KnowledgeIngestionDraftRepository draftRepository;
    private DocumentReviewRequestRepository documentReviewRequestRepository;
    private DocumentGovernanceService documentGovernanceService;
    private KnowledgeIngestionAgentService ingestionAgentService;
    private KnowledgeIngestionTaskService taskService;
    private StorageService storageService;
    private KnowledgeIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        draftRepository = mock(KnowledgeIngestionDraftRepository.class);
        documentReviewRequestRepository = mock(DocumentReviewRequestRepository.class);
        documentGovernanceService = mock(DocumentGovernanceService.class);
        ingestionAgentService = mock(KnowledgeIngestionAgentService.class);
        taskService = mock(KnowledgeIngestionTaskService.class);
        storageService = mock(StorageService.class);

        when(draftRepository.save(any(KnowledgeIngestionDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.store(any(), any())).thenReturn(new StorageService.StoredObject(
                "local",
                "knowledge-ingestion-source/demo.md",
                "/uploads/knowledge-ingestion-source/demo.md",
                "text/markdown",
                128L
        ));

        ingestionService = new KnowledgeIngestionService(
                properties,
                draftRepository,
                documentReviewRequestRepository,
                documentGovernanceService,
                ingestionAgentService,
                taskService,
                storageService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldProcessDraftAnalysisIntoAwaitingConfirmation() throws Exception {
        KnowledgeIngestionDraft draft = draft(21L, 7L, KnowledgeIngestionDraftSourceType.MARKDOWN, KnowledgeIngestionDraftStatus.CREATED);
        draft.setSourceFilename("redis-io.md");
        draft.setSourceContent("# Redis IO 多路复用\n\n正文");
        when(draftRepository.findById(21L)).thenReturn(Optional.of(draft));
        when(ingestionAgentService.analyze(draft)).thenReturn(new KnowledgeIngestionAgentService.DraftAnalysisResult(
                "Redis IO 多路复用机制",
                "Redis",
                List.of("redis", "io多路复用"),
                "解释 Redis 如何处理高并发连接。",
                "标题与标签来自首个一级标题和正文关键术语。",
                "# Redis IO 多路复用机制\n\n整理后的正文"
        ));

        ingestionService.processDraftAnalysis(21L);

        assertThat(draft.getStatus()).isEqualTo(KnowledgeIngestionDraftStatus.AWAITING_CONFIRMATION);
        assertThat(draft.getStage()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(draft.getSuggestedTitle()).isEqualTo("Redis IO 多路复用机制");
        assertThat(draft.getSuggestedCategoryName()).isEqualTo("Redis");
        assertThat(draft.getSuggestedTagsJson()).isEqualTo("[\"redis\",\"io多路复用\"]");
        assertThat(draft.getGeneratedMarkdown()).contains("整理后的正文");
        verify(draftRepository, times(2)).save(draft);
    }

    @Test
    void shouldMarkDraftFailedWhenAnalysisThrows() throws Exception {
        KnowledgeIngestionDraft draft = draft(22L, 7L, KnowledgeIngestionDraftSourceType.INLINE, KnowledgeIngestionDraftStatus.CREATED);
        draft.setSourceFilename("inline-content.md");
        draft.setSourceContent("redis io 多路复用机制");
        when(draftRepository.findById(22L)).thenReturn(Optional.of(draft));
        when(ingestionAgentService.analyze(draft)).thenThrow(new IllegalStateException("model unavailable"));

        ingestionService.processDraftAnalysis(22L);

        assertThat(draft.getStatus()).isEqualTo(KnowledgeIngestionDraftStatus.FAILED);
        assertThat(draft.getStage()).isEqualTo("FAILED");
        assertThat(draft.getErrorMessage()).isEqualTo("model unavailable");
        verify(draftRepository, times(2)).save(draft);
    }

    @Test
    void shouldConfirmDraftWithSuggestedMetadataAndSourceAsset() throws Exception {
        KnowledgeIngestionDraft draft = draft(23L, 7L, KnowledgeIngestionDraftSourceType.PDF, KnowledgeIngestionDraftStatus.AWAITING_CONFIRMATION);
        draft.setDraftCode("draft-23");
        draft.setSourceFilename("redis-multiplexing.pdf");
        draft.setGeneratedMarkdown("# Redis IO 多路复用机制\n\n整理后的正文");
        draft.setSuggestedTitle("Redis IO 多路复用机制");
        draft.setSuggestedCategoryName("Redis");
        draft.setSuggestedTagsJson("[\"redis\",\"网络\"]");
        draft.setAnalysisReasoning("来自文件名与正文关键词。");
        draft.setSourceFileProvider("oss");
        draft.setSourceFileObjectKey("knowledge-ingestion-source/redis-multiplexing.pdf");
        draft.setSourceFileUrl("https://oss.example.com/redis-multiplexing.pdf");
        draft.setSourceFileContentType("application/pdf");
        draft.setSourceFileContentLength(4096L);

        DocumentReviewRequest review = new DocumentReviewRequest();
        setId(review, 88L);
        review.setRequestCode("review-88");
        when(draftRepository.findByIdAndUserId(23L, 7L)).thenReturn(Optional.of(draft));
        when(documentGovernanceService.createPreparedPendingReview(any())).thenReturn(new DocumentReviewRequestSummaryView(
                88L,
                "review-88",
                null,
                null,
                "Redis IO 多路复用机制",
                "redis-multiplexing.pdf",
                DocumentUploaderType.USER,
                7L,
                DocumentVisibilityType.PUBLIC,
                DocumentReviewStatus.PENDING_REVIEW,
                "PENDING_REVIEW",
                100,
                "Redis",
                "[\"redis\",\"网络\"]",
                "Redis",
                "缓存专题",
                "[\"redis\",\"网络\"]",
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(documentReviewRequestRepository.findById(88L)).thenReturn(Optional.of(review));

        var view = ingestionService.confirmDraft(
                23L,
                new ConfirmKnowledgeIngestionDraftRequest(null, null, "缓存专题", List.of()),
                7L
        );

        ArgumentCaptor<DocumentGovernanceService.PreparedPendingReviewRequest> requestCaptor =
                ArgumentCaptor.forClass(DocumentGovernanceService.PreparedPendingReviewRequest.class);
        verify(documentGovernanceService).createPreparedPendingReview(requestCaptor.capture());

        DocumentGovernanceService.PreparedPendingReviewRequest captured = requestCaptor.getValue();
        assertThat(captured.title()).isEqualTo("Redis IO 多路复用机制");
        assertThat(captured.sourceFilename()).isEqualTo("redis-multiplexing.pdf");
        assertThat(captured.uploaderType()).isEqualTo(DocumentUploaderType.USER);
        assertThat(captured.uploaderUserId()).isEqualTo(7L);
        assertThat(captured.visibilityType()).isEqualTo(DocumentVisibilityType.PUBLIC);
        assertThat(captured.selectedCategoryName()).isEqualTo("Redis");
        assertThat(captured.selectedColumnName()).isEqualTo("缓存专题");
        assertThat(captured.selectedTags()).containsExactly("redis", "网络");
        assertThat(captured.extensionJson()).contains("\"ingestionDraftId\":23");
        assertThat(captured.extensionJson()).contains("\"sourceType\":\"PDF\"");
        assertThat(captured.sourceAsset()).isNotNull();
        assertThat(captured.sourceAsset().assetRole()).isEqualTo(DocumentAssetRole.SOURCE_FILE);
        assertThat(captured.sourceAsset().storedUrl()).isEqualTo("https://oss.example.com/redis-multiplexing.pdf");

        assertThat(view.status()).isEqualTo(KnowledgeIngestionDraftStatus.CONFIRMED);
        assertThat(view.confirmedReviewRequestCode()).isEqualTo("review-88");
        assertThat(draft.getConfirmedReviewRequest()).isSameAs(review);
    }

    @Test
    void shouldRejectConfirmWhenDraftNotReady() throws Exception {
        KnowledgeIngestionDraft draft = draft(24L, 7L, KnowledgeIngestionDraftSourceType.INLINE, KnowledgeIngestionDraftStatus.PROCESSING);
        when(draftRepository.findByIdAndUserId(24L, 7L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> ingestionService.confirmDraft(
                24L,
                new ConfirmKnowledgeIngestionDraftRequest("标题", null, null, List.of("redis")),
                7L
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("当前草稿尚未完成分析或已被确认");
    }

    @Test
    void shouldRouteLargePdfUploadToAsyncTask() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-big.pdf",
                "application/pdf",
                pdfBytes(90)
        );
        when(taskService.createUploadTask(file, 7L)).thenReturn(new KnowledgeIngestionUploadResultView(
                KnowledgeIngestionUploadMode.ASYNC_TASK,
                null,
                new KnowledgeIngestionTaskSummaryView(
                        301L,
                        "ingest-task-301",
                        "redis-big.pdf",
                        "/uploads/redis-big.pdf",
                        "application/pdf",
                        4096L,
                        90,
                        KnowledgeIngestionTaskStatus.QUEUED,
                        "UPLOAD_STORED",
                        5,
                        false,
                        0,
                        0,
                        0,
                        0,
                        "queued",
                        null,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ),
                "queued",
                false
        ));

        KnowledgeIngestionUploadResultView result = ingestionService.createUploadSubmission(file, 7L);

        assertThat(result.mode()).isEqualTo(KnowledgeIngestionUploadMode.ASYNC_TASK);
        assertThat(result.task()).isNotNull();
        verify(taskService).createUploadTask(file, 7L);
    }

    @Test
    void shouldRouteSmallMarkdownUploadToSyncDraft() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis.md",
                "text/markdown",
                "# Redis\n\nIO 多路复用".getBytes()
        );

        KnowledgeIngestionUploadResultView result = ingestionService.createUploadSubmission(file, 7L);

        assertThat(result.mode()).isEqualTo(KnowledgeIngestionUploadMode.SYNC_DRAFT);
        assertThat(result.draft()).isNotNull();
        verify(taskService, times(0)).createUploadTask(any(), any());
        verify(draftRepository).save(any(KnowledgeIngestionDraft.class));
    }

    private KnowledgeIngestionDraft draft(Long id, Long userId, KnowledgeIngestionDraftSourceType sourceType, KnowledgeIngestionDraftStatus status)
            throws Exception {
        KnowledgeIngestionDraft draft = new KnowledgeIngestionDraft();
        setId(draft, id);
        draft.setDraftCode("draft-" + id);
        draft.setUserId(userId);
        draft.setSourceType(sourceType);
        draft.setStatus(status);
        draft.setStage(status.name());
        draft.setProgressPercent(0);
        return draft;
    }

    private void setId(Object target, Long id) throws Exception {
        Field idField = BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }

    private byte[] pdfBytes(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage());
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
