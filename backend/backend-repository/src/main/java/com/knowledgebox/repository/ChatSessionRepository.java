package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionCode(String sessionCode);

    Optional<ChatSession> findByUserIdAndSessionCode(Long userId, String sessionCode);

    List<ChatSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    List<ChatSession> findAllByUserIdAndActiveProfileCodeOrderByUpdatedAtDesc(Long userId, String activeProfileCode);

    List<ChatSession> findAllByActiveProfileCode(String activeProfileCode);

    @Query("select distinct session.activeProfileCode from ChatSession session where session.userId = :userId")
    List<String> findDistinctActiveProfileCodesByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndSessionCode(Long userId, String sessionCode);

    @Modifying
    void deleteByActiveProfileCode(String activeProfileCode);
}
