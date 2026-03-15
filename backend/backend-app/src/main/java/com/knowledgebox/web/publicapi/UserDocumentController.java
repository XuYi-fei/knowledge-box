package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.service.document.DocumentGovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/documents")
public class UserDocumentController {

    private final DocumentGovernanceService documentGovernanceService;

    public UserDocumentController(DocumentGovernanceService documentGovernanceService) {
        this.documentGovernanceService = documentGovernanceService;
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentView documentDetail(@PathVariable Long id) {
        return documentGovernanceService.userVisibleDocumentDetail(id);
    }
}
