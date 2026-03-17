package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.PublicDocumentFacetView;
import com.knowledgebox.api.PublicDocumentPageView;
import com.knowledgebox.service.document.DocumentGovernanceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/documents")
public class PublicDocumentController {

    private final DocumentGovernanceService documentGovernanceService;

    public PublicDocumentController(DocumentGovernanceService documentGovernanceService) {
        this.documentGovernanceService = documentGovernanceService;
    }

    @GetMapping
    public PublicDocumentPageView documents(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize
    ) {
        return documentGovernanceService.publicDocuments(categoryId, tagId, page, pageSize);
    }

    @GetMapping("/facets")
    public PublicDocumentFacetView facets() {
        return documentGovernanceService.publicDocumentFacets();
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentView documentDetail(@PathVariable Long id) {
        return documentGovernanceService.userVisibleDocumentDetail(id);
    }
}
