package com.knowledgebox.service.apptool;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.apptool.AppToolDefinition;
import com.knowledgebox.domain.apptool.AppToolRateLimitScope;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppToolRateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final KnowledgeBoxProperties properties;

    public AppToolRateLimitService(StringRedisTemplate stringRedisTemplate, KnowledgeBoxProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void checkAllowed(AppToolDefinition definition, Long userId, String clientIp) {
        AppToolRateLimitScope scope = definition.getRateLimitScope();
        Integer maxRequests = definition.getRateLimitMaxRequests();
        Integer windowSeconds = definition.getRateLimitWindowSeconds();
        if (scope == null || scope == AppToolRateLimitScope.NONE || maxRequests == null || maxRequests < 1 || windowSeconds == null || windowSeconds < 1) {
            return;
        }
        try {
            if (scope == AppToolRateLimitScope.USER || scope == AppToolRateLimitScope.USER_AND_IP) {
                if (userId == null) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录后再继续操作");
                }
                assertKeyWithinLimit(buildKey(definition.getCode(), "user", String.valueOf(userId)), maxRequests, windowSeconds);
            }
            if (scope == AppToolRateLimitScope.USER_AND_IP && StringUtils.hasText(clientIp)) {
                assertKeyWithinLimit(buildKey(definition.getCode(), "ip", clientIp.trim()), maxRequests, windowSeconds);
            }
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "工具限流服务当前不可用，请确认 Redis 已启动");
        }
    }

    private void assertKeyWithinLimit(String key, int maxRequests, int windowSeconds) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current == null) {
            return;
        }
        if (current == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (current > maxRequests) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "APP_TOOL_RATE_LIMITED", "工具调用过于频繁，请稍后再试");
        }
    }

    private String buildKey(String toolCode, String dimension, String identifier) {
        String prefix = properties.getRedis().getKeys().getRateLimit().getAppToolExecute();
        String normalizedPrefix = StringUtils.hasText(prefix) ? prefix.trim() : "knowledge-box:rate-limit:app-tool-execute";
        if (normalizedPrefix.endsWith(":")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return normalizedPrefix + ":" + toolCode + ":" + dimension + ":" + identifier;
    }
}
