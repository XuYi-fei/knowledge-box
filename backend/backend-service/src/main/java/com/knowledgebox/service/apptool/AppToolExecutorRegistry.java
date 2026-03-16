package com.knowledgebox.service.apptool;

import com.knowledgebox.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AppToolExecutorRegistry {

    private final Map<String, AppToolExecutor> executors = new LinkedHashMap<>();

    public AppToolExecutorRegistry(List<AppToolExecutor> executorList) {
        for (AppToolExecutor executor : executorList) {
            executors.put(executor.handlerCode(), executor);
        }
    }

    public boolean supports(String handlerCode) {
        return executors.containsKey(handlerCode);
    }

    public AppToolExecutor require(String handlerCode) {
        AppToolExecutor executor = executors.get(handlerCode);
        if (executor == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_HANDLER_NOT_FOUND", "未找到可执行的后端工具处理器: " + handlerCode);
        }
        return executor;
    }
}
