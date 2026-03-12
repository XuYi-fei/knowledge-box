package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionCode(String sessionCode);

    Optional<ChatSession> findByUserIdAndSessionCode(Long userId, String sessionCode);

    List<ChatSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    void deleteByUserIdAndSessionCode(Long userId, String sessionCode);
}
