package com.knowledgebox.api;

import com.knowledgebox.domain.document.DocumentStatus;
import com.knowledgebox.domain.document.DocumentUploaderType;
import com.knowledgebox.domain.document.DocumentVisibilityType;
import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeDocumentView(
        Long id,
        String title,
        String sourceFilename,
        DocumentStatus status,
        DocumentVisibilityType visibilityType,
        DocumentUploaderType uploaderType,
        Long uploaderUserId,
        String normalizedMarkdownPath,
        String sourceMarkdown,
        String extensionJson,
        String vectorConfigJson,
        String categoryName,
        String columnName,
        String tags,
        List<DocumentColumnDocumentView> columnDocuments,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
