package com.knowledgebox.web.admin;

import com.knowledgebox.api.BatchDocumentReviewActionResultView;
import com.knowledgebox.api.BatchReviewActionRequest;
import com.knowledgebox.api.DocumentCategoryView;
import com.knowledgebox.api.DocumentDuplicateCleanupPreviewView;
import com.knowledgebox.api.DocumentDuplicateCleanupRequest;
import com.knowledgebox.api.DocumentDuplicateCleanupResultView;
import com.knowledgebox.api.DocumentIndexRebuildJobView;
import com.knowledgebox.api.DocumentReviewRequestPageView;
import com.knowledgebox.api.DocumentReviewRequestDetailView;
import com.knowledgebox.api.DocumentReviewRequestSummaryView;
import com.knowledgebox.api.DocumentTagView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.ReviewActionRequest;
import com.knowledgebox.api.UpdateDocumentSourceRequest;
import com.knowledgebox.api.UpdateReviewTaxonomyRequest;
import com.knowledgebox.domain.document.DocumentReviewStatus;
import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.service.admin.AdminOperatorService;
import com.knowledgebox.service.document.DocumentDuplicateCleanupService;
import com.knowledgebox.service.document.DocumentGovernanceService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDocumentGovernanceController {

    private final DocumentGovernanceService documentGovernanceService;
    private final DocumentDuplicateCleanupService documentDuplicateCleanupService;
    private final AdminOperatorService adminOperatorService;

    public AdminDocumentGovernanceController(
            DocumentGovernanceService documentGovernanceService,
            DocumentDuplicateCleanupService documentDuplicateCleanupService,
            AdminOperatorService adminOperatorService
    ) {
        this.documentGovernanceService = documentGovernanceService;
        this.documentDuplicateCleanupService = documentDuplicateCleanupService;
        this.adminOperatorService = adminOperatorService;
    }

    @GetMapping("/documents/{id}")
    public KnowledgeDocumentView documentDetail(@PathVariable Long id) {
        return documentGovernanceService.documentDetail(id);
    }

    @PutMapping("/documents/{id}/source")
    public DocumentReviewRequestSummaryView updateDocumentSource(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentSourceRequest request,
            Principal principal
    ) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.createEditReview(id, request, operatorId);
    }

    @GetMapping("/document-categories")
    public List<DocumentCategoryView> categories() {
        return documentGovernanceService.categories();
    }

    @GetMapping("/document-tags")
    public List<DocumentTagView> tags() {
        return documentGovernanceService.tags();
    }

    @GetMapping("/document-duplicates")
    public DocumentDuplicateCleanupPreviewView duplicatePreview(
            @RequestParam(required = false) DocumentVisibilityType visibilityType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String keepStrategy,
            @RequestParam(required = false) Integer limit
    ) {
        return documentDuplicateCleanupService.preview(visibilityType, status, keepStrategy, limit);
    }

    @PostMapping("/document-duplicates/cleanup")
    public DocumentDuplicateCleanupResultView cleanupDuplicates(
            @Valid @RequestBody DocumentDuplicateCleanupRequest request,
            Principal principal
    ) {
        DocumentDuplicateCleanupResultView cleanupResult = documentDuplicateCleanupService.cleanup(request);
        DocumentIndexRebuildJobView indexRebuildJob = null;
        if (request.triggerIndexRebuild() && cleanupResult.duplicateDocumentsDeleted() > 0) {
            Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
            indexRebuildJob = documentGovernanceService.triggerIndexRebuild(operatorId);
        }
        return new DocumentDuplicateCleanupResultView(
                cleanupResult.duplicateDocumentsDeleted(),
                cleanupResult.mergedTagBindings(),
                cleanupResult.rewiredSourceReviews(),
                cleanupResult.rewiredPublishedReviews(),
                cleanupResult.rewiredIngestionJobs(),
                cleanupResult.deletedTagBindings(),
                cleanupResult.deletedAssets(),
                cleanupResult.deletedChunks(),
                cleanupResult.vectorRowsDeleted(),
                cleanupResult.refreshedKeeperTags(),
                cleanupResult.deletedDocuments(),
                indexRebuildJob
        );
    }

    @GetMapping("/document-reviews")
    public DocumentReviewRequestPageView reviewRequests(
            @RequestParam(required = false) DocumentReviewStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return documentGovernanceService.reviewRequests(status, page, pageSize);
    }

    @GetMapping("/document-reviews/{id}")
    public DocumentReviewRequestDetailView reviewRequestDetail(@PathVariable Long id) {
        return documentGovernanceService.reviewRequestDetail(id);
    }

    @PutMapping("/document-reviews/{id}/taxonomy")
    public DocumentReviewRequestSummaryView updateReviewTaxonomy(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewTaxonomyRequest request
    ) {
        return documentGovernanceService.updateReviewTaxonomy(id, request);
    }

    @PostMapping("/document-reviews/{id}/approve")
    public DocumentReviewRequestSummaryView approveReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            Principal principal
    ) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.approveReview(id, operatorId, request == null ? null : request.reason());
    }

    @PostMapping("/document-reviews/batch/approve")
    public BatchDocumentReviewActionResultView batchApproveReviews(
            @Valid @RequestBody BatchReviewActionRequest request,
            Principal principal
    ) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.batchApproveReviews(request.reviewIds(), operatorId, request.reason());
    }

    @PostMapping("/document-reviews/{id}/reject")
    public DocumentReviewRequestSummaryView rejectReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            Principal principal
    ) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.rejectReview(id, operatorId, request == null ? null : request.reason());
    }

    @PostMapping("/documents/index-rebuilds")
    public DocumentIndexRebuildJobView triggerIndexRebuild(Principal principal) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.triggerIndexRebuild(operatorId);
    }

    @GetMapping("/documents/index-rebuilds/latest")
    public DocumentIndexRebuildJobView latestIndexRebuild() {
        return documentGovernanceService.latestIndexRebuild();
    }
}
