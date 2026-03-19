package com.knowledgebox.api;

import java.util.List;

public record UserDebugChatOptionsView(
        String defaultChatModel,
        List<PublicChatModelOptionView> models,
        List<UserDebugChatEntryView> entries
) {
}
