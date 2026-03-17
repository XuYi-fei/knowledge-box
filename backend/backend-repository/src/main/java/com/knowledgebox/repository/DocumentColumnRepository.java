package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentColumn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentColumnRepository extends JpaRepository<DocumentColumn, Long> {

    Optional<DocumentColumn> findByNameIgnoreCase(String name);

    List<DocumentColumn> findAllByOrderByNameAsc();
}
