package com.knowledgebox.api;

import java.util.List;

public record UserChatSessionDetailView(
        String sessionId,
        String title,
        String selectedChatModel,
        List<UserChatMessageView> messages
) {
}
