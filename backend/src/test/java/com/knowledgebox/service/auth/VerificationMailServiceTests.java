package com.knowledgebox.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSender;

class VerificationMailServiceTests {

    private JavaMailSender javaMailSender;
    private MockEnvironment environment;
    private KnowledgeBoxProperties properties;
    private VerificationMailService verificationMailService;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        environment = new MockEnvironment()
                .withProperty("spring.mail.host", "smtp.qq.com")
                .withProperty("spring.mail.username", "857998989@qq.com");
        properties = new KnowledgeBoxProperties();
        verificationMailService = new VerificationMailService(provider(javaMailSender), properties, environment);
    }

    @Test
    void shouldUseConfiguredFromAddressAndPersonal() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        properties.getMail().setFromAddress("857998989@qq.com");
        properties.getMail().setFromPersonal("小灰飞");

        verificationMailService.sendVerificationCode("user@example.com", "123456", Duration.ofMinutes(10));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        InternetAddress from = (InternetAddress) captor.getValue().getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("857998989@qq.com");
        assertThat(from.getPersonal()).isEqualTo("小灰飞");
    }

    @Test
    void shouldFallbackToSpringMailUsernameWhenFromAddressMissing() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        properties.getMail().setFromPersonal("Knowledge Box");

        verificationMailService.sendVerificationCode("user@example.com", "123456", Duration.ofMinutes(10));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        InternetAddress from = (InternetAddress) captor.getValue().getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("857998989@qq.com");
        assertThat(from.getPersonal()).isEqualTo("Knowledge Box");
    }

    @Test
    void shouldRejectInvalidFromAddress() {
        properties.getMail().setFromAddress("小灰飞");

        assertThatThrownBy(() -> verificationMailService.sendVerificationCode("user@example.com", "123456", Duration.ofMinutes(10)))
                .isInstanceOf(ApiException.class)
                .hasMessage("验证码发送失败，knowledge-box.mail.from-address 不是合法邮箱地址");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
