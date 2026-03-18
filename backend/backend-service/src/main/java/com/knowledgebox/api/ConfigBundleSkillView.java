package com.knowledgebox.api;

public record ConfigBundleSkillView(
        String code,
        String name,
        String description,
        String sourceType,
        String checksumMd5,
        String ossObjectKey,
        String packageLocation,
        boolean enabled
) {
}
