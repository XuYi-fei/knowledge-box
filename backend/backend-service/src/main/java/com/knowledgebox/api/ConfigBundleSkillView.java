package com.knowledgebox.api;

public record ConfigBundleSkillView(
        String code,
        String name,
        String description,
        String sourceType,
        String checksumMd5,
        String ossObjectKey,
        String packageLocation,
        java.util.List<RuntimeEnvRequirementView> runtimeEnvRequirements,
        boolean enabled
) {
}
