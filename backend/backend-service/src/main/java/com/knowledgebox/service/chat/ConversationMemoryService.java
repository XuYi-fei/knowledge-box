package com.knowledgebox.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ChatCitationView;
import com.knowledgebox.api.UserChatMessageView;
import com.knowledgebox.api.UserChatSessionDetailView;
import com.knowledgebox.api.UserChatSessionSummaryView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import com.knowledgebox.domain.chat.ChatSession;
import com.knowledgebox.domain.chat.ChatSessionStatus;
import com.knowledgebox.domain.chat.ChatTurn;
import com.knowledgebox.repository.ChatSessionRepository;
import com.knowledgebox.repository.ChatTurnRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationMemoryService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatCitationView>> CITATION_LIST = new TypeReference<>() {
    };

    private final ChatSessionRepository chatSessionRepository;
    private final ChatTurnRepository chatTurnRepository;
    private final ObjectMapper objectMapper;

    public ConversationMemoryService(
            ChatSessionRepository chatSessionRepository,
            ChatTurnRepository chatTurnRepository,
            ObjectMapper objectMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatTurnRepository = chatTurnRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatSession ensureSession(Long userId, String sessionCode, String profileCode, String chatModel, String titleHint) {
        ChatSession session = chatSessionRepository.findByUserIdAndSessionCode(userId, sessionCode)
                .orElseGet(() -> {
                    ChatSession created = new ChatSession();
                    created.setUserId(userId);
                    created.setSessionCode(sessionCode);
                    created.setActiveProfileCode(profileCode);
                    created.setStatus(ChatSessionStatus.ACTIVE);
                    return created;
                });

        session.setSelectedChatModel(chatModel);
        if ((session.getTitle() == null || session.getTitle().isBlank()) && titleHint != null && !titleHint.isBlank()) {
            session.setTitle(buildSessionTitle(titleHint));
        }
        session.setLastMessageAt(OffsetDateTime.now());
        return chatSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatTurn> history(Long userId, String sessionCode, int limit) {
        List<ChatTurn> turns = chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, sessionCode);
        List<ChatTurn> completedTurns = turns.stream()
                .filter(turn -> turn.getStatus() == ChatMessageStatus.COMPLETED)
                .filter(turn -> "assistant".equals(turn.getRole()) || "user".equals(turn.getRole()))
                .toList();
        int skip = Math.max(0, completedTurns.size() - limit);
        return completedTurns.stream().skip(skip).toList();
    }

    @Transactional
    public ChatTurn appendUserMessage(Long userId, String sessionCode, String clientMessageId, String content) {
        return chatTurnRepository.findByUserIdAndSessionCodeAndClientMessageId(userId, sessionCode, clientMessageId)
                .orElseGet(() -> {
                    ChatTurn turn = new ChatTurn();
                    turn.setUserId(userId);
                    turn.setSessionCode(sessionCode);
                    turn.setMessageCode(UUID.randomUUID().toString());
                    turn.setClientMessageId(clientMessageId);
                    turn.setRole("user");
                    turn.setStatus(ChatMessageStatus.COMPLETED);
                    turn.setContent(content);
                    turn.setCompletedAt(OffsetDateTime.now());
                    return chatTurnRepository.save(turn);
                });
    }

    @Transactional
    public ChatTurn createAssistantPlaceholder(Long userId, String sessionCode, String modelCode) {
        ChatTurn turn = new ChatTurn();
        turn.setUserId(userId);
        turn.setSessionCode(sessionCode);
        turn.setMessageCode(UUID.randomUUID().toString());
        turn.setRole("assistant");
        turn.setStatus(ChatMessageStatus.PENDING);
        turn.setContent("");
        turn.setModelCode(modelCode);
        return chatTurnRepository.save(turn);
    }

    @Transactional
    public ChatTurn markAssistantStreaming(
            Long userId,
            String sessionCode,
            String messageCode,
            String content,
            List<String> reasoningSteps
    ) {
        ChatTurn turn = loadMessage(userId, sessionCode, messageCode);
        turn.setStatus(ChatMessageStatus.STREAMING);
        turn.setContent(content);
        turn.setReasoningStepsJson(writeJson(reasoningSteps));
        touchSession(userId, sessionCode, turn.getModelCode(), null);
        return chatTurnRepository.save(turn);
    }

    @Transactional
    public ChatTurn completeAssistantMessage(
            Long userId,
            String sessionCode,
            String messageCode,
            String content,
            List<String> reasoningSteps,
            List<ChatCitationView> citations,
            List<String> toolCalls
    ) {
        ChatTurn turn = loadMessage(userId, sessionCode, messageCode);
        turn.setStatus(ChatMessageStatus.COMPLETED);
        turn.setContent(content);
        turn.setReasoningStepsJson(writeJson(reasoningSteps));
        turn.setCitationsJson(writeJson(citations));
        turn.setToolCallsJson(writeJson(toolCalls));
        turn.setCompletedAt(OffsetDateTime.now());
        touchSession(userId, sessionCode, turn.getModelCode(), null);
        return chatTurnRepository.save(turn);
    }

    @Transactional
    public ChatTurn failAssistantMessage(
            Long userId,
            String sessionCode,
            String messageCode,
            String partialContent,
            List<String> reasoningSteps,
            String errorMessage
    ) {
        ChatTurn turn = loadMessage(userId, sessionCode, messageCode);
        turn.setStatus(ChatMessageStatus.FAILED);
        turn.setContent(partialContent);
        turn.setReasoningStepsJson(writeJson(reasoningSteps));
        turn.setErrorMessage(errorMessage);
        turn.setCompletedAt(OffsetDateTime.now());
        touchSession(userId, sessionCode, turn.getModelCode(), null);
        return chatTurnRepository.save(turn);
    }

    @Transactional(readOnly = true)
    public List<UserChatSessionSummaryView> listSessions(Long userId) {
        return chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> {
                    List<ChatTurn> turns = chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, session.getSessionCode());
                    ChatTurn lastTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);
                    boolean pending = turns.stream().anyMatch(turn ->
                            "assistant".equals(turn.getRole())
                                    && (turn.getStatus() == ChatMessageStatus.PENDING || turn.getStatus() == ChatMessageStatus.STREAMING)
                    );
                    String title = session.getTitle() == null || session.getTitle().isBlank() ? "新对话" : session.getTitle();
                    return new UserChatSessionSummaryView(
                            session.getSessionCode(),
                            title,
                            session.getSelectedChatModel(),
                            turns.size(),
                            lastTurn == null ? "" : summarize(lastTurn.getContent()),
                            pending,
                            session.getUpdatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public UserChatSessionDetailView sessionDetail(Long userId, String sessionCode) {
        ChatSession session = chatSessionRepository.findByUserIdAndSessionCode(userId, sessionCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "对话会话不存在"));
        return new UserChatSessionDetailView(
                session.getSessionCode(),
                session.getTitle() == null || session.getTitle().isBlank() ? "新对话" : session.getTitle(),
                session.getSelectedChatModel(),
                chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, sessionCode).stream()
                        .map(this::toView)
                        .toList()
                );
    }

    @Transactional
    public void deleteSession(Long userId, String sessionCode) {
        chatSessionRepository.findByUserIdAndSessionCode(userId, sessionCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "对话会话不存在"));
        chatTurnRepository.deleteByUserIdAndSessionCode(userId, sessionCode);
        chatSessionRepository.deleteByUserIdAndSessionCode(userId, sessionCode);
    }

    @Transactional(readOnly = true)
    public List<String> listActiveAssistantMessageCodes(Long userId, String sessionCode) {
        return chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, sessionCode).stream()
                .filter(turn -> "assistant".equals(turn.getRole()))
                .filter(turn -> turn.getStatus() == ChatMessageStatus.PENDING || turn.getStatus() == ChatMessageStatus.STREAMING)
                .map(ChatTurn::getMessageCode)
                .toList();
    }

    @Transactional(readOnly = true)
    public String findUserQueryForAssistantMessage(Long userId, String sessionCode, String messageCode) {
        List<ChatTurn> turns = chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, sessionCode);
        for (int index = 0; index < turns.size(); index++) {
            ChatTurn turn = turns.get(index);
            if (!"assistant".equals(turn.getRole()) || !messageCode.equals(turn.getMessageCode())) {
                continue;
            }
            for (int cursor = index - 1; cursor >= 0; cursor--) {
                ChatTurn candidate = turns.get(cursor);
                if (!"user".equals(candidate.getRole())) {
                    continue;
                }
                String content = candidate.getContent();
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }
            break;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public ChatTurn loadMessage(Long userId, String sessionCode, String messageCode) {
        return chatTurnRepository.findByUserIdAndSessionCodeAndMessageCode(userId, sessionCode, messageCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "对话消息不存在"));
    }

    @Transactional(readOnly = true)
    public ChatTurn findAssistantByClientMessageId(Long userId, String sessionCode, String clientMessageId) {
        List<ChatTurn> turns = chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(userId, sessionCode);
        for (int index = 0; index < turns.size(); index++) {
            ChatTurn turn = turns.get(index);
            if ("user".equals(turn.getRole()) && clientMessageId.equals(turn.getClientMessageId())) {
                for (int next = index + 1; next < turns.size(); next++) {
                    if ("assistant".equals(turns.get(next).getRole())) {
                        return turns.get(next);
                    }
                }
            }
        }
        return null;
    }

    private void touchSession(Long userId, String sessionCode, String chatModel, String titleHint) {
        ChatSession session = ensureSession(userId, sessionCode, "default-qa", chatModel, titleHint);
        session.setUpdatedAt(OffsetDateTime.now());
        session.setLastMessageAt(OffsetDateTime.now());
        chatSessionRepository.save(session);
    }

    private UserChatMessageView toView(ChatTurn turn) {
        return new UserChatMessageView(
                turn.getMessageCode(),
                turn.getClientMessageId(),
                turn.getRole(),
                turn.getContent(),
                turn.getStatus().name(),
                readStringList(turn.getReasoningStepsJson()),
                readCitations(turn.getCitationsJson()),
                readStringList(turn.getToolCallsJson()),
                turn.getModelCode(),
                turn.getErrorMessage(),
                turn.getCreatedAt(),
                turn.getCompletedAt()
        );
    }

    private String buildSessionTitle(String query) {
        return query.length() > 24 ? query.substring(0, 24) + "..." : query;
    }

    private String summarize(String content) {
        return content.length() > 42 ? content.substring(0, 42) + "..." : content;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat metadata");
        }
    }

    private List<String> readStringList(String value) {
        return readJson(value, STRING_LIST);
    }

    private List<ChatCitationView> readCitations(String value) {
        return readJson(value, CITATION_LIST);
    }

    private <T> T readJson(String value, TypeReference<T> typeReference) {
        if (value == null || value.isBlank()) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to deserialize default chat metadata");
            }
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chat metadata");
        }
    }
}
