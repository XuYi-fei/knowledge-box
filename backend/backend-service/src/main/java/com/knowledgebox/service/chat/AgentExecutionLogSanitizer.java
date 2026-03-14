package com.knowledgebox.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class AgentExecutionLogSanitizer {

    private static final String MASK = "***";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([A-Z0-9._%+-]{1,64})@([A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Z0-9._\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASIC_PATTERN = Pattern.compile("(?i)basic\\s+[A-Z0-9+/=]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|token|secret|password|authorization|cookie|jwt)");

    private final ObjectMapper objectMapper;

    AgentExecutionLogSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String sanitizeToJson(Object value) {
        try {
            JsonNode node = value == null ? JsonNodeFactory.instance.nullNode() : objectMapper.valueToTree(value);
            return objectMapper.writeValueAsString(sanitizeNode(node, null));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sanitize execution trace payload", exception);
        }
    }

    String sanitizeText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = EMAIL_PATTERN.matcher(value).replaceAll("***@$2");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("Bearer ***");
        masked = BASIC_PATTERN.matcher(masked).replaceAll("Basic ***");
        return masked;
    }

    private JsonNode sanitizeNode(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                sanitized.set(key, isSensitiveKey(key) ? JsonNodeFactory.instance.textNode(MASK) : sanitizeNode(entry.getValue(), key));
            }
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                arrayNode.add(sanitizeNode(child, fieldName));
            }
            return arrayNode;
        }
        if (node.isTextual()) {
            if (isSensitiveKey(fieldName)) {
                return JsonNodeFactory.instance.textNode(MASK);
            }
            return JsonNodeFactory.instance.textNode(sanitizeText(node.asText()));
        }
        return node;
    }

    private boolean isSensitiveKey(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return API_KEY_PATTERN.matcher(fieldName.toLowerCase(Locale.ROOT)).find();
    }
}
