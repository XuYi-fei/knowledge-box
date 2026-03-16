package com.knowledgebox.service.apptool;

import com.fasterxml.jackson.databind.JsonNode;

public interface AppToolExecutor {

    String handlerCode();

    JsonNode execute(JsonNode input, JsonNode serverConfig);

    default String resultType() {
        return "text";
    }

    default String preview(JsonNode result) {
        return result == null || result.isNull() ? "" : result.toString();
    }
}
