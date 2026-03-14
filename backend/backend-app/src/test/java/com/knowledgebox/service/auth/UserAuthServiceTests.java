package com.knowledgebox.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.api.EmailCodeLoginRequest;
import com.knowledgebox.api.EmailPasswordLoginRequest;
import com.knowledgebox.api.EmailRegisterRequest;
import com.knowledgebox.api.UserAuthAction;
import com.knowledgebox.api.UserAuthResponse;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.user.UserAccount;
import com.knowledgebox.repository.UserAccountRepository;
import com.knowledgebox.security.JwtTokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAuthServiceTests {

    private UserAccountRepository userAccountRepository;
    private PasswordEncoder passwordEncoder;
    private EmailVerificationService emailVerificationService;
    private JwtTokenService jwtTokenService;
    private UserAuthService userAuthService;

    @BeforeEach
    void setUp() {
        userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
        passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        emailVerificationService = org.mockito.Mockito.mock(EmailVerificationService.class);
        jwtTokenService = org.mockito.Mockito.mock(JwtTokenService.class);
        userAuthService = new UserAuthService(
                userAccountRepository,
                passwordEncoder,
                emailVerificationService,
                jwtTokenService
        );
        when(jwtTokenService.issue(org.mockito.ArgumentMatchers.any(UserAccount.class)))
                .thenReturn(new JwtTokenService.IssuedToken("token", Instant.parse("2026-03-11T00:00:00Z")));
    }

    @Test
    void shouldAutoRegisterWhenCodeLoginUsesUnregisteredEmail() {
        when(userAccountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("{bcrypt}encoded");
        when(userAccountRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuthResponse response = userAuthService.loginByCode(new EmailCodeLoginRequest("user@example.com", "123456"));

        assertThat(response.authAction()).isEqualTo(UserAuthAction.AUTO_REGISTERED);
        assertThat(response.message()).isEqualTo("当前邮箱未注册，系统已自动注册并登录");
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().isPasswordLoginEnabled()).isFalse();
        verify(emailVerificationService).verify("user@example.com", "123456");
        verify(emailVerificationService).consume("user@example.com");
    }

    @Test
    void shouldRejectPasswordLoginWhenEmailIsNotRegistered() {
        when(userAccountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAuthService.loginByPassword(new EmailPasswordLoginRequest("user@example.com", "password123")))
                .isInstanceOf(ApiException.class)
                .hasMessage("邮箱尚未注册，请前往注册");
    }

    @Test
    void shouldRejectRegisterWhenEmailAlreadyHasPasswordLogin() {
        UserAccount existing = existingUser(true, "{bcrypt}stored");
        when(userAccountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userAuthService.register(new EmailRegisterRequest("user@example.com", "123456", "password123")))
                .isInstanceOf(ApiException.class)
                .hasMessage("当前邮箱已注册，请直接登录");

        verify(emailVerificationService).verify("user@example.com", "123456");
        verify(emailVerificationService, never()).consume("user@example.com");
    }

    @Test
    void shouldEnablePasswordLoginWhenCompletingRegistrationAfterAutoRegister() {
        UserAccount existing = existingUser(false, "{bcrypt}random");
        when(userAccountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}password123");
        when(userAccountRepository.save(existing)).thenReturn(existing);

        UserAuthResponse response = userAuthService.register(new EmailRegisterRequest("user@example.com", "123456", "password123"));

        assertThat(response.authAction()).isEqualTo(UserAuthAction.REGISTERED);
        assertThat(existing.isPasswordLoginEnabled()).isTrue();
        assertThat(existing.getPasswordHash()).isEqualTo("{bcrypt}password123");
        verify(emailVerificationService).consume("user@example.com");
    }

    @Test
    void shouldLoginByPasswordForRegisteredUser() {
        UserAccount existing = existingUser(true, "{bcrypt}stored");
        when(userAccountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);
        when(userAccountRepository.save(existing)).thenReturn(existing);

        UserAuthResponse response = userAuthService.loginByPassword(new EmailPasswordLoginRequest("user@example.com", "password123"));

        assertThat(response.authAction()).isEqualTo(UserAuthAction.LOGGED_IN);
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.message()).isEqualTo("登录成功，欢迎回来");
    }

    private UserAccount existingUser(boolean passwordLoginEnabled, String passwordHash) {
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail("user@example.com");
        userAccount.setEnabled(true);
        userAccount.setPasswordLoginEnabled(passwordLoginEnabled);
        userAccount.setPasswordHash(passwordHash);
        return userAccount;
    }
}
