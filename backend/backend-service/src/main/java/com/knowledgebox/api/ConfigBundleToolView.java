package com.knowledgebox.api;

public record ConfigBundleToolView(
        String code,
        String name,
        String className,
        String beanName,
        String configJson,
        boolean enabled
) {
}
