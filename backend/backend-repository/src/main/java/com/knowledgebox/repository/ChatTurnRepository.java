package com.knowledgebox.repository;

import com.knowledgebox.domain.chat.ChatTurn;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    List<ChatTurn> findBySessionCodeOrderByIdAsc(String sessionCode);

    List<ChatTurn> findByUserIdAndSessionCodeOrderByIdAsc(Long userId, String sessionCode);

    Optional<ChatTurn> findByUserIdAndSessionCodeAndClientMessageId(Long userId, String sessionCode, String clientMessageId);

    Optional<ChatTurn> findByUserIdAndSessionCodeAndMessageCode(Long userId, String sessionCode, String messageCode);

    void deleteByUserIdAndSessionCode(Long userId, String sessionCode);

    @Modifying
    void deleteBySessionCodeIn(Collection<String> sessionCodes);
}
