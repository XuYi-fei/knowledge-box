package com.knowledgebox.service.apptool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class Md5DigestAppToolExecutor implements AppToolExecutor {

    @Override
    public String handlerCode() {
        return "md5-digest";
    }

    @Override
    public JsonNode execute(JsonNode input, JsonNode serverConfig) {
        String text = input.path("text").asText("");
        String digest = md5Hex(text.getBytes(StandardCharsets.UTF_8));
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("text", digest);
        result.put("digest", digest);
        result.put("sourceText", text);
        result.put("algorithm", "MD5");
        return result;
    }

    @Override
    public String resultType() {
        return "text";
    }

    @Override
    public String preview(JsonNode result) {
        return result == null ? "" : result.path("digest").asText("");
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate MD5 digest", exception);
        }
    }
}
