package com.knowledgebox.api;

public record RuntimeEnvRequirementView(
        String key,
        boolean required,
        boolean secret,
        String description
) {
}
