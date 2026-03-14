package com.knowledgebox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ApiErrorResponse;
import com.knowledgebox.web.error.ApiErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final ApiErrorResponseFactory apiErrorResponseFactory;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper, ApiErrorResponseFactory apiErrorResponseFactory) {
        this.objectMapper = objectMapper;
        this.apiErrorResponseFactory = apiErrorResponseFactory;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ApiErrorResponse apiErrorResponse = apiErrorResponseFactory.build(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "管理员认证失败，请提供正确的账号密码",
                request
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiErrorResponse);
    }
}
