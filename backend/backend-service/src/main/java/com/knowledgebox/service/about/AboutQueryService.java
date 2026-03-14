package com.knowledgebox.service.about;

import com.knowledgebox.api.AboutReleaseNoteView;
import com.knowledgebox.repository.AboutReleaseNoteRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AboutQueryService {

    private final AboutReleaseNoteRepository aboutReleaseNoteRepository;

    public AboutQueryService(AboutReleaseNoteRepository aboutReleaseNoteRepository) {
        this.aboutReleaseNoteRepository = aboutReleaseNoteRepository;
    }

    public List<AboutReleaseNoteView> releaseNotes() {
        return aboutReleaseNoteRepository.findAllByOrderByPublishedAtDescDisplayOrderAscIdDesc().stream()
                .map(note -> new AboutReleaseNoteView(
                        note.getId(),
                        note.getVersionLabel(),
                        note.getTitle(),
                        note.getSummary(),
                        note.getContentMarkdown(),
                        note.getPublishedAt(),
                        Boolean.TRUE.equals(note.getHighlighted())
                ))
                .toList();
    }
}
