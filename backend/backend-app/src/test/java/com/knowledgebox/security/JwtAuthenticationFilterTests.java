package com.knowledgebox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgebox.config.JsonAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtAuthenticationFilterTests {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldParticipateInAsyncDispatches() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenService.class), mock(JsonAuthenticationEntryPoint.class));

        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }

    @Test
    void shouldReAuthenticateOnAsyncRedispatch() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.parse("token")).thenReturn(new CurrentUser(1L, "user@example.com"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, mock(JsonAuthenticationEntryPoint.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        request.addHeader("Authorization", "Bearer token");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(jwtTokenService).parse("token");
    }
}
