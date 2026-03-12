package com.knowledgebox.service.auth;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import java.security.SecureRandom;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final VerificationMailService verificationMailService;
    private final KnowledgeBoxProperties properties;

    public EmailVerificationService(
            StringRedisTemplate stringRedisTemplate,
            VerificationMailService verificationMailService,
            KnowledgeBoxProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.verificationMailService = verificationMailService;
        this.properties = properties;
    }

    public void sendLoginCode(String email) {
        String normalizedEmail = normalize(email);
        String cooldownKey = cooldownKey(normalizedEmail);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_RATE_LIMITED", "验证码发送过于频繁，请稍后再试");
            }
            String code = generateCode();
            verificationMailService.sendVerificationCode(
                    normalizedEmail,
                    code,
                    properties.getAuth().getVerificationCodeTtl()
            );
            stringRedisTemplate.opsForValue().set(codeKey(normalizedEmail), code, properties.getAuth().getVerificationCodeTtl());
            stringRedisTemplate.opsForValue().set(cooldownKey, "1", properties.getAuth().getSendCooldown());
            log.info("Login verification code sent to {}", normalizedEmail);
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "验证码服务当前不可用，请确认 Redis 已启动");
        }
    }

    public void verify(String email, String submittedCode) {
        String normalizedEmail = normalize(email);
        String storedCode;
        try {
            storedCode = stringRedisTemplate.opsForValue().get(codeKey(normalizedEmail));
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "验证码服务当前不可用，请确认 Redis 已启动");
        }
        if (storedCode == null || !storedCode.equals(submittedCode)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_CODE", "验证码无效或已过期");
        }
    }

    public void consume(String email) {
        try {
            stringRedisTemplate.delete(codeKey(normalize(email)));
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "验证码服务当前不可用，请确认 Redis 已启动");
        }
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private String codeKey(String email) {
        return key(properties.getRedis().getKeys().getAuth().getVerificationCode(), email);
    }

    private String cooldownKey(String email) {
        return key(properties.getRedis().getKeys().getAuth().getVerificationCooldown(), email);
    }

    private String key(String prefix, String email) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "knowledge-box:auth" : prefix;
        if (normalizedPrefix.endsWith(":")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return normalizedPrefix + ":" + email;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
