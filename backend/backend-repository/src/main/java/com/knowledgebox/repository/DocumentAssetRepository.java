package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAssetRepository extends JpaRepository<DocumentAsset, Long> {

    List<DocumentAsset> findByDocument_IdOrderByIdAsc(Long documentId);

    void deleteByDocument_Id(Long documentId);
}
