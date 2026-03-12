package com.knowledgebox.web.publicapi;

import com.knowledgebox.api.EmailCodeLoginRequest;
import com.knowledgebox.api.EmailPasswordLoginRequest;
import com.knowledgebox.api.EmailRegisterRequest;
import com.knowledgebox.api.SendEmailCodeRequest;
import com.knowledgebox.api.UserAuthResponse;
import com.knowledgebox.api.UserView;
import com.knowledgebox.security.CurrentUserAccessor;
import com.knowledgebox.service.auth.UserAuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PublicAuthController {

    private final UserAuthService userAuthService;
    private final CurrentUserAccessor currentUserAccessor;

    public PublicAuthController(UserAuthService userAuthService, CurrentUserAccessor currentUserAccessor) {
        this.userAuthService = userAuthService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @PostMapping("/public/auth/send-code")
    public Map<String, String> sendCode(@Valid @RequestBody SendEmailCodeRequest request) {
        userAuthService.sendLoginCode(request.email());
        return Map.of("message", "验证码已发送，请检查邮箱；若本地未配置 SMTP，请查看后端日志中的验证码");
    }

    @PostMapping("/public/auth/register")
    public UserAuthResponse register(@Valid @RequestBody EmailRegisterRequest request) {
        return userAuthService.register(request);
    }

    @PostMapping("/public/auth/login/code")
    public UserAuthResponse loginByCode(@Valid @RequestBody EmailCodeLoginRequest request) {
        return userAuthService.loginByCode(request);
    }

    @PostMapping("/public/auth/login/password")
    public UserAuthResponse loginByPassword(@Valid @RequestBody EmailPasswordLoginRequest request) {
        return userAuthService.loginByPassword(request);
    }

    @GetMapping("/app/me")
    public UserView me() {
        return userAuthService.me(currentUserAccessor.requireCurrentUser());
    }
}
