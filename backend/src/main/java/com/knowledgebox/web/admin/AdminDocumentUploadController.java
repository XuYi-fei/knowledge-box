package com.knowledgebox.web.admin;

import com.knowledgebox.api.CreateDocumentReviewRequest;
import com.knowledgebox.api.DocumentPastedImageUploadView;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import com.knowledgebox.service.admin.AdminOperatorService;
import com.knowledgebox.service.document.DocumentGovernanceService;
import com.knowledgebox.service.document.DocumentUploadResult;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentUploadController {

    private final DocumentGovernanceService documentGovernanceService;
    private final AdminOperatorService adminOperatorService;

    public AdminDocumentUploadController(
            DocumentGovernanceService documentGovernanceService,
            AdminOperatorService adminOperatorService
    ) {
        this.documentGovernanceService = documentGovernanceService;
        this.adminOperatorService = adminOperatorService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentUploadResult upload(
            @RequestPart("markdown") MultipartFile markdown,
            @RequestPart(name = "assets", required = false) List<MultipartFile> assets,
            @RequestPart(name = "title", required = false) String title,
            @RequestPart(name = "visibilityType", required = false) String visibilityType,
            @RequestPart(name = "extensionJson", required = false) String extensionJson,
            Principal principal
    ) {
        DocumentVisibilityType resolvedVisibilityType = null;
        if (visibilityType != null && !visibilityType.isBlank()) {
            resolvedVisibilityType = DocumentVisibilityType.valueOf(visibilityType.trim().toUpperCase());
        }
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.createUploadReview(
                markdown,
                assets == null ? List.of() : assets,
                title,
                resolvedVisibilityType,
                extensionJson,
                operatorId
        );
    }

    @PostMapping(path = "/upload-json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentUploadResult uploadJson(
            @Valid @RequestBody CreateDocumentReviewRequest request,
            Principal principal
    ) {
        Long operatorId = adminOperatorService.resolveOperatorId(principal == null ? "admin" : principal.getName());
        return documentGovernanceService.createUploadReview(request, operatorId);
    }

    @PostMapping(path = "/paste-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentPastedImageUploadView uploadPastedImage(
            @RequestPart("image") MultipartFile image
    ) {
        return documentGovernanceService.uploadPastedImage(image);
    }
}
