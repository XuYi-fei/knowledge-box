package com.knowledgebox.api;

import com.knowledgebox.domain.hook.HookEventType;

public record WebhookSubscriptionView(
        Long id,
        HookEventType eventType,
        String targetUrl,
        String secretMasked,
        boolean enabled
) {
}

