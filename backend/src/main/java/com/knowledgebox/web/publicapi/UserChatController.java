package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.ChatRequest;
import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.api.ChatMessageRequest;
import com.knowledgebox.api.UserChatSessionDetailView;
import com.knowledgebox.api.UserChatSessionSummaryView;
import com.knowledgebox.security.CurrentUserAccessor;
import com.knowledgebox.service.chat.ChatOrchestrator;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/app/chat")
public class UserChatController {

    private final ChatOrchestrator chatOrchestrator;
    private final CurrentUserAccessor currentUserAccessor;

    public UserChatController(ChatOrchestrator chatOrchestrator, CurrentUserAccessor currentUserAccessor) {
        this.chatOrchestrator = chatOrchestrator;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping("/options")
    public com.knowledgebox.api.PublicChatOptionsView options() {
        return chatOrchestrator.options();
    }

    @GetMapping("/sessions")
    public List<UserChatSessionSummaryView> sessions() {
        return chatOrchestrator.sessions(currentUserAccessor.requireCurrentUser().id());
    }

    @GetMapping("/sessions/{sessionId}")
    public UserChatSessionDetailView sessionDetail(@PathVariable String sessionId) {
        return chatOrchestrator.sessionDetail(currentUserAccessor.requireCurrentUser().id(), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        chatOrchestrator.deleteSession(currentUserAccessor.requireCurrentUser().id(), sessionId);
    }

    @PostMapping("/messages")
    public ChatResponse answer(@Valid @RequestBody ChatRequest request) {
        return chatOrchestrator.answerLegacy(currentUserAccessor.requireCurrentUser().id(), request);
    }

    @PostMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatMessageRequest request) {
        return chatOrchestrator.stream(currentUserAccessor.requireCurrentUser().id(), request);
    }

    @GetMapping(path = "/sessions/{sessionId}/messages/{messageId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@PathVariable String sessionId, @PathVariable String messageId) {
        return chatOrchestrator.resume(currentUserAccessor.requireCurrentUser().id(), sessionId, messageId);
    }
}
