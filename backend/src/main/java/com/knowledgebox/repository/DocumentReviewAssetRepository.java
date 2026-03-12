package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentReviewAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewAssetRepository extends JpaRepository<DocumentReviewAsset, Long> {

    List<DocumentReviewAsset> findByReviewRequest_IdOrderByIdAsc(Long reviewRequestId);

    void deleteByReviewRequest_Id(Long reviewRequestId);
}
