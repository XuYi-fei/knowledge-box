package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    @Query("select document from KnowledgeDocument document left join fetch document.category order by document.updatedAt desc, document.id desc")
    List<KnowledgeDocument> findAllForAdmin();

    @Query("select document from KnowledgeDocument document left join fetch document.category where document.id = :id")
    Optional<KnowledgeDocument> findByIdWithCategory(@Param("id") Long id);

    @Query(
            value = """
                    select exists(
                        select 1
                        from knowledge_document
                        where coalesce(extension_json, '{}')::jsonb ->> 'importKey' = :importKey
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByImportKey(@Param("importKey") String importKey);
}
