package com.knowledgebox.service.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.api.KnowledgeIngestionTaskDetailView;
import com.knowledgebox.api.KnowledgeIngestionTaskDocumentDetailView;
import com.knowledgebox.api.KnowledgeIngestionTaskDocumentSummaryView;
import com.knowledgebox.api.KnowledgeIngestionTaskStageView;
import com.knowledgebox.api.KnowledgeIngestionTaskSummaryView;
import com.knowledgebox.api.KnowledgeIngestionUploadMode;
import com.knowledgebox.api.KnowledgeIngestionUploadResultView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.document.DocumentAssetRole;
import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.domain.document.KnowledgeIngestionTask;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocument;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocumentStatus;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStage;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageCode;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageStatus;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStatus;
import com.knowledgebox.repository.DocumentReviewRequestRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskDocumentRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskRepository;
import com.knowledgebox.repository.KnowledgeIngestionTaskStageRepository;
import jakarta.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeIngestionTaskService {

    private static final EnumSet<KnowledgeIngestionTaskStatus> TERMINAL_STATUSES = EnumSet.of(
            KnowledgeIngestionTaskStatus.CANCELLED,
            KnowledgeIngestionTaskStatus.COMPLETED,
            KnowledgeIngestionTaskStatus.PARTIAL_FAILED,
            KnowledgeIngestionTaskStatus.FAILED
    );

    private static final List<KnowledgeIngestionTaskStageCode> ORDERED_STAGES = List.of(
            KnowledgeIngestionTaskStageCode.UPLOAD_STORED,
            KnowledgeIngestionTaskStageCode.PAGE_SCAN,
            KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION,
            KnowledgeIngestionTaskStageCode.SEGMENT_PLANNING,
            KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION,
            KnowledgeIngestionTaskStageCode.FINALIZING
    );

    private final KnowledgeBoxProperties properties;
    private final KnowledgeIngestionTaskRepository taskRepository;
    private final KnowledgeIngestionTaskStageRepository stageRepository;
    private final KnowledgeIngestionTaskDocumentRepository documentRepository;
    private final DocumentReviewRequestRepository documentReviewRequestRepository;
    private final DocumentGovernanceService documentGovernanceService;
    private final KnowledgeIngestionAgentService ingestionAgentService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<Long, RunningKnowledgeIngestionTask> runningTasks = new ConcurrentHashMap<>();

    public KnowledgeIngestionTaskService(
            KnowledgeBoxProperties properties,
            KnowledgeIngestionTaskRepository taskRepository,
            KnowledgeIngestionTaskStageRepository stageRepository,
            KnowledgeIngestionTaskDocumentRepository documentRepository,
            DocumentReviewRequestRepository documentReviewRequestRepository,
            DocumentGovernanceService documentGovernanceService,
            KnowledgeIngestionAgentService ingestionAgentService,
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.stageRepository = stageRepository;
        this.documentRepository = documentRepository;
        this.documentReviewRequestRepository = documentReviewRequestRepository;
        this.documentGovernanceService = documentGovernanceService;
        this.ingestionAgentService = ingestionAgentService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeIngestionUploadResultView createUploadTask(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_FILE_REQUIRED", "请上传 PDF 文件");
        }
        String sourceFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename());
        byte[] bytes = readBytes(file);
        String contentHash = DigestUtils.md5DigestAsHex(bytes);
        Optional<KnowledgeIngestionTask> existingTask = taskRepository
                .findFirstByUserIdAndSourceFileContentHashOrderByUpdatedAtDescIdDesc(userId, contentHash);
        if (existingTask.isPresent()) {
            KnowledgeIngestionTask task = existingTask.get();
            return new KnowledgeIngestionUploadResultView(
                    KnowledgeIngestionUploadMode.ASYNC_TASK,
                    null,
                    toSummaryView(task),
                    "检测到相同内容的大 PDF 已存在，已复用历史任务。",
                    true
            );
        }

        int pageCount = countPdfPages(bytes, sourceFilename);
        String deterministicName = contentHash + resolveExtension(sourceFilename);
        StorageService.StoredObject stored = storageService.storeDeterministic(
                "knowledge-ingestion-source",
                deterministicName,
                new ByteArrayMultipartFile(sourceFilename, file.getContentType(), bytes)
        );

        KnowledgeIngestionTask task = new KnowledgeIngestionTask();
        task.setTaskCode("ingest-task-" + UUID.randomUUID());
        task.setUserId(userId);
        task.setSourceFilename(sourceFilename);
        task.setSourceFileProvider(stored.provider());
        task.setSourceFileObjectKey(stored.objectKey());
        task.setSourceFileUrl(stored.url());
        task.setSourceFileContentType(stored.contentType());
        task.setSourceFileContentLength(stored.contentLength());
        task.setSourceFileContentHash(contentHash);
        task.setSourcePageCount(pageCount);
        task.setStatus(KnowledgeIngestionTaskStatus.QUEUED);
        task.setStage(KnowledgeIngestionTaskStageCode.UPLOAD_STORED.name());
        task.setProgressPercent(5);
        task.setSummaryText("原始 PDF 已保存，等待异步拆解。");
        task = taskRepository.save(task);
        initializeStages(task);
        markStageCompleted(task.getId(), KnowledgeIngestionTaskStageCode.UPLOAD_STORED, "原始 PDF 已保存。");
        startTaskPipeline(task.getId());

        return new KnowledgeIngestionUploadResultView(
                KnowledgeIngestionUploadMode.ASYNC_TASK,
                null,
                toSummaryView(task),
                "大 PDF 已进入异步拆解任务。",
                false
        );
    }

    @Transactional(readOnly = true)
    public List<KnowledgeIngestionTaskSummaryView> tasks(Long userId) {
        return taskRepository.findAllByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
                .map(this::toSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeIngestionTaskDetailView taskDetail(Long taskId, Long userId) {
        KnowledgeIngestionTask task = loadOwnedTask(taskId, userId);
        List<KnowledgeIngestionTaskStageView> stages = stageRepository.findAllByTask_IdOrderBySortOrderAscIdAsc(taskId).stream()
                .map(this::toStageView)
                .toList();
        List<KnowledgeIngestionTaskDocumentSummaryView> documents = documentRepository.findAllByTask_IdOrderBySegmentIndexAscIdAsc(taskId).stream()
                .map(this::toDocumentSummaryView)
                .toList();
        return new KnowledgeIngestionTaskDetailView(
                task.getId(),
                task.getTaskCode(),
                task.getSourceFilename(),
                task.getSourceFileUrl(),
                task.getSourceFileContentType(),
                task.getSourceFileContentLength(),
                task.getSourcePageCount(),
                task.getStatus(),
                task.getStage(),
                task.getProgressPercent(),
                task.getCancelRequested(),
                task.getPlannedDocumentCount(),
                task.getGeneratedDocumentCount(),
                task.getFailedDocumentCount(),
                task.getCancelledDocumentCount(),
                task.getSummaryText(),
                task.getErrorMessage(),
                stages,
                documents,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public KnowledgeIngestionTaskDocumentDetailView taskDocumentDetail(Long taskId, Long documentId, Long userId) {
        loadOwnedTask(taskId, userId);
        KnowledgeIngestionTaskDocument document = documentRepository.findByIdAndTask_Id(documentId, taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INGESTION_TASK_DOCUMENT_NOT_FOUND", "任务文档不存在或无权访问"));
        DocumentReviewRequest review = document.getReviewRequest();
        return new KnowledgeIngestionTaskDocumentDetailView(
                document.getId(),
                document.getDocumentCode(),
                document.getSegmentIndex(),
                document.getPageFromNumber(),
                document.getPageToNumber(),
                document.getStatus(),
                document.getSuggestedTitle(),
                document.getSuggestedCategoryName(),
                document.getSuggestedTagsJson(),
                document.getSummaryText(),
                document.getAnalysisReasoning(),
                document.getGeneratedMarkdown(),
                document.getErrorMessage(),
                review == null ? null : review.getId(),
                review == null ? null : review.getRequestCode(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    @Transactional
    public void cancelTask(Long taskId, Long userId) {
        KnowledgeIngestionTask task = loadOwnedTask(taskId, userId);
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            return;
        }
        task.setCancelRequested(Boolean.TRUE);
        if (task.getStatus() == KnowledgeIngestionTaskStatus.QUEUED || task.getStatus() == KnowledgeIngestionTaskStatus.RUNNING) {
            task.setStatus(KnowledgeIngestionTaskStatus.CANCELLING);
        }
        task.setSummaryText("已收到取消请求，当前任务会尽快停止后续拆解。");
        taskRepository.save(task);
        RunningKnowledgeIngestionTask runningTask = runningTasks.get(taskId);
        if (runningTask != null) {
            runningTask.cancel();
        }
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        KnowledgeIngestionTask task = loadOwnedTask(taskId, userId);
        if (!TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INGESTION_TASK_NOT_DELETABLE", "仅支持删除已结束的任务");
        }
        deleteTaskSourceFile(task);
        deleteTaskChain(task);
    }

    void processTask(Long taskId, RunningKnowledgeIngestionTask runningTask) {
        KnowledgeIngestionTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            moveTaskToRunning(task, KnowledgeIngestionTaskStageCode.PAGE_SCAN, 10, "开始扫描 PDF 页数与结构。");
            byte[] bytes = storageService.read(task.getSourceFileObjectKey());
            PdfExtraction extraction = extractPdf(task, bytes, runningTask);
            checkCancelled(taskId, runningTask);

            beginPlanning(taskId, extraction.pagePreviews().size());
            List<KnowledgeIngestionAgentService.PlannedDocument> plans = ingestionAgentService.planLargePdfDocuments(
                    task.getSourceFilename(),
                    extraction.pagePreviews()
            );
            if (plans.isEmpty()) {
                throw new IllegalStateException("未生成任何拆解计划");
            }
            List<KnowledgeIngestionTaskDocument> documents = persistPlans(taskId, plans);
            completePlanning(taskId, documents.size());
            checkCancelled(taskId, runningTask);

            runGeneration(taskId, documents, extraction.pageTexts(), runningTask);
            finishTask(taskId);
        } catch (TaskCancelledException cancelledException) {
            cancelTaskAfterInterruption(taskId);
        } catch (Exception exception) {
            failTask(taskId, resolveRootCauseMessage(exception));
        }
    }

    private void initializeStages(KnowledgeIngestionTask task) {
        for (int index = 0; index < ORDERED_STAGES.size(); index++) {
            KnowledgeIngestionTaskStage stage = new KnowledgeIngestionTaskStage();
            stage.setTask(task);
            stage.setStageCode(ORDERED_STAGES.get(index));
            stage.setSortOrder(index + 1);
            stage.setStatus(KnowledgeIngestionTaskStageStatus.PENDING);
            stage.setProgressPercent(0);
            stageRepository.save(stage);
        }
    }

    private void startTaskPipeline(Long taskId) {
        Runnable startTask = () -> {
            RunningKnowledgeIngestionTask runningTask = new RunningKnowledgeIngestionTask();
            runningTasks.put(taskId, runningTask);
            Thread.ofVirtual()
                    .name("kb-ingestion-task-" + taskId)
                    .start(() -> {
                        runningTask.bind(Thread.currentThread());
                        try {
                            processTask(taskId, runningTask);
                        } finally {
                            runningTasks.remove(taskId);
                        }
                    });
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startTask.run();
                }
            });
            return;
        }
        startTask.run();
    }

    private void moveTaskToRunning(KnowledgeIngestionTask task, KnowledgeIngestionTaskStageCode stageCode, int progressPercent, String summaryText) {
        task.setStatus(KnowledgeIngestionTaskStatus.RUNNING);
        task.setStage(stageCode.name());
        task.setProgressPercent(progressPercent);
        task.setSummaryText(summaryText);
        task.setErrorMessage(null);
        taskRepository.save(task);
        markStageRunning(task.getId(), stageCode, summaryText);
    }

    private PdfExtraction extractPdf(KnowledgeIngestionTask task, byte[] bytes, RunningKnowledgeIngestionTask runningTask) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int totalPages = document.getNumberOfPages();
            task.setSourcePageCount(totalPages);
            task.setProgressPercent(15);
            taskRepository.save(task);
            markStageCompleted(task.getId(), KnowledgeIngestionTaskStageCode.PAGE_SCAN, "共扫描到 " + totalPages + " 页。");
            markStageRunning(task.getId(), KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION, "正在提取各页文本。");

            List<String> pageTexts = new ArrayList<>(totalPages);
            List<KnowledgeIngestionAgentService.LargePdfPagePreview> pagePreviews = new ArrayList<>(totalPages);
            int previewLimit = Math.max(120, properties.getDocument().getIngestion().getPreviewCharsPerPage());
            int nonBlankPages = 0;
            for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
                checkCancelled(task.getId(), runningTask);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = normalizeExtractedText(stripper.getText(document));
                if (StringUtils.hasText(pageText)) {
                    nonBlankPages++;
                }
                pageTexts.add(pageText);
                pagePreviews.add(new KnowledgeIngestionAgentService.LargePdfPagePreview(pageNumber, abbreviate(pageText, previewLimit)));
                int progress = Math.min(100, (int) Math.round(pageNumber * 100.0 / Math.max(totalPages, 1)));
                String extractionMessage = buildExtractionProgressMessage(pageNumber, totalPages, pageText);
                updateStageProgress(task.getId(), KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION, progress, extractionMessage);
                updateTaskProgress(task.getId(), 15 + Math.min(20, progress / 5), extractionMessage);
            }
            if (nonBlankPages == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_EMPTY", "PDF 未提取到文本，当前仅支持文本型 PDF");
            }
            markStageCompleted(task.getId(), KnowledgeIngestionTaskStageCode.TEXT_EXTRACTION, "文本提取完成，共 " + totalPages + " 页。");
            return new PdfExtraction(pageTexts, pagePreviews);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_PARSE_FAILED", "无法解析 PDF 文件: " + task.getSourceFilename());
        }
    }

    private void beginPlanning(Long taskId, int pageCount) {
        updateTaskStatus(taskId, KnowledgeIngestionTaskStatus.RUNNING, KnowledgeIngestionTaskStageCode.SEGMENT_PLANNING, 40, "正在规划拆解段落。");
        markStageRunning(taskId, KnowledgeIngestionTaskStageCode.SEGMENT_PLANNING, "开始根据 " + pageCount + " 页内容规划知识文档。");
    }

    private List<KnowledgeIngestionTaskDocument> persistPlans(Long taskId, List<KnowledgeIngestionAgentService.PlannedDocument> plans) {
        KnowledgeIngestionTask task = requireTask(taskId);
        task.setPlannedDocumentCount(plans.size());
        task.setSummaryText("已规划 " + plans.size() + " 个知识文档，准备逐个生成。");
        task.setProgressPercent(50);
        taskRepository.save(task);

        List<KnowledgeIngestionTaskDocument> documents = new ArrayList<>(plans.size());
        for (KnowledgeIngestionAgentService.PlannedDocument plan : plans) {
            KnowledgeIngestionTaskDocument document = new KnowledgeIngestionTaskDocument();
            document.setTask(task);
            document.setDocumentCode("ingest-doc-" + UUID.randomUUID());
            document.setSegmentIndex(plan.segmentIndex());
            document.setPageFromNumber(plan.pageFromNumber());
            document.setPageToNumber(plan.pageToNumber());
            document.setSuggestedTitle(plan.title());
            document.setSuggestedCategoryName(plan.categoryName());
            document.setSuggestedTagsJson(writeJson(plan.tagNames()));
            document.setSummaryText(plan.summary());
            document.setAnalysisReasoning(plan.reasoning());
            documents.add(documentRepository.save(document));
        }
        return documents;
    }

    private void completePlanning(Long taskId, int documentCount) {
        markStageCompleted(taskId, KnowledgeIngestionTaskStageCode.SEGMENT_PLANNING, "规划完成，共 " + documentCount + " 个待生成文档。");
        updateTaskProgress(taskId, 55, "已生成拆解计划，开始逐个产出审核文档。");
    }

    private void runGeneration(
            Long taskId,
            List<KnowledgeIngestionTaskDocument> documents,
            List<String> pageTexts,
            RunningKnowledgeIngestionTask runningTask
    ) {
        updateTaskStatus(taskId, KnowledgeIngestionTaskStatus.RUNNING, KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION, 60, "正在逐个生成知识文档与审核单。");
        markStageRunning(taskId, KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION, "开始生成知识文档。");
        int totalDocuments = documents.size();
        for (int index = 0; index < totalDocuments; index++) {
            checkCancelled(taskId, runningTask);
            KnowledgeIngestionTaskDocument document = requireDocument(documents.get(index).getId());
            document.setStatus(KnowledgeIngestionTaskDocumentStatus.GENERATING);
            document.setErrorMessage(null);
            documentRepository.save(document);

            try {
                String segmentText = buildSegmentText(pageTexts, document.getPageFromNumber(), document.getPageToNumber());
                KnowledgeIngestionAgentService.GeneratedDocument generated = ingestionAgentService.generateLargePdfDocument(
                        requireTask(taskId).getSourceFilename(),
                        document.getPageFromNumber(),
                        document.getPageToNumber(),
                        document.getSuggestedTitle(),
                        segmentText
                );
                persistGeneratedDocument(taskId, document.getId(), generated);
            } catch (TaskCancelledException cancelledException) {
                throw cancelledException;
            } catch (Exception exception) {
                markDocumentFailed(taskId, document.getId(), resolveRootCauseMessage(exception));
            }

            int processedCount = index + 1;
            int progress = 60 + (int) Math.round(processedCount * 30.0 / Math.max(totalDocuments, 1));
            updateStageProgress(
                    taskId,
                    KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION,
                    (int) Math.round(processedCount * 100.0 / Math.max(totalDocuments, 1)),
                    "已处理 " + processedCount + "/" + totalDocuments + " 个文档。"
            );
            updateTaskProgress(taskId, progress, buildProgressSummary(taskId));
        }
        markStageCompleted(taskId, KnowledgeIngestionTaskStageCode.DOCUMENT_GENERATION, buildProgressSummary(taskId));
    }

    private void persistGeneratedDocument(Long taskId, Long documentId, KnowledgeIngestionAgentService.GeneratedDocument generated) {
        KnowledgeIngestionTask task = requireTask(taskId);
        KnowledgeIngestionTaskDocument document = requireDocument(documentId);
        document.setSuggestedTitle(generated.title());
        document.setSuggestedCategoryName(generated.categoryName());
        document.setSuggestedTagsJson(writeJson(generated.tagNames()));
        document.setSummaryText(generated.summary());
        document.setAnalysisReasoning(generated.reasoning());
        document.setGeneratedMarkdown(generated.markdown());

        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("ingestionTaskId", task.getId());
        extension.put("ingestionTaskCode", task.getTaskCode());
        extension.put("ingestionTaskDocumentId", document.getId());
        extension.put("ingestionTaskDocumentCode", document.getDocumentCode());
        extension.put("sourceType", "PDF");
        extension.put("sourceFilename", task.getSourceFilename());
        extension.put("pageFromNumber", document.getPageFromNumber());
        extension.put("pageToNumber", document.getPageToNumber());
        if (StringUtils.hasText(task.getSourceFileObjectKey())) {
            extension.put("sourceFileObjectKey", task.getSourceFileObjectKey());
        }
        if (StringUtils.hasText(task.getSourceFileUrl())) {
            extension.put("sourceFileUrl", task.getSourceFileUrl());
        }

        DocumentGovernanceService.StoredReviewAsset sourceAsset = null;
        if (StringUtils.hasText(task.getSourceFileUrl())) {
            sourceAsset = new DocumentGovernanceService.StoredReviewAsset(
                    task.getSourceFilename(),
                    task.getSourceFileUrl(),
                    DocumentAssetRole.SOURCE_FILE,
                    task.getSourceFileProvider(),
                    task.getSourceFileObjectKey(),
                    task.getSourceFileContentType(),
                    task.getSourceFileContentLength()
            );
        }

        DocumentReviewRequestSummaryView reviewSummary = documentGovernanceService.createPreparedPendingReview(
                new DocumentGovernanceService.PreparedPendingReviewRequest(
                        generated.title(),
                        task.getSourceFilename(),
                        DocumentUploaderType.USER,
                        task.getUserId(),
                        DocumentVisibilityType.PUBLIC,
                        generated.markdown(),
                        writeJson(extension),
                        generated.categoryName(),
                        null,
                        generated.tagNames(),
                        generated.reasoning(),
                        sourceAsset
                )
        );
        DocumentReviewRequest reviewRequest = documentReviewRequestRepository.findById(reviewSummary.id())
                .orElseThrow(() -> new IllegalStateException("Prepared review request was not created for task document " + documentId));
        document.setReviewRequest(reviewRequest);
        document.setStatus(KnowledgeIngestionTaskDocumentStatus.PENDING_REVIEW_CREATED);
        document.setErrorMessage(null);
        documentRepository.save(document);

        task.setGeneratedDocumentCount(task.getGeneratedDocumentCount() + 1);
        taskRepository.save(task);
    }

    private void markDocumentFailed(Long taskId, Long documentId, String errorMessage) {
        KnowledgeIngestionTaskDocument document = requireDocument(documentId);
        if (document.getStatus() == KnowledgeIngestionTaskDocumentStatus.PENDING_REVIEW_CREATED) {
            return;
        }
        document.setStatus(KnowledgeIngestionTaskDocumentStatus.FAILED);
        document.setErrorMessage(errorMessage);
        documentRepository.save(document);

        KnowledgeIngestionTask task = requireTask(taskId);
        task.setFailedDocumentCount(task.getFailedDocumentCount() + 1);
        taskRepository.save(task);
    }

    private void finishTask(Long taskId) {
        updateTaskStatus(taskId, KnowledgeIngestionTaskStatus.RUNNING, KnowledgeIngestionTaskStageCode.FINALIZING, 95, "正在汇总任务结果。");
        markStageRunning(taskId, KnowledgeIngestionTaskStageCode.FINALIZING, "正在收尾并同步最终状态。");

        KnowledgeIngestionTask task = requireTask(taskId);
        KnowledgeIngestionTaskStatus finalStatus;
        if (Boolean.TRUE.equals(task.getCancelRequested())) {
            finalStatus = KnowledgeIngestionTaskStatus.CANCELLED;
        } else if (task.getGeneratedDocumentCount() > 0 && task.getFailedDocumentCount() > 0) {
            finalStatus = KnowledgeIngestionTaskStatus.PARTIAL_FAILED;
        } else if (task.getGeneratedDocumentCount() > 0) {
            finalStatus = KnowledgeIngestionTaskStatus.COMPLETED;
        } else {
            finalStatus = KnowledgeIngestionTaskStatus.FAILED;
        }
        task.setStatus(finalStatus);
        task.setStage(KnowledgeIngestionTaskStageCode.FINALIZING.name());
        task.setProgressPercent(100);
        task.setSummaryText(buildProgressSummary(taskId));
        taskRepository.save(task);
        markStageCompleted(taskId, KnowledgeIngestionTaskStageCode.FINALIZING, task.getSummaryText());
    }

    private void cancelTaskAfterInterruption(Long taskId) {
        KnowledgeIngestionTask task = requireTask(taskId);
        task.setStatus(KnowledgeIngestionTaskStatus.CANCELLED);
        task.setStage(KnowledgeIngestionTaskStageCode.FINALIZING.name());
        task.setCancelRequested(Boolean.TRUE);
        task.setProgressPercent(100);
        task.setSummaryText(buildProgressSummary(taskId));
        taskRepository.save(task);

        for (KnowledgeIngestionTaskDocument document : documentRepository.findAllByTask_IdOrderBySegmentIndexAscIdAsc(taskId)) {
            if (document.getStatus() == KnowledgeIngestionTaskDocumentStatus.PLANNED
                    || document.getStatus() == KnowledgeIngestionTaskDocumentStatus.GENERATING) {
                document.setStatus(KnowledgeIngestionTaskDocumentStatus.CANCELLED);
                if (document.getErrorMessage() == null) {
                    document.setErrorMessage("任务已取消，未继续生成该文档。");
                }
                documentRepository.save(document);
                task.setCancelledDocumentCount(task.getCancelledDocumentCount() + 1);
            }
        }
        taskRepository.save(task);

        cancelRemainingStages(taskId);
        markStageCompleted(taskId, KnowledgeIngestionTaskStageCode.FINALIZING, task.getSummaryText());
    }

    private void failTask(Long taskId, String errorMessage) {
        KnowledgeIngestionTask task = requireTask(taskId);
        task.setStatus(KnowledgeIngestionTaskStatus.FAILED);
        task.setStage(KnowledgeIngestionTaskStageCode.FINALIZING.name());
        task.setProgressPercent(100);
        task.setErrorMessage(errorMessage);
        task.setSummaryText(buildProgressSummary(taskId));
        taskRepository.save(task);
        markCurrentRunningStageFailed(taskId, errorMessage);
        markStageFailed(taskId, KnowledgeIngestionTaskStageCode.FINALIZING, errorMessage);
    }

    private void cancelRemainingStages(Long taskId) {
        for (KnowledgeIngestionTaskStage stage : stageRepository.findAllByTask_IdOrderBySortOrderAscIdAsc(taskId)) {
            if (stage.getStatus() == KnowledgeIngestionTaskStageStatus.PENDING) {
                stage.setStatus(KnowledgeIngestionTaskStageStatus.CANCELLED);
                stage.setMessage("任务已取消。");
                stageRepository.save(stage);
            } else if (stage.getStatus() == KnowledgeIngestionTaskStageStatus.RUNNING) {
                stage.setStatus(KnowledgeIngestionTaskStageStatus.CANCELLED);
                stage.setProgressPercent(Math.min(stage.getProgressPercent(), 99));
                stage.setMessage("任务已取消。");
                stageRepository.save(stage);
            }
        }
    }

    private void markCurrentRunningStageFailed(Long taskId, String errorMessage) {
        for (KnowledgeIngestionTaskStage stage : stageRepository.findAllByTask_IdOrderBySortOrderAscIdAsc(taskId)) {
            if (stage.getStatus() == KnowledgeIngestionTaskStageStatus.RUNNING) {
                stage.setStatus(KnowledgeIngestionTaskStageStatus.FAILED);
                stage.setMessage(errorMessage);
                stageRepository.save(stage);
                return;
            }
        }
    }

    private void updateTaskStatus(
            Long taskId,
            KnowledgeIngestionTaskStatus status,
            KnowledgeIngestionTaskStageCode stageCode,
            int progressPercent,
            String summaryText
    ) {
        KnowledgeIngestionTask task = requireTask(taskId);
        task.setStatus(status);
        task.setStage(stageCode.name());
        task.setProgressPercent(progressPercent);
        task.setSummaryText(summaryText);
        taskRepository.save(task);
    }

    private void updateTaskProgress(Long taskId, int progressPercent, String summaryText) {
        KnowledgeIngestionTask task = requireTask(taskId);
        task.setProgressPercent(progressPercent);
        task.setSummaryText(summaryText);
        taskRepository.save(task);
    }

    private void markStageRunning(Long taskId, KnowledgeIngestionTaskStageCode stageCode, String message) {
        KnowledgeIngestionTaskStage stage = requireStage(taskId, stageCode);
        stage.setStatus(KnowledgeIngestionTaskStageStatus.RUNNING);
        if (!StringUtils.hasText(stage.getMessage())) {
            stage.setMessage(message);
        } else {
            stage.setMessage(message);
        }
        if (stage.getProgressPercent() <= 0) {
            stage.setProgressPercent(1);
        }
        stageRepository.save(stage);
    }

    private void updateStageProgress(Long taskId, KnowledgeIngestionTaskStageCode stageCode, int progressPercent, String message) {
        KnowledgeIngestionTaskStage stage = requireStage(taskId, stageCode);
        if (stage.getStatus() == KnowledgeIngestionTaskStageStatus.PENDING) {
            stage.setStatus(KnowledgeIngestionTaskStageStatus.RUNNING);
        }
        stage.setProgressPercent(progressPercent);
        stage.setMessage(message);
        stageRepository.save(stage);
    }

    private void markStageCompleted(Long taskId, KnowledgeIngestionTaskStageCode stageCode, String message) {
        KnowledgeIngestionTaskStage stage = requireStage(taskId, stageCode);
        stage.setStatus(KnowledgeIngestionTaskStageStatus.COMPLETED);
        stage.setProgressPercent(100);
        stage.setMessage(message);
        stageRepository.save(stage);
    }

    private void markStageFailed(Long taskId, KnowledgeIngestionTaskStageCode stageCode, String errorMessage) {
        KnowledgeIngestionTaskStage stage = requireStage(taskId, stageCode);
        stage.setStatus(KnowledgeIngestionTaskStageStatus.FAILED);
        stage.setMessage(errorMessage);
        stageRepository.save(stage);
    }

    private void checkCancelled(Long taskId, RunningKnowledgeIngestionTask runningTask) {
        if ((runningTask != null && runningTask.isCancelled())
                || Thread.currentThread().isInterrupted()
                || Boolean.TRUE.equals(requireTask(taskId).getCancelRequested())) {
            throw new TaskCancelledException();
        }
    }

    private KnowledgeIngestionTask loadOwnedTask(Long taskId, Long userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INGESTION_TASK_NOT_FOUND", "任务不存在或无权访问"));
    }

    protected void deleteTaskChain(KnowledgeIngestionTask task) {
        runningTasks.remove(task.getId());
        for (KnowledgeIngestionTaskDocument document : documentRepository.findAllByTask_IdOrderBySegmentIndexAscIdAsc(task.getId())) {
            if (document.getReviewRequest() != null) {
                document.setReviewRequest(null);
            }
            documentRepository.delete(document);
        }
        for (KnowledgeIngestionTaskStage stage : stageRepository.findAllByTask_IdOrderBySortOrderAscIdAsc(task.getId())) {
            stageRepository.delete(stage);
        }
        taskRepository.delete(task);
    }

    private void deleteTaskSourceFile(KnowledgeIngestionTask task) {
        if (!StringUtils.hasText(task.getSourceFileObjectKey())) {
            return;
        }
        try {
            storageService.delete(task.getSourceFileObjectKey());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INGESTION_TASK_SOURCE_DELETE_FAILED", "删除任务源文件失败，请稍后重试");
        }
    }

    private KnowledgeIngestionTask requireTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
    }

    private KnowledgeIngestionTaskDocument requireDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Task document not found: " + documentId));
    }

    private KnowledgeIngestionTaskStage requireStage(Long taskId, KnowledgeIngestionTaskStageCode stageCode) {
        return stageRepository.findByTask_IdAndStageCode(taskId, stageCode)
                .orElseThrow(() -> new IllegalStateException("Task stage not found: " + stageCode + " for task " + taskId));
    }

    private KnowledgeIngestionTaskSummaryView toSummaryView(KnowledgeIngestionTask task) {
        return new KnowledgeIngestionTaskSummaryView(
                task.getId(),
                task.getTaskCode(),
                task.getSourceFilename(),
                task.getSourceFileUrl(),
                task.getSourceFileContentType(),
                task.getSourceFileContentLength(),
                task.getSourcePageCount(),
                task.getStatus(),
                task.getStage(),
                task.getProgressPercent(),
                task.getCancelRequested(),
                task.getPlannedDocumentCount(),
                task.getGeneratedDocumentCount(),
                task.getFailedDocumentCount(),
                task.getCancelledDocumentCount(),
                task.getSummaryText(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private KnowledgeIngestionTaskStageView toStageView(KnowledgeIngestionTaskStage stage) {
        return new KnowledgeIngestionTaskStageView(
                stage.getId(),
                stage.getStageCode(),
                stage.getStatus(),
                stage.getSortOrder(),
                stage.getProgressPercent(),
                stage.getMessage(),
                stage.getCreatedAt(),
                stage.getUpdatedAt()
        );
    }

    private KnowledgeIngestionTaskDocumentSummaryView toDocumentSummaryView(KnowledgeIngestionTaskDocument document) {
        DocumentReviewRequest review = document.getReviewRequest();
        return new KnowledgeIngestionTaskDocumentSummaryView(
                document.getId(),
                document.getDocumentCode(),
                document.getSegmentIndex(),
                document.getPageFromNumber(),
                document.getPageToNumber(),
                document.getStatus(),
                document.getSuggestedTitle(),
                document.getSuggestedCategoryName(),
                document.getSummaryText(),
                document.getErrorMessage(),
                review == null ? null : review.getId(),
                review == null ? null : review.getRequestCode(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String buildSegmentText(List<String> pageTexts, @Nullable Integer pageFromNumber, @Nullable Integer pageToNumber) {
        int from = pageFromNumber == null ? 1 : Math.max(1, pageFromNumber);
        int to = pageToNumber == null ? pageTexts.size() : Math.min(pageTexts.size(), pageToNumber);
        StringBuilder builder = new StringBuilder();
        for (int pageNumber = from; pageNumber <= to; pageNumber++) {
            String pageText = pageTexts.get(pageNumber - 1);
            builder.append("## 第 ").append(pageNumber).append(" 页\n\n");
            if (StringUtils.hasText(pageText)) {
                builder.append(pageText.trim());
            } else {
                builder.append("（本页未提取到有效文本）");
            }
            builder.append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildProgressSummary(Long taskId) {
        KnowledgeIngestionTask task = requireTask(taskId);
        return "已生成 " + task.getGeneratedDocumentCount()
                + "/" + Math.max(task.getPlannedDocumentCount(), 0)
                + " 个审核文档，失败 " + task.getFailedDocumentCount()
                + " 个，取消 " + task.getCancelledDocumentCount() + " 个。";
    }

    private String buildExtractionProgressMessage(int pageNumber, int totalPages, String pageText) {
        String preview = abbreviate(pageText, 100);
        if (!StringUtils.hasText(preview)) {
            preview = "本页暂无可提取文本。";
        }
        return "正在读取第 " + pageNumber + "/" + totalPages + " 页： " + preview;
    }

    private String normalizeExtractedText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace('\u0000', ' ').replaceAll("\\s+\\n", "\n").trim();
    }

    private String abbreviate(String text, int maxChars) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private int countPdfPages(byte[] bytes, String filename) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            if (pages < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_EMPTY", "PDF 文件为空: " + filename);
            }
            return pages;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_PDF_PARSE_FAILED", "无法解析 PDF 文件: " + filename);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGESTION_FILE_READ_FAILED", "无法读取上传文件");
        }
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return ".pdf";
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return ".pdf";
        }
        return filename.substring(extensionIndex);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize ingestion metadata", exception);
        }
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : throwable.toString();
    }

    private record PdfExtraction(
            List<String> pageTexts,
            List<KnowledgeIngestionAgentService.LargePdfPagePreview> pagePreviews
    ) {
    }

    private static final class TaskCancelledException extends RuntimeException {
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private ByteArrayMultipartFile(String originalFilename, String contentType, byte[] bytes) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
