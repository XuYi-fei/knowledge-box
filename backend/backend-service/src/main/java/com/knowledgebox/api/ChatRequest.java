package com.knowledgebox.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String sessionId,
        @NotBlank String query,
        String clientTraceId,
        String chatModel
) {
}
