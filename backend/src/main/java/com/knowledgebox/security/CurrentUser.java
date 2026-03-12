package com.knowledgebox.security;

public record CurrentUser(
        Long id,
        String email
) {
}
