package com.knowledgebox.service.chat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public class AgentRuntimeEnvironment {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-.]+)}");

    private final Map<String, String> valuesByKey;

    public AgentRuntimeEnvironment(Map<String, String> valuesByKey) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (valuesByKey != null) {
            valuesByKey.forEach((key, value) -> {
                if (!StringUtils.hasText(key) || value == null) {
                    return;
                }
                normalized.put(normalizeKey(key), value);
            });
        }
        this.valuesByKey = Map.copyOf(normalized);
    }

    public static AgentRuntimeEnvironment empty() {
        return new AgentRuntimeEnvironment(Map.of());
    }

    public String get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return valuesByKey.get(normalizeKey(key));
    }

    public boolean has(String key) {
        return StringUtils.hasText(get(key));
    }

    public Map<String, String> asMap() {
        return valuesByKey;
    }

    public String resolvePlaceholders(String template) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
