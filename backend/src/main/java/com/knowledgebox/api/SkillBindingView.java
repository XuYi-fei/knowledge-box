package com.knowledgebox.api;

public record SkillBindingView(
        Long id,
        String code,
        String name,
        boolean enabled
) {
}

