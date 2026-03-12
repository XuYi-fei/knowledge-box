package com.knowledgebox.api;

import java.time.Instant;

public record UserAuthResponse(
        String accessToken,
        Instant expiresAt,
        UserView user,
        UserAuthAction authAction,
        String message
) {
}
