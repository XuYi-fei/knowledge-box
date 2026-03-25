package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeIngestionTaskDocumentRepository extends JpaRepository<KnowledgeIngestionTaskDocument, Long> {

    List<KnowledgeIngestionTaskDocument> findAllByTask_IdOrderBySegmentIndexAscIdAsc(Long taskId);

    Optional<KnowledgeIngestionTaskDocument> findByIdAndTask_Id(Long id, Long taskId);
}
