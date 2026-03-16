package com.knowledgebox.service.apptool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgebox.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppToolSchemaSupport {

    private static final Pattern CODE_LIKE_PATTERN = Pattern.compile("^[a-z0-9]+(?:[._-][a-z0-9]+)*$");

    private final ObjectMapper objectMapper;

    public AppToolSchemaSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeJsonObject(String rawText, String fieldLabel, String fallback) {
        String resolved = StringUtils.hasText(rawText) ? rawText.trim() : fallback;
        try {
            JsonNode parsed = objectMapper.readTree(resolved);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException(fieldLabel + " 必须是 JSON 对象");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldLabel + " 必须是有效 JSON 对象", exception);
        }
    }

    public List<String> normalizeTags(List<String> tags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (!StringUtils.hasText(tag)) {
                    continue;
                }
                normalized.add(tag.trim());
            }
        }
        return List.copyOf(normalized);
    }

    public String tagsToJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(normalizeTags(tags));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize app tool tags", exception);
        }
    }

    public List<String> tagsFromJson(String tagsJson) {
        try {
            JsonNode parsed = objectMapper.readTree(StringUtils.hasText(tagsJson) ? tagsJson : "[]");
            if (!(parsed instanceof ArrayNode arrayNode)) {
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                if (node.isTextual() && StringUtils.hasText(node.asText())) {
                    items.add(node.asText().trim());
                }
            }
            return List.copyOf(new LinkedHashSet<>(items));
        } catch (Exception exception) {
            return List.of();
        }
    }

    public JsonNode parseObject(String json, String fieldLabel) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException(fieldLabel + " 必须是 JSON 对象");
            }
            return parsed;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldLabel + " 必须是有效 JSON 对象", exception);
        }
    }

    public void validateInputSchema(String inputSchemaJson) {
        JsonNode schema = parseObject(inputSchemaJson, "输入 Schema");
        JsonNode fieldsNode = schema.path("fields");
        if (!fieldsNode.isArray() || fieldsNode.isEmpty()) {
            throw new IllegalArgumentException("输入 Schema 必须包含至少一个 fields 字段定义");
        }
        for (JsonNode fieldNode : fieldsNode) {
            String name = fieldNode.path("name").asText("").trim();
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("输入 Schema 中的字段必须提供非空 name");
            }
        }
    }

    public void validateInputPayload(String inputSchemaJson, JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_INVALID_INPUT", "工具输入必须是 JSON 对象");
        }
        JsonNode schema = parseObject(inputSchemaJson, "输入 Schema");
        for (JsonNode fieldNode : schema.path("fields")) {
            String fieldName = fieldNode.path("name").asText("").trim();
            if (!StringUtils.hasText(fieldName)) {
                continue;
            }
            JsonNode value = input.get(fieldName);
            boolean required = fieldNode.path("required").asBoolean(false);
            if (required && (value == null || value.isNull() || value.asText("").trim().isEmpty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_INVALID_INPUT", "字段 " + fieldName + " 不能为空");
            }
            if (value != null && !value.isNull() && fieldNode.hasNonNull("maxLength") && value.isTextual()) {
                int maxLength = Math.max(1, fieldNode.path("maxLength").asInt());
                if (value.asText().length() > maxLength) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_INVALID_INPUT", "字段 " + fieldName + " 超过长度限制");
                }
            }
        }
    }

    public void validatePayloadSize(JsonNode input, Integer limitBytes) {
        if (input == null || limitBytes == null || limitBytes <= 0) {
            return;
        }
        int size = input.toString().getBytes(StandardCharsets.UTF_8).length;
        if (size > limitBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_PAYLOAD_TOO_LARGE", "工具输入超过大小限制");
        }
    }

    public String summarizeJson(JsonNode payload, int maxChars) {
        if (payload == null || payload.isNull()) {
            return "{}";
        }
        try {
            String serialized = objectMapper.writeValueAsString(payload);
            if (serialized.length() <= maxChars) {
                return serialized;
            }
            return serialized.substring(0, maxChars) + "...";
        } catch (Exception exception) {
            return payload.toString();
        }
    }

    public String normalizeCode(String code) {
        return normalizeCodeLike(code, "编码", 64);
    }

    public String normalizeCodeLike(String value, String fieldLabel, int maxLength) {
        String normalized = normalizeText(value, fieldLabel, maxLength).toLowerCase(Locale.ROOT);
        if (!CODE_LIKE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldLabel + " 仅支持小写字母、数字，以及 . _ - 连接符");
        }
        return normalized;
    }

    public String normalizeText(String value, String fieldLabel, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldLabel + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " 超出长度限制");
        }
        return normalized;
    }

    public String safeJsonPreview(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return node.toString();
        }
    }
}
