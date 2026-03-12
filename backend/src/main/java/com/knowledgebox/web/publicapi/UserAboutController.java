package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.AboutReleaseNoteView;
import com.knowledgebox.service.about.AboutQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/about")
public class UserAboutController {

    private final AboutQueryService aboutQueryService;

    public UserAboutController(AboutQueryService aboutQueryService) {
        this.aboutQueryService = aboutQueryService;
    }

    @GetMapping("/release-notes")
    public List<AboutReleaseNoteView> releaseNotes() {
        return aboutQueryService.releaseNotes();
    }
}
