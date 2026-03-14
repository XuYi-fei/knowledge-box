package com.knowledgebox.api;

import java.util.List;

public record PublicChatOptionsView(
        String activeChatModel,
        String defaultChatModel,
        List<PublicChatModelOptionView> models
) {
}
