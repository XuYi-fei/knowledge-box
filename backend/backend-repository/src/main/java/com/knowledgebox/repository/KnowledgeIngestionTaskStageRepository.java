package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeIngestionTaskStage;
import com.knowledgebox.domain.document.KnowledgeIngestionTaskStageCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeIngestionTaskStageRepository extends JpaRepository<KnowledgeIngestionTaskStage, Long> {

    List<KnowledgeIngestionTaskStage> findAllByTask_IdOrderBySortOrderAscIdAsc(Long taskId);

    Optional<KnowledgeIngestionTaskStage> findByTask_IdAndStageCode(Long taskId, KnowledgeIngestionTaskStageCode stageCode);
}
