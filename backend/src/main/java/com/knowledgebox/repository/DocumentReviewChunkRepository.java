package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentReviewChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewChunkRepository extends JpaRepository<DocumentReviewChunk, Long> {

    List<DocumentReviewChunk> findByReviewRequest_IdOrderByChunkIndexAsc(Long reviewRequestId);

    void deleteByReviewRequest_Id(Long reviewRequestId);
}
