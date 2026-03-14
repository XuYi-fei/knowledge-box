package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentIndexRebuildJob;
import com.knowledgebox.domain.document.DocumentIndexRebuildStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIndexRebuildJobRepository extends JpaRepository<DocumentIndexRebuildJob, Long> {

    Optional<DocumentIndexRebuildJob> findFirstByOrderByStartedAtDesc();

    Optional<DocumentIndexRebuildJob> findFirstByStatusOrderByStartedAtDesc(DocumentIndexRebuildStatus status);
}
