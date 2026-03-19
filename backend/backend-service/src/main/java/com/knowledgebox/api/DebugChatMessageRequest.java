package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DebugChatMessageRequest(
        @NotBlank @Size(max = 64) String profileCode,
        @NotBlank @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 64) String clientMessageId,
        @NotBlank @Size(max = 8000) String query,
        @Size(max = 64) String chatModel
) {
}
