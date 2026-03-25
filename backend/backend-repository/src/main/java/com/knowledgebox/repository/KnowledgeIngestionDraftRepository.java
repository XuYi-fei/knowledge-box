package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeIngestionDraftRepository extends JpaRepository<KnowledgeIngestionDraft, Long> {

    Optional<KnowledgeIngestionDraft> findByIdAndUserId(Long id, Long userId);
}
