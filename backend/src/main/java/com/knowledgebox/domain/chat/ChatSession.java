package com.knowledgebox.domain.chat;

import com.knowledgebox.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session")
public class ChatSession extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String sessionCode;

    @Column(nullable = false, length = 32)
    private String activeProfileCode;

    @Column(length = 128)
    private String title;

    @Column(length = 64)
    private String selectedChatModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatSessionStatus status = ChatSessionStatus.ACTIVE;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

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

    public String getActiveProfileCode() {
        return activeProfileCode;
    }

    public void setActiveProfileCode(String activeProfileCode) {
        this.activeProfileCode = activeProfileCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSelectedChatModel() {
        return selectedChatModel;
    }

    public void setSelectedChatModel(String selectedChatModel) {
        this.selectedChatModel = selectedChatModel;
    }

    public ChatSessionStatus getStatus() {
        return status;
    }

    public void setStatus(ChatSessionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(OffsetDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
