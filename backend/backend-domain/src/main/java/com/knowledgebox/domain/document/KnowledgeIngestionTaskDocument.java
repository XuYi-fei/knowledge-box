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
@Table(name = "knowledge_ingestion_task_document")
public class KnowledgeIngestionTaskDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private KnowledgeIngestionTask task;

    @Column(name = "document_code", nullable = false, unique = true, length = 64)
    private String documentCode;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex = 0;

    @Column(name = "page_from_number")
    private Integer pageFromNumber;

    @Column(name = "page_to_number")
    private Integer pageToNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeIngestionTaskDocumentStatus status = KnowledgeIngestionTaskDocumentStatus.PLANNED;

    @Column(name = "suggested_title", length = 256)
    private String suggestedTitle;

    @Column(name = "suggested_category_name", length = 128)
    private String suggestedCategoryName;

    @Column(name = "suggested_tags_json", nullable = false, columnDefinition = "TEXT")
    private String suggestedTagsJson = "[]";

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "analysis_reasoning", columnDefinition = "TEXT")
    private String analysisReasoning;

    @Column(name = "generated_markdown", columnDefinition = "TEXT")
    private String generatedMarkdown;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_request_id")
    private DocumentReviewRequest reviewRequest;

    public KnowledgeIngestionTask getTask() {
        return task;
    }

    public void setTask(KnowledgeIngestionTask task) {
        this.task = task;
    }

    public String getDocumentCode() {
        return documentCode;
    }

    public void setDocumentCode(String documentCode) {
        this.documentCode = documentCode;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public Integer getPageFromNumber() {
        return pageFromNumber;
    }

    public void setPageFromNumber(Integer pageFromNumber) {
        this.pageFromNumber = pageFromNumber;
    }

    public Integer getPageToNumber() {
        return pageToNumber;
    }

    public void setPageToNumber(Integer pageToNumber) {
        this.pageToNumber = pageToNumber;
    }

    public KnowledgeIngestionTaskDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeIngestionTaskDocumentStatus status) {
        this.status = status;
    }

    public String getSuggestedTitle() {
        return suggestedTitle;
    }

    public void setSuggestedTitle(String suggestedTitle) {
        this.suggestedTitle = suggestedTitle;
    }

    public String getSuggestedCategoryName() {
        return suggestedCategoryName;
    }

    public void setSuggestedCategoryName(String suggestedCategoryName) {
        this.suggestedCategoryName = suggestedCategoryName;
    }

    public String getSuggestedTagsJson() {
        return suggestedTagsJson;
    }

    public void setSuggestedTagsJson(String suggestedTagsJson) {
        this.suggestedTagsJson = suggestedTagsJson;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getAnalysisReasoning() {
        return analysisReasoning;
    }

    public void setAnalysisReasoning(String analysisReasoning) {
        this.analysisReasoning = analysisReasoning;
    }

    public String getGeneratedMarkdown() {
        return generatedMarkdown;
    }

    public void setGeneratedMarkdown(String generatedMarkdown) {
        this.generatedMarkdown = generatedMarkdown;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public DocumentReviewRequest getReviewRequest() {
        return reviewRequest;
    }

    public void setReviewRequest(DocumentReviewRequest reviewRequest) {
        this.reviewRequest = reviewRequest;
    }
}
