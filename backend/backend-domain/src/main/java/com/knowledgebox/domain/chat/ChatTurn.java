package com.knowledgebox.domain.chat;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_turn")
public class ChatTurn extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String sessionCode;

    @Column(nullable = false, length = 64)
    private String messageCode;

    @Column(length = 64)
    private String clientMessageId;

    @Column(nullable = false, length = 16)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatMessageStatus status = ChatMessageStatus.COMPLETED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "citations_json", columnDefinition = "TEXT")
    private String citationsJson;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "reasoning_steps_json", columnDefinition = "TEXT")
    private String reasoningStepsJson;

    @Column(name = "process_details_json", columnDefinition = "TEXT")
    private String processDetailsJson;

    @Column(name = "model_code", length = 64)
    private String modelCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public void setStatus(ChatMessageStatus status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson) {
        this.citationsJson = citationsJson;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public String getReasoningStepsJson() {
        return reasoningStepsJson;
    }

    public void setReasoningStepsJson(String reasoningStepsJson) {
        this.reasoningStepsJson = reasoningStepsJson;
    }

    public String getProcessDetailsJson() {
        return processDetailsJson;
    }

    public void setProcessDetailsJson(String processDetailsJson) {
        this.processDetailsJson = processDetailsJson;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
