package com.knowledgebox.web.error;

import com.knowledgebox.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorResponseFactory {

    public ApiErrorResponse build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return build(status, code, message, request, Map.of());
    }

    public ApiErrorResponse build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
    }
}
