package com.knowledgebox.domain.document;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 256)
    private String sourceFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentUploaderType uploaderType = DocumentUploaderType.ADMIN;

    @Column
    private Long uploaderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentVisibilityType visibilityType = DocumentVisibilityType.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(nullable = false, length = 256)
    private String normalizedMarkdownPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceMarkdown = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String extensionJson = "{}";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String vectorConfigJson = "{}";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DocumentCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tags = "[]";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public DocumentUploaderType getUploaderType() {
        return uploaderType;
    }

    public void setUploaderType(DocumentUploaderType uploaderType) {
        this.uploaderType = uploaderType;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public DocumentVisibilityType getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(DocumentVisibilityType visibilityType) {
        this.visibilityType = visibilityType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getNormalizedMarkdownPath() {
        return normalizedMarkdownPath;
    }

    public void setNormalizedMarkdownPath(String normalizedMarkdownPath) {
        this.normalizedMarkdownPath = normalizedMarkdownPath;
    }

    public String getSourceMarkdown() {
        return sourceMarkdown;
    }

    public void setSourceMarkdown(String sourceMarkdown) {
        this.sourceMarkdown = sourceMarkdown;
    }

    public String getExtensionJson() {
        return extensionJson;
    }

    public void setExtensionJson(String extensionJson) {
        this.extensionJson = extensionJson;
    }

    public String getVectorConfigJson() {
        return vectorConfigJson;
    }

    public void setVectorConfigJson(String vectorConfigJson) {
        this.vectorConfigJson = vectorConfigJson;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
