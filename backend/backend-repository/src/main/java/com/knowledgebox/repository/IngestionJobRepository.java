package com.knowledgebox.repository;

import com.knowledgebox.domain.document.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
}
