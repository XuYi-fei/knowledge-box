package com.knowledgebox.service.auth;

import com.knowledgebox.api.EmailCodeLoginRequest;
import com.knowledgebox.api.EmailPasswordLoginRequest;
import com.knowledgebox.api.EmailRegisterRequest;
import com.knowledgebox.api.UserAuthAction;
import com.knowledgebox.api.UserAuthResponse;
import com.knowledgebox.api.UserView;
import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.user.UserAccount;
import com.knowledgebox.repository.UserAccountRepository;
import com.knowledgebox.security.CurrentUser;
import com.knowledgebox.security.JwtTokenService;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtTokenService jwtTokenService;

    public UserAuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            JwtTokenService jwtTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.jwtTokenService = jwtTokenService;
    }

    public void sendLoginCode(String email) {
        emailVerificationService.sendLoginCode(normalize(email));
    }

    @Transactional
    public UserAuthResponse register(EmailRegisterRequest request) {
        String email = normalize(request.email());
        emailVerificationService.verify(email, request.verificationCode());
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .map(existing -> registerExistingUser(existing, request.password()))
                .orElseGet(() -> registerNewUser(email, request.password(), true));
        emailVerificationService.consume(email);
        return issueToken(userAccount, UserAuthAction.REGISTERED, "注册成功，欢迎使用 Knowledge Box");
    }

    @Transactional
    public UserAuthResponse loginByCode(EmailCodeLoginRequest request) {
        String email = normalize(request.email());
        emailVerificationService.verify(email, request.verificationCode());
        java.util.Optional<UserAccount> existingUser = userAccountRepository.findByEmailIgnoreCase(email);
        boolean autoRegistered = existingUser.isEmpty();
        UserAccount userAccount = existingUser
                .map(this::touchExistingUser)
                .orElseGet(() -> autoRegisterByCode(email));
        emailVerificationService.consume(email);
        UserAuthAction authAction = autoRegistered ? UserAuthAction.AUTO_REGISTERED : UserAuthAction.LOGGED_IN;
        String message = authAction == UserAuthAction.AUTO_REGISTERED
                ? "当前邮箱未注册，系统已自动注册并登录"
                : "登录成功，欢迎回来";
        return issueToken(userAccount, authAction, message);
    }

    @Transactional
    public UserAuthResponse loginByPassword(EmailPasswordLoginRequest request) {
        String email = normalize(request.email());
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EMAIL_NOT_REGISTERED",
                        "邮箱尚未注册，请前往注册"
                ));
        requireEnabled(userAccount);
        if (!userAccount.isPasswordLoginEnabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PASSWORD_LOGIN_NOT_AVAILABLE",
                    "当前邮箱尚未设置密码，请前往注册完成账号设置"
            );
        }
        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确");
        }
        userAccount.setLastLoginAt(OffsetDateTime.now());
        return issueToken(userAccountRepository.save(userAccount), UserAuthAction.LOGGED_IN, "登录成功，欢迎回来");
    }

    @Transactional(readOnly = true)
    public UserView me(CurrentUser currentUser) {
        UserAccount userAccount = userAccountRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "当前登录用户不存在或已失效"));
        return new UserView(userAccount.getId(), userAccount.getEmail());
    }

    private UserAccount registerExistingUser(UserAccount userAccount, String rawPassword) {
        requireEnabled(userAccount);
        if (userAccount.isPasswordLoginEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "当前邮箱已注册，请直接登录");
        }
        userAccount.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAccount.setPasswordLoginEnabled(true);
        userAccount.setLastLoginAt(OffsetDateTime.now());
        return userAccountRepository.save(userAccount);
    }

    private UserAccount touchExistingUser(UserAccount userAccount) {
        requireEnabled(userAccount);
        userAccount.setLastLoginAt(OffsetDateTime.now());
        return userAccountRepository.save(userAccount);
    }

    private UserAccount autoRegisterByCode(String email) {
        return registerNewUser(email, UUID.randomUUID().toString(), false);
    }

    private UserAccount registerNewUser(String email, String rawPassword, boolean passwordLoginEnabled) {
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(email);
        userAccount.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAccount.setPasswordLoginEnabled(passwordLoginEnabled);
        userAccount.setEnabled(true);
        userAccount.setLastLoginAt(OffsetDateTime.now());
        return userAccountRepository.save(userAccount);
    }

    private UserAuthResponse issueToken(UserAccount userAccount, UserAuthAction authAction, String message) {
        JwtTokenService.IssuedToken issuedToken = jwtTokenService.issue(userAccount);
        return new UserAuthResponse(
                issuedToken.token(),
                issuedToken.expiresAt(),
                new UserView(userAccount.getId(), userAccount.getEmail()),
                authAction,
                message
        );
    }

    private void requireEnabled(UserAccount userAccount) {
        if (!userAccount.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "当前账号已被禁用");
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
