package com.knowledgebox.web.admin;

import com.knowledgebox.api.AuthorProfilePhotoUploadView;
import com.knowledgebox.api.AuthorProfileView;
import com.knowledgebox.api.UpdateAuthorProfileRequest;
import com.knowledgebox.service.about.AuthorProfileAdminService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/author-profile")
public class AdminAuthorProfileController {

    private final AuthorProfileAdminService authorProfileAdminService;

    public AdminAuthorProfileController(AuthorProfileAdminService authorProfileAdminService) {
        this.authorProfileAdminService = authorProfileAdminService;
    }

    @GetMapping
    public AuthorProfileView profile() {
        return authorProfileAdminService.profile();
    }

    @PutMapping
    public AuthorProfileView updateProfile(@Valid @RequestBody UpdateAuthorProfileRequest request) {
        return authorProfileAdminService.updateProfile(request);
    }

    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthorProfilePhotoUploadView uploadPhoto(@RequestPart("image") MultipartFile image) {
        return authorProfileAdminService.uploadPhoto(image);
    }
}
