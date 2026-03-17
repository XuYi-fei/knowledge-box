package com.knowledgebox.repository;

import com.knowledgebox.domain.document.KnowledgeDocument;
import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long>, JpaSpecificationExecutor<KnowledgeDocument> {

    @Query("""
            select document
            from KnowledgeDocument document
            left join fetch document.category
            left join fetch document.documentColumn
            order by document.updatedAt desc, document.id desc
            """)
    List<KnowledgeDocument> findAllForAdmin();

    @Query("""
            select document
            from KnowledgeDocument document
            left join fetch document.category
            left join fetch document.documentColumn
            where document.id = :id
            """)
    Optional<KnowledgeDocument> findByIdWithCategory(@Param("id") Long id);

    @Query("""
            select document
            from KnowledgeDocument document
            left join fetch document.category
            left join fetch document.documentColumn
            where document.id = :id
              and document.visibilityType = :visibilityType
              and document.status = :status
            """)
    Optional<KnowledgeDocument> findByIdWithCategoryAndVisibilityTypeAndStatus(
            @Param("id") Long id,
            @Param("visibilityType") DocumentVisibilityType visibilityType,
            @Param("status") DocumentStatus status
    );

    @EntityGraph(attributePaths = {"category", "documentColumn"})
    Page<KnowledgeDocument> findAll(@Nullable Specification<KnowledgeDocument> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "documentColumn"})
    List<KnowledgeDocument> findAll(@Nullable Specification<KnowledgeDocument> spec, Sort sort);

    @Query("""
            select document
            from KnowledgeDocument document
            left join fetch document.category
            left join fetch document.documentColumn
            where document.visibilityType = :visibilityType
              and document.status = :status
            order by document.updatedAt desc, document.id desc
            """)
    List<KnowledgeDocument> findAllByVisibilityTypeAndStatusWithCategory(
            @Param("visibilityType") DocumentVisibilityType visibilityType,
            @Param("status") DocumentStatus status
    );

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

    @Query(
            value = """
                    select exists(
                        select 1
                        from knowledge_document
                        where md5(coalesce(source_markdown, '')) = :contentFingerprint
                    )
                    """,
            nativeQuery = true
    )
    boolean existsByContentFingerprint(@Param("contentFingerprint") String contentFingerprint);

    @Query("""
            select document
            from KnowledgeDocument document
            left join fetch document.category
            left join fetch document.documentColumn
            where document.documentColumn.id = :columnId
              and document.visibilityType = :visibilityType
              and document.status = :status
            order by document.createdAt asc, lower(document.title) asc, document.id asc
            """)
    List<KnowledgeDocument> findAllByColumnIdAndVisibilityTypeAndStatusOrderByTimeline(
            @Param("columnId") Long columnId,
            @Param("visibilityType") DocumentVisibilityType visibilityType,
            @Param("status") DocumentStatus status
    );
}
