package com.knowledgebox.repository;

import com.knowledgebox.domain.document.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Query("select chunk from DocumentChunk chunk join fetch chunk.document")
    List<DocumentChunk> findAllWithDocument();

    List<DocumentChunk> findByDocument_IdOrderByChunkIndexAsc(Long documentId);

    void deleteByDocument_Id(Long documentId);

    @Query(value = """
            SELECT dc.*
            FROM document_chunk dc
            JOIN knowledge_document kd ON kd.id = dc.document_id
            WHERE to_tsvector('simple', coalesce(kd.title, '') || ' ' || coalesce(dc.heading_path, '') || ' ' || dc.content)
                @@ websearch_to_tsquery('simple', :query)
            ORDER BY ts_rank(
                to_tsvector('simple', coalesce(kd.title, '') || ' ' || coalesce(dc.heading_path, '') || ' ' || dc.content),
                websearch_to_tsquery('simple', :query)
            ) DESC, dc.chunk_index ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> searchByText(@Param("query") String query, @Param("limit") int limit);
}
