package com.knowledgebox.service.auth;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.config.KnowledgeBoxProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VerificationMailService {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

    private final JavaMailSender javaMailSender;
    private final KnowledgeBoxProperties properties;
    private final org.springframework.core.env.Environment environment;

    public VerificationMailService(
            org.springframework.beans.factory.ObjectProvider<JavaMailSender> javaMailSenderProvider,
            KnowledgeBoxProperties properties,
            org.springframework.core.env.Environment environment
    ) {
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        this.properties = properties;
        this.environment = environment;
    }

    public void sendVerificationCode(String email, String code, Duration ttl) {
        if (javaMailSender == null || !StringUtils.hasText(environment.getProperty("spring.mail.host"))) {
            log.warn("SMTP not configured. Login code for {} is {} (valid for {} minutes)", email, code, ttl.toMinutes());
            return;
        }

        String mailFromAddress = resolveFromAddress();
        String mailFromPersonal = properties.getMail().getFromPersonal();
        String mailHost = environment.getProperty("spring.mail.host");
        String mailUsername = environment.getProperty("spring.mail.username");

        if (!StringUtils.hasText(mailFromAddress)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_CONFIG_INVALID",
                    "验证码发送失败，缺少发件人邮箱配置 knowledge-box.mail.from-address"
            );
        }

        InternetAddress fromAddress = parseFromAddress(mailFromAddress, mailFromPersonal);

        try {
            var mimeMessage = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Knowledge Box 登录验证码");
            helper.setText("""
                    你好，

                    你的 Knowledge Box 登录验证码为：%s
                    验证码有效期：%d 分钟

                    如果这不是你的操作，请忽略此邮件。
                    """.formatted(code, ttl.toMinutes()));
            javaMailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            log.error(
                    "Failed to build verification mail message. host={}, username={}, fromAddress={}, fromPersonal={}, target={}, reason={}",
                    mailHost,
                    mailUsername,
                    mailFromAddress,
                    mailFromPersonal,
                    email,
                    exception.getMessage(),
                    exception
            );
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_CONFIG_INVALID",
                    "验证码发送失败，邮件消息构建失败，请检查发件人和收件人配置"
            );
        } catch (MailAuthenticationException exception) {
            log.error(
                    "Failed to send verification mail due to SMTP authentication error. host={}, username={}, fromAddress={}, fromPersonal={}, target={}, reason={}",
                    mailHost,
                    mailUsername,
                    mailFromAddress,
                    mailFromPersonal,
                    email,
                    exception.getMessage(),
                    exception
            );
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_AUTH_FAILED",
                    "验证码发送失败，SMTP 认证失败。若使用 QQ 邮箱，请确认已开启 SMTP 并填写授权码，而不是邮箱登录密码"
            );
        } catch (MailException exception) {
            log.error(
                    "Failed to send verification mail. host={}, username={}, fromAddress={}, fromPersonal={}, target={}, reason={}",
                    mailHost,
                    mailUsername,
                    mailFromAddress,
                    mailFromPersonal,
                    email,
                    exception.getMessage(),
                    exception
            );
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_DELIVERY_FAILED",
                    "验证码发送失败，请检查 SMTP 主机、端口、发件人和授权码配置；详细原因见后端日志"
            );
        }
    }

    private String resolveFromAddress() {
        String configured = properties.getMail().getFromAddress();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return environment.getProperty("spring.mail.username");
    }

    private InternetAddress parseFromAddress(String fromAddress, String fromPersonal) {
        try {
            InternetAddress address = new InternetAddress(fromAddress);
            address.validate();
            if (StringUtils.hasText(fromPersonal)) {
                address.setPersonal(fromPersonal, StandardCharsets.UTF_8.name());
            }
            return address;
        } catch (AddressException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_CONFIG_INVALID",
                    "验证码发送失败，knowledge-box.mail.from-address 不是合法邮箱地址"
            );
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_CONFIG_INVALID",
                    "验证码发送失败，发件人昵称或邮箱配置无效"
            );
        }
    }
}
