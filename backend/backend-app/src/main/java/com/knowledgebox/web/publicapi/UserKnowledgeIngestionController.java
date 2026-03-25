package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.ConfirmKnowledgeIngestionDraftRequest;
import com.knowledgebox.api.CreateKnowledgeIngestionInlineDraftRequest;
import com.knowledgebox.api.KnowledgeIngestionDraftView;
import com.knowledgebox.api.KnowledgeIngestionOptionsView;
import com.knowledgebox.security.CurrentUserAccessor;
import com.knowledgebox.service.document.KnowledgeIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/app/knowledge-ingestion")
public class UserKnowledgeIngestionController {

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final CurrentUserAccessor currentUserAccessor;

    public UserKnowledgeIngestionController(
            KnowledgeIngestionService knowledgeIngestionService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping("/options")
    public KnowledgeIngestionOptionsView options() {
        return knowledgeIngestionService.options();
    }

    @PostMapping(path = "/drafts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeIngestionDraftView uploadDraft(@RequestPart("file") MultipartFile file) {
        return knowledgeIngestionService.createUploadDraft(file, currentUserAccessor.requireCurrentUser().id());
    }

    @PostMapping(path = "/drafts/inline", consumes = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeIngestionDraftView createInlineDraft(@Valid @RequestBody CreateKnowledgeIngestionInlineDraftRequest request) {
        return knowledgeIngestionService.createInlineDraft(request, currentUserAccessor.requireCurrentUser().id());
    }

    @GetMapping("/drafts/{draftId}")
    public KnowledgeIngestionDraftView draftDetail(@PathVariable Long draftId) {
        return knowledgeIngestionService.draftDetail(draftId, currentUserAccessor.requireCurrentUser().id());
    }

    @PostMapping(path = "/drafts/{draftId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeIngestionDraftView confirmDraft(
            @PathVariable Long draftId,
            @Valid @RequestBody ConfirmKnowledgeIngestionDraftRequest request
    ) {
        return knowledgeIngestionService.confirmDraft(draftId, request, currentUserAccessor.requireCurrentUser().id());
    }
}
