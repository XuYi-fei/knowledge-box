package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentTagBinding;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTagBindingRepository extends JpaRepository<DocumentTagBinding, Long> {

    List<DocumentTagBinding> findByDocument_IdOrderByIdAsc(Long documentId);

    @Query("""
            select binding
            from DocumentTagBinding binding
            join fetch binding.tag
            where binding.document.id in :documentIds
            order by binding.document.id asc, binding.id asc
            """)
    List<DocumentTagBinding> findAllWithTagByDocumentIds(@Param("documentIds") Collection<Long> documentIds);

    void deleteByDocument_Id(Long documentId);
}
