package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.AboutReleaseNoteView;
import com.knowledgebox.api.AuthorProfileView;
import com.knowledgebox.service.about.AboutQueryService;
import com.knowledgebox.service.about.AuthorProfileAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicAboutController {

    private final AboutQueryService aboutQueryService;
    private final AuthorProfileAdminService authorProfileAdminService;

    public PublicAboutController(
            AboutQueryService aboutQueryService,
            AuthorProfileAdminService authorProfileAdminService
    ) {
        this.aboutQueryService = aboutQueryService;
        this.authorProfileAdminService = authorProfileAdminService;
    }

    @GetMapping("/log/release-notes")
    public List<AboutReleaseNoteView> releaseNotes() {
        return aboutQueryService.releaseNotes();
    }

    @GetMapping("/author-profile")
    public AuthorProfileView authorProfile() {
        return authorProfileAdminService.publicProfile();
    }
}
