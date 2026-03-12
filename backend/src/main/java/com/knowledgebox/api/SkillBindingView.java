package com.knowledgebox.api;

public record SkillBindingView(
        Long id,
        String code,
        String name,
        String description,
        String sourceType,
        String ossObjectKey,
        String checksumMd5,
        boolean enabled
) {
}
