package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentTag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    Optional<DocumentTag> findByNameIgnoreCase(String name);

    List<DocumentTag> findAllByOrderByNameAsc();

    List<DocumentTag> findAllByNameIn(Collection<String> names);
}
