package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentTagBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTagBindingRepository extends JpaRepository<DocumentTagBinding, Long> {

    List<DocumentTagBinding> findByDocument_IdOrderByIdAsc(Long documentId);

    void deleteByDocument_Id(Long documentId);
}
