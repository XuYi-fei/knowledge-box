package com.knowledgebox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ApiErrorResponse;
import com.knowledgebox.web.error.ApiErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ApiErrorResponseFactory apiErrorResponseFactory;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper, ApiErrorResponseFactory apiErrorResponseFactory) {
        this.objectMapper = objectMapper;
        this.apiErrorResponseFactory = apiErrorResponseFactory;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        ApiErrorResponse apiErrorResponse = apiErrorResponseFactory.build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "当前账号没有访问该资源的权限",
                request
        );
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiErrorResponse);
    }
}
