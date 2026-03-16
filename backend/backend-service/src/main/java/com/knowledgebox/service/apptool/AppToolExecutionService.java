package com.knowledgebox.service.apptool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgebox.api.AppToolExecutionResultView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.apptool.AppToolDefinition;
import com.knowledgebox.domain.apptool.AppToolExecutionLog;
import com.knowledgebox.domain.apptool.AppToolExecutionMode;
import com.knowledgebox.domain.apptool.AppToolExecutionStatus;
import com.knowledgebox.repository.AppToolDefinitionRepository;
import com.knowledgebox.repository.AppToolExecutionLogRepository;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AppToolExecutionService {

    private final AppToolDefinitionRepository definitionRepository;
    private final AppToolExecutionLogRepository executionLogRepository;
    private final AppToolSchemaSupport schemaSupport;
    private final AppToolExecutorRegistry executorRegistry;
    private final AppToolRateLimitService rateLimiterService;
    private final ObjectMapper objectMapper;

    public AppToolExecutionService(
            AppToolDefinitionRepository definitionRepository,
            AppToolExecutionLogRepository executionLogRepository,
            AppToolSchemaSupport schemaSupport,
            AppToolExecutorRegistry executorRegistry,
            AppToolRateLimitService rateLimiterService,
            ObjectMapper objectMapper
    ) {
        this.definitionRepository = definitionRepository;
        this.executionLogRepository = executionLogRepository;
        this.schemaSupport = schemaSupport;
        this.executorRegistry = executorRegistry;
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    public AppToolExecutionResultView execute(Long userId, String toolCode, JsonNode input, String clientIp) {
        AppToolDefinition definition = definitionRepository.findByCode(schemaSupport.normalizeCode(toolCode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APP_TOOL_NOT_FOUND", "用户工具不存在: " + toolCode));
        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "APP_TOOL_NOT_FOUND", "用户工具不存在: " + toolCode);
        }
        if (definition.getExecutionMode() != AppToolExecutionMode.SERVER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_CLIENT_ONLY", "该工具为纯前端工具，无需调用后端执行接口");
        }
        schemaSupport.validateInputPayload(definition.getInputSchemaJson(), input);
        schemaSupport.validatePayloadSize(input, definition.getPayloadLimitBytes());

        String executionId = "app-tool-" + UUID.randomUUID();
        long startedNanos = System.nanoTime();
        String maskedIp = maskClientIp(clientIp);
        try {
            rateLimiterService.checkAllowed(definition, userId, clientIp);
            AppToolExecutor executor = executorRegistry.require(definition.getHandlerCode());
            JsonNode serverConfig = schemaSupport.parseObject(definition.getServerConfigJson(), "服务端配置 JSON");
            JsonNode result = executeWithTimeout(definition, executor, input, serverConfig);
            long durationMs = elapsedMs(startedNanos);
            if (Boolean.TRUE.equals(definition.getAuditEnabled())) {
                executionLogRepository.save(buildLog(
                        executionId,
                        definition.getCode(),
                        userId,
                        AppToolExecutionStatus.SUCCESS,
                        durationMs,
                        buildRequestSummary(input, maskedIp),
                        buildResponseSummary(executor, result),
                        null,
                        null,
                        maskedIp
                ));
            }
            return new AppToolExecutionResultView(
                    definition.getCode(),
                    definition.getExecutionMode().name(),
                    executor.resultType(),
                    result,
                    executor.preview(result),
                    durationMs,
                    executionId
            );
        } catch (ApiException exception) {
            long durationMs = elapsedMs(startedNanos);
            if (Boolean.TRUE.equals(definition.getAuditEnabled())) {
                AppToolExecutionStatus status = "APP_TOOL_RATE_LIMITED".equals(exception.getCode())
                        ? AppToolExecutionStatus.RATE_LIMITED
                        : AppToolExecutionStatus.FAILED;
                executionLogRepository.save(buildLog(
                        executionId,
                        definition.getCode(),
                        userId,
                        status,
                        durationMs,
                        buildRequestSummary(input, maskedIp),
                        null,
                        exception.getCode(),
                        exception.getMessage(),
                        maskedIp
                ));
            }
            throw exception;
        } catch (Exception exception) {
            long durationMs = elapsedMs(startedNanos);
            if (Boolean.TRUE.equals(definition.getAuditEnabled())) {
                executionLogRepository.save(buildLog(
                        executionId,
                        definition.getCode(),
                        userId,
                        AppToolExecutionStatus.FAILED,
                        durationMs,
                        buildRequestSummary(input, maskedIp),
                        null,
                        "APP_TOOL_EXECUTION_FAILED",
                        exception.getMessage(),
                        maskedIp
                ));
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "APP_TOOL_EXECUTION_FAILED", "工具执行失败，请稍后重试");
        }
    }

    private JsonNode executeWithTimeout(AppToolDefinition definition, AppToolExecutor executor, JsonNode input, JsonNode serverConfig) throws Exception {
        Integer timeoutMs = definition.getTimeoutMs();
        if (timeoutMs == null || timeoutMs < 1) {
            return executor.execute(input, serverConfig);
        }
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        Future<JsonNode> future = null;
        try {
            future = executorService.submit(() -> executor.execute(input, serverConfig));
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            if (future != null) {
                future.cancel(true);
            }
            throw new ApiException(HttpStatus.REQUEST_TIMEOUT, "APP_TOOL_TIMEOUT", "工具执行超时，请稍后重试");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "APP_TOOL_INVALID_INPUT", illegalArgumentException.getMessage());
            }
            if (cause instanceof Exception nested) {
                throw nested;
            }
            throw new IllegalStateException("Tool execution failed", cause);
        } finally {
            executorService.shutdownNow();
        }
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private AppToolExecutionLog buildLog(
            String executionId,
            String toolCode,
            Long userId,
            AppToolExecutionStatus status,
            Long durationMs,
            String requestSummaryJson,
            String responseSummaryJson,
            String errorCode,
            String errorMessage,
            String clientIpMasked
    ) {
        AppToolExecutionLog log = new AppToolExecutionLog();
        log.setExecutionId(executionId);
        log.setToolCode(toolCode);
        log.setUserId(userId);
        log.setStatus(status);
        log.setDurationMs(durationMs);
        log.setRequestSummaryJson(requestSummaryJson == null ? "{}" : requestSummaryJson);
        log.setResponseSummaryJson(responseSummaryJson == null ? "{}" : responseSummaryJson);
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 512)));
        log.setClientIpMasked(clientIpMasked);
        return log;
    }

    private String buildRequestSummary(JsonNode input, String maskedIp) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("input", schemaSupport.summarizeJson(input, 1200));
        if (maskedIp != null) {
            summary.put("clientIpMasked", maskedIp);
        }
        return schemaSupport.safeJsonPreview(summary);
    }

    private String buildResponseSummary(AppToolExecutor executor, JsonNode result) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("resultType", executor.resultType());
        summary.put("preview", executor.preview(result));
        summary.put("result", schemaSupport.summarizeJson(result, 1200));
        return schemaSupport.safeJsonPreview(summary);
    }

    private String maskClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        String resolved = clientIp.trim();
        if (resolved.contains(",")) {
            resolved = resolved.substring(0, resolved.indexOf(',')).trim();
        }
        if (resolved.contains(".")) {
            String[] parts = resolved.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
            return "*.*.*.*";
        }
        if (resolved.contains(":")) {
            String[] parts = resolved.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":*:*";
            }
            return "*:*";
        }
        return "***";
    }
}
