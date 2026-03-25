package com.knowledgebox.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.KnowledgeIngestionTask;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocument;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocumentStatus;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStage;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageCode;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStatus;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskDocumentRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskStageRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionTaskServiceTests {

    private KnowledgeIngestionTaskRepository taskRepository;
    private KnowledgeIngestionTaskStageRepository stageRepository;
    private KnowledgeIngestionTaskDocumentRepository documentRepository;
    private DocumentReviewRequestRepository documentReviewRequestRepository;
    private DocumentGovernanceService documentGovernanceService;
    private KnowledgeIngestionAgentService ingestionAgentService;
    private StorageService storageService;
    private KnowledgeIngestionTaskService taskService;

    private final Map<Long, KnowledgeIngestionTask> tasks = new LinkedHashMap<>();
    private final Map<Long, KnowledgeIngestionTaskStage> stages = new LinkedHashMap<>();
    private final Map<Long, KnowledgeIngestionTaskDocument> documents = new LinkedHashMap<>();
    private final Map<Long, DocumentReviewRequest> reviewRequests = new LinkedHashMap<>();
    private final AtomicLong taskIdSequence = new AtomicLong(100);
    private final AtomicLong stageIdSequence = new AtomicLong(200);
    private final AtomicLong documentIdSequence = new AtomicLong(300);
    private final AtomicLong reviewIdSequence = new AtomicLong(800);

    @BeforeEach
    void setUp() {
        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getDocument().getIngestion().setPageBatchSize(2);
        properties.getDocument().getIngestion().setMaxGeneratedDocuments(6);
        properties.getDocument().getIngestion().setPreviewCharsPerPage(200);

        taskRepository = mock(KnowledgeIngestionTaskRepository.class);
        stageRepository = mock(KnowledgeIngestionTaskStageRepository.class);
        documentRepository = mock(KnowledgeIngestionTaskDocumentRepository.class);
        documentReviewRequestRepository = mock(DocumentReviewRequestRepository.class);
        documentGovernanceService = mock(DocumentGovernanceService.class);
        ingestionAgentService = mock(KnowledgeIngestionAgentService.class);
        storageService = mock(StorageService.class);

        when(taskRepository.save(any(KnowledgeIngestionTask.class))).thenAnswer(invocation -> {
            KnowledgeIngestionTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                setId(task, taskIdSequence.incrementAndGet());
            }
            touch(task);
            tasks.put(task.getId(), task);
            return task;
        });
        when(taskRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(tasks.get(invocation.getArgument(0))));
        when(taskRepository.findByIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation -> {
            KnowledgeIngestionTask task = tasks.get(invocation.getArgument(0));
            Long userId = invocation.getArgument(1);
            return task != null && userId.equals(task.getUserId()) ? Optional.of(task) : Optional.empty();
        });
        doAnswer(invocation -> {
            KnowledgeIngestionTask task = invocation.getArgument(0);
            tasks.remove(task.getId());
            return null;
        }).when(taskRepository).delete(any(KnowledgeIngestionTask.class));

        when(stageRepository.save(any(KnowledgeIngestionTaskStage.class))).thenAnswer(invocation -> {
            KnowledgeIngestionTaskStage stage = invocation.getArgument(0);
            if (stage.getId() == null) {
                setId(stage, stageIdSequence.incrementAndGet());
            }
            touch(stage);
            stages.put(stage.getId(), stage);
            return stage;
        });
        when(stageRepository.findAllByTask_IdOrderBySortOrderAscIdAsc(anyLong())).thenAnswer(invocation -> stages.values().stream()
                .filter(stage -> stage.getTask() != null && invocation.getArgument(0).equals(stage.getTask().getId()))
                .sorted(Comparator.comparing(KnowledgeIngestionTaskStage::getSortOrder).thenComparing(KnowledgeIngestionTaskStage::getId))
                .toList());
        when(stageRepository.findByTask_IdAndStageCode(anyLong(), any())).thenAnswer(invocation -> stages.values().stream()
                .filter(stage -> stage.getTask() != null
                        && invocation.getArgument(0).equals(stage.getTask().getId())
                        && invocation.getArgument(1) == stage.getStageCode())
                .findFirst());
        doAnswer(invocation -> {
            KnowledgeIngestionTaskStage stage = invocation.getArgument(0);
            stages.remove(stage.getId());
            return null;
        }).when(stageRepository).delete(any(KnowledgeIngestionTaskStage.class));

        when(documentRepository.save(any(KnowledgeIngestionTaskDocument.class))).thenAnswer(invocation -> {
            KnowledgeIngestionTaskDocument document = invocation.getArgument(0);
            if (document.getId() == null) {
                setId(document, documentIdSequence.incrementAndGet());
            }
            touch(document);
            documents.put(document.getId(), document);
            return document;
        });
        when(documentRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(documents.get(invocation.getArgument(0))));
        when(documentRepository.findAllByTask_IdOrderBySegmentIndexAscIdAsc(anyLong())).thenAnswer(invocation -> documents.values().stream()
                .filter(document -> document.getTask() != null && invocation.getArgument(0).equals(document.getTask().getId()))
                .sorted(Comparator.comparing(KnowledgeIngestionTaskDocument::getSegmentIndex).thenComparing(KnowledgeIngestionTaskDocument::getId))
                .toList());
        when(documentRepository.findByIdAndTask_Id(anyLong(), anyLong())).thenAnswer(invocation -> documents.values().stream()
                .filter(document -> invocation.getArgument(0).equals(document.getId())
                        && document.getTask() != null
                        && invocation.getArgument(1).equals(document.getTask().getId()))
                .findFirst());
        doAnswer(invocation -> {
            KnowledgeIngestionTaskDocument document = invocation.getArgument(0);
            documents.remove(document.getId());
            return null;
        }).when(documentRepository).delete(any(KnowledgeIngestionTaskDocument.class));

        when(documentReviewRequestRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(reviewRequests.get(invocation.getArgument(0))));
        when(documentGovernanceService.createPreparedPendingReview(any())).thenAnswer(invocation -> {
            long id = reviewIdSequence.incrementAndGet();
            DocumentReviewRequest request = new DocumentReviewRequest();
            setId(request, id);
            request.setRequestCode("review-" + id);
            reviewRequests.put(id, request);
            return new DocumentReviewRequestSummaryView(
                    id,
                    request.getRequestCode(),
                    null,
                    null,
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).title(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).sourceFilename(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).uploaderType(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).uploaderUserId(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).visibilityType(),
                    null,
                    "PENDING_REVIEW",
                    100,
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).selectedCategoryName(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).selectedTags().toString(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).selectedCategoryName(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).selectedColumnName(),
                    invocation.<DocumentGovernanceService.PreparedPendingReviewRequest>getArgument(0).selectedTags().toString(),
                    null,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            );
        });

        taskService = new KnowledgeIngestionTaskService(
                properties,
                taskRepository,
                stageRepository,
                documentRepository,
                documentReviewRequestRepository,
                documentGovernanceService,
                ingestionAgentService,
                storageService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldGenerateMultipleReviewDocumentsAndCompleteTask() throws Exception {
        KnowledgeIngestionTask task = seedTask("redis-big.pdf", 7L, 6);
        byte[] pdfBytes = pdfBytesWithText(List.of(
                "Redis section one",
                "Redis section two",
                "Redis section three",
                "Thread model section one",
                "Thread model section two",
                "Thread model section three"
        ));
        when(storageService.read(task.getSourceFileObjectKey())).thenReturn(pdfBytes);
        when(ingestionAgentService.planLargePdfDocuments(any(), any())).thenReturn(List.of(
                new KnowledgeIngestionAgentService.PlannedDocument(1, 1, 3, "Redis 基础", "Redis", List.of("redis"), "前半部分摘要", "前半部分主题集中。"),
                new KnowledgeIngestionAgentService.PlannedDocument(2, 4, 6, "Redis 线程模型", "Redis", List.of("线程模型"), "后半部分摘要", "后半部分主题集中。")
        ));
        when(ingestionAgentService.generateLargePdfDocument(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String plannedTitle = invocation.getArgument(3, String.class);
            return new KnowledgeIngestionAgentService.GeneratedDocument(
                    plannedTitle,
                    "Redis",
                    List.of("redis"),
                    plannedTitle + " 摘要",
                    "根据页码内容生成。",
                    "# " + plannedTitle + "\n\n整理后的正文"
            );
        });

        taskService.processTask(task.getId(), new RunningKnowledgeIngestionTask());

        KnowledgeIngestionTask savedTask = tasks.get(task.getId());
        assertThat(savedTask.getStatus()).isEqualTo(KnowledgeIngestionTaskStatus.COMPLETED);
        assertThat(savedTask.getGeneratedDocumentCount()).isEqualTo(2);
        assertThat(savedTask.getFailedDocumentCount()).isEqualTo(0);
        assertThat(documents.values()).hasSize(2);
        assertThat(documents.values())
                .allMatch(document -> document.getStatus() == KnowledgeIngestionTaskDocumentStatus.PENDING_REVIEW_CREATED);
        assertThat(reviewRequests).hasSize(2);
    }

    @Test
    void shouldCancelRemainingDocumentsButKeepGeneratedArtifacts() throws Exception {
        KnowledgeIngestionTask task = seedTask("redis-cancel.pdf", 7L, 6);
        byte[] pdfBytes = pdfBytesWithText(List.of(
                "Page one content",
                "Page two content",
                "Page three content",
                "Page four content",
                "Page five content",
                "Page six content"
        ));
        when(storageService.read(task.getSourceFileObjectKey())).thenReturn(pdfBytes);
        when(ingestionAgentService.planLargePdfDocuments(any(), any())).thenReturn(List.of(
                new KnowledgeIngestionAgentService.PlannedDocument(1, 1, 2, "第一段", "Redis", List.of("redis"), "第一段摘要", "第一段"),
                new KnowledgeIngestionAgentService.PlannedDocument(2, 3, 4, "第二段", "Redis", List.of("redis"), "第二段摘要", "第二段"),
                new KnowledgeIngestionAgentService.PlannedDocument(3, 5, 6, "第三段", "Redis", List.of("redis"), "第三段摘要", "第三段")
        ));
        when(ingestionAgentService.generateLargePdfDocument(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String plannedTitle = invocation.getArgument(3, String.class);
            KnowledgeIngestionTask currentTask = tasks.get(task.getId());
            if ("第一段".equals(plannedTitle)) {
                currentTask.setCancelRequested(Boolean.TRUE);
            }
            return new KnowledgeIngestionAgentService.GeneratedDocument(
                    plannedTitle,
                    "Redis",
                    List.of("redis"),
                    plannedTitle + " 摘要",
                    "根据页码内容生成。",
                    "# " + plannedTitle + "\n\n整理后的正文"
            );
        });

        taskService.processTask(task.getId(), new RunningKnowledgeIngestionTask());

        KnowledgeIngestionTask savedTask = tasks.get(task.getId());
        assertThat(savedTask.getStatus()).isEqualTo(KnowledgeIngestionTaskStatus.CANCELLED);
        assertThat(savedTask.getGeneratedDocumentCount()).isEqualTo(1);
        assertThat(savedTask.getCancelledDocumentCount()).isEqualTo(2);
        List<KnowledgeIngestionTaskDocument> orderedDocuments = new ArrayList<>(documents.values());
        orderedDocuments.sort(Comparator.comparing(KnowledgeIngestionTaskDocument::getSegmentIndex));
        assertThat(orderedDocuments.get(0).getStatus()).isEqualTo(KnowledgeIngestionTaskDocumentStatus.PENDING_REVIEW_CREATED);
        assertThat(orderedDocuments.get(1).getStatus()).isEqualTo(KnowledgeIngestionTaskDocumentStatus.CANCELLED);
        assertThat(orderedDocuments.get(2).getStatus()).isEqualTo(KnowledgeIngestionTaskDocumentStatus.CANCELLED);
        assertThat(reviewRequests).hasSize(1);
    }

    @Test
    void shouldDeleteTerminalTaskAndKeepReviewRequests() throws Exception {
        KnowledgeIngestionTask task = seedTask("redis-cleanup.pdf", 7L, 4);
        task.setStatus(KnowledgeIngestionTaskStatus.COMPLETED);
        task.setStage(KnowledgeIngestionTaskStageCode.FINALIZING.name());
        task.setSummaryText("已生成 1/1 个审核文档，失败 0 个，取消 0 个。");

        KnowledgeIngestionTaskDocument document = new KnowledgeIngestionTaskDocument();
        document.setTask(task);
        document.setDocumentCode("doc-cleanup");
        document.setSegmentIndex(1);
        document.setStatus(KnowledgeIngestionTaskDocumentStatus.PENDING_REVIEW_CREATED);
        DocumentReviewRequest reviewRequest = new DocumentReviewRequest();
        setId(reviewRequest, reviewIdSequence.incrementAndGet());
        reviewRequest.setRequestCode("review-" + reviewRequest.getId());
        reviewRequests.put(reviewRequest.getId(), reviewRequest);
        document.setReviewRequest(reviewRequest);
        documentRepository.save(document);

        taskService.deleteTask(task.getId(), 7L);

        assertThat(tasks).doesNotContainKey(task.getId());
        assertThat(stages.values()).noneMatch(stage -> stage.getTask() != null && task.getId().equals(stage.getTask().getId()));
        assertThat(documents.values()).noneMatch(item -> item.getTask() != null && task.getId().equals(item.getTask().getId()));
        assertThat(reviewRequests).containsKey(reviewRequest.getId());
    }

    @Test
    void shouldRejectDeletingRunningTask() throws Exception {
        KnowledgeIngestionTask task = seedTask("redis-running.pdf", 7L, 4);
        task.setStatus(KnowledgeIngestionTaskStatus.RUNNING);
        task.setStage(KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION.name());

        assertThatThrownBy(() -> taskService.deleteTask(task.getId(), 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅支持删除已结束的任务");
    }

    private KnowledgeIngestionTask seedTask(String sourceFilename, Long userId, int pageCount) throws Exception {
        KnowledgeIngestionTask task = new KnowledgeIngestionTask();
        task.setTaskCode("task-" + taskIdSequence.incrementAndGet());
        task.setUserId(userId);
        task.setSourceFilename(sourceFilename);
        task.setSourceFileProvider("local");
        task.setSourceFileObjectKey("knowledge-ingestion-source/" + sourceFilename);
        task.setSourceFileUrl("/uploads/" + sourceFilename);
        task.setSourceFileContentType("application/pdf");
        task.setSourceFileContentLength(4096L);
        task.setSourceFileContentHash("hash-" + sourceFilename);
        task.setSourcePageCount(pageCount);
        task.setStatus(KnowledgeIngestionTaskStatus.QUEUED);
        task.setStage(KnowledgeIngestionTaskStageCode.UPLOAD_STORED.name());
        task.setProgressPercent(5);
        taskRepository.save(task);
        for (KnowledgeIngestionTaskStageCode stageCode : List.of(
                KnowledgeIngestionTaskStageCode.UPLOAD_STORED,
                KnowledgeIngestionTaskStageCode.PAGE_SCAN,
                KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION,
                KnowledgeIngestionTaskStageCode.SEGMENT_PLANNING,
                KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION,
                KnowledgeIngestionTaskStageCode.FINALIZING
        )) {
            KnowledgeIngestionTaskStage stage = new KnowledgeIngestionTaskStage();
            stage.setTask(task);
            stage.setStageCode(stageCode);
            stage.setSortOrder(stageCode.ordinal() + 1);
            stageRepository.save(stage);
        }
        return task;
    }

    private byte[] pdfBytesWithText(List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void setId(Object target, Long id) throws Exception {
        Field idField = Class.forName("com.knowledgebox.common.BaseEntity").getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }

    private void touch(Object target) throws Exception {
        Field createdAtField = Class.forName("com.knowledgebox.common.BaseEntity").getDeclaredField("createdAt");
        Field updatedAtField = Class.forName("com.knowledgebox.common.BaseEntity").getDeclaredField("updatedAt");
        createdAtField.setAccessible(true);
        updatedAtField.setAccessible(true);
        if (createdAtField.get(target) == null) {
            createdAtField.set(target, OffsetDateTime.now());
        }
        updatedAtField.set(target, OffsetDateTime.now());
    }
}
