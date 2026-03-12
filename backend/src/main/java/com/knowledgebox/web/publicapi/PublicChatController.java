package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.PublicChatOptionsView;
import com.knowledgebox.service.chat.ChatOrchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/public/chat")
public class PublicChatController {

    private final ChatOrchestrator chatOrchestrator;

    public PublicChatController(ChatOrchestrator chatOrchestrator) {
        this.chatOrchestrator = chatOrchestrator;
    }

    @GetMapping("/options")
    public PublicChatOptionsView options() {
        return chatOrchestrator.options();
    }
}
