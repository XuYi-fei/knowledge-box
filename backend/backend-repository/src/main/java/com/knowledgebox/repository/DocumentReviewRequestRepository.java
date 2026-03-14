package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentReviewRequest;
import com.knowledgebox.domain.document.DocumentReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentReviewRequestRepository extends JpaRepository<DocumentReviewRequest, Long> {

    Optional<DocumentReviewRequest> findByRequestCode(String requestCode);

    List<DocumentReviewRequest> findAllByOrderByUpdatedAtDescIdDesc();

    List<DocumentReviewRequest> findAllByStatusOrderByUpdatedAtDesc(DocumentReviewStatus status);

    Page<DocumentReviewRequest> findByStatus(DocumentReviewStatus status, Pageable pageable);

    Optional<DocumentReviewRequest> findFirstBySourceDocument_IdAndStatusInOrderByUpdatedAtDescIdDesc(
            Long sourceDocumentId,
            List<DocumentReviewStatus> statuses
    );

    @Query(
            value = """
                    select exists(
                        select 1
                        from document_review_request
                        where coalesce(extension_json, '{}')::jsonb ->> 'importKey' = :importKey
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByImportKey(@Param("importKey") String importKey);
}
