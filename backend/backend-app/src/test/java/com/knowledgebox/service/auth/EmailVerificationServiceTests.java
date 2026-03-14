package com.knowledgebox.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

class EmailVerificationServiceTests {

    private static final String NORMALIZED_EMAIL = "user@example.com";

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private VerificationMailService verificationMailService;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        verificationMailService = mock(VerificationMailService.class);

        KnowledgeBoxProperties properties = new KnowledgeBoxProperties();
        properties.getAuth().setVerificationCodeTtl(Duration.ofMinutes(10));
        properties.getAuth().setSendCooldown(Duration.ofSeconds(60));
        properties.getMail().setFromAddress("noreply@example.com");
        properties.getRedis().getKeys().getAuth().setVerificationCode("kb:test:auth:verification-code");
        properties.getRedis().getKeys().getAuth().setVerificationCooldown("kb:test:auth:verification-cooldown");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey("kb:test:auth:verification-cooldown:" + NORMALIZED_EMAIL)).thenReturn(false);

        emailVerificationService = new EmailVerificationService(
                stringRedisTemplate,
                verificationMailService,
                properties
        );
    }

    @Test
    void shouldSendMailBeforeWritingRedis() {
        emailVerificationService.sendLoginCode("User@Example.com");

        InOrder inOrder = inOrder(stringRedisTemplate, verificationMailService, valueOperations);
        inOrder.verify(stringRedisTemplate).hasKey("kb:test:auth:verification-cooldown:" + NORMALIZED_EMAIL);
        inOrder.verify(verificationMailService).sendVerificationCode(eq(NORMALIZED_EMAIL), anyString(), eq(Duration.ofMinutes(10)));
        inOrder.verify(valueOperations).set(eq("kb:test:auth:verification-code:" + NORMALIZED_EMAIL), anyString(), eq(Duration.ofMinutes(10)));
        inOrder.verify(valueOperations).set("kb:test:auth:verification-cooldown:" + NORMALIZED_EMAIL, "1", Duration.ofSeconds(60));
    }

    @Test
    void shouldNotWriteRedisWhenMailSendFails() {
        ApiException mailFailure = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_DELIVERY_FAILED", "mail failed");
        org.mockito.Mockito.doThrow(mailFailure)
                .when(verificationMailService)
                .sendVerificationCode(eq(NORMALIZED_EMAIL), anyString(), eq(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> emailVerificationService.sendLoginCode("User@Example.com"))
                .isSameAs(mailFailure);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
