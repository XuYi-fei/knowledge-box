package com.knowledgebox.repository;

import com.knowledgebox.domain.about.AboutReleaseNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AboutReleaseNoteRepository extends JpaRepository<AboutReleaseNote, Long> {

    List<AboutReleaseNote> findAllByOrderByPublishedAtDescDisplayOrderAscIdDesc();
}
