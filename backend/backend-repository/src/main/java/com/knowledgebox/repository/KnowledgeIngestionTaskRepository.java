package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeIngestionTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeIngestionTaskRepository extends JpaRepository<KnowledgeIngestionTask, Long> {

    Optional<KnowledgeIngestionTask> findByIdAndUserId(Long id, Long userId);

    Optional<KnowledgeIngestionTask> findFirstByUserIdAndSourceFileContentHashOrderByUpdatedAtDescIdDesc(Long userId, String sourceFileContentHash);

    List<KnowledgeIngestionTask> findAllByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
}
