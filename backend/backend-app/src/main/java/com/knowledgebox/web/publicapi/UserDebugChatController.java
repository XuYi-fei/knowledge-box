package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.AgentExecutionTracePageView;
import com.knowledgebox.api.DebugChatMessageRequest;
import com.knowledgebox.api.UserChatMessageView;
import com.knowledgebox.api.UserChatSessionDetailView;
import com.knowledgebox.api.UserChatSessionSummaryView;
import com.knowledgebox.api.UserDebugChatOptionsView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/app/agent-debug")
public class UserDebugChatController {

    private final ChatOrchestrator chatOrchestrator;
    private final CurrentUserAccessor currentUserAccessor;

    public UserDebugChatController(ChatOrchestrator chatOrchestrator, CurrentUserAccessor currentUserAccessor) {
        this.chatOrchestrator = chatOrchestrator;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping("/options")
    public UserDebugChatOptionsView options() {
        return chatOrchestrator.debugOptions(currentUserAccessor.requireCurrentUser().id());
    }

    @GetMapping("/{profileCode}/sessions")
    public List<UserChatSessionSummaryView> sessions(@PathVariable String profileCode) {
        return chatOrchestrator.debugSessions(currentUserAccessor.requireCurrentUser().id(), profileCode);
    }

    @GetMapping("/{profileCode}/sessions/{sessionId}")
    public UserChatSessionDetailView sessionDetail(@PathVariable String profileCode, @PathVariable String sessionId) {
        return chatOrchestrator.debugSessionDetail(currentUserAccessor.requireCurrentUser().id(), profileCode, sessionId);
    }

    @DeleteMapping("/{profileCode}/sessions/{sessionId}")
    public void deleteSession(@PathVariable String profileCode, @PathVariable String sessionId) {
        chatOrchestrator.debugDeleteSession(currentUserAccessor.requireCurrentUser().id(), profileCode, sessionId);
    }

    @GetMapping("/{profileCode}/traces")
    public AgentExecutionTracePageView traces(
            @PathVariable String profileCode,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return chatOrchestrator.debugTraces(currentUserAccessor.requireCurrentUser().id(), profileCode, sessionId, page, pageSize);
    }

    @PostMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody DebugChatMessageRequest request) {
        return chatOrchestrator.debugStream(currentUserAccessor.requireCurrentUser().id(), request);
    }

    @GetMapping(path = "/{profileCode}/sessions/{sessionId}/messages/{messageId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@PathVariable String profileCode, @PathVariable String sessionId, @PathVariable String messageId) {
        return chatOrchestrator.debugResume(currentUserAccessor.requireCurrentUser().id(), profileCode, sessionId, messageId);
    }

    @PostMapping("/{profileCode}/sessions/{sessionId}/messages/{messageId}/stop")
    public UserChatMessageView stop(@PathVariable String profileCode, @PathVariable String sessionId, @PathVariable String messageId) {
        return chatOrchestrator.debugStop(currentUserAccessor.requireCurrentUser().id(), profileCode, sessionId, messageId);
    }
}
