package com.first.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StateCheckFilterTest {

    private StateCheckFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new StateCheckFilter(new ObjectMapper().registerModule(new JavaTimeModule()));
        chain = mock(FilterChain.class);
    }

    private User user(UserStatus status) {
        return User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .status(status)
                .build();
    }

    @Test
    void shouldPass_whenMeRequestHasActiveUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setAttribute("currentUser", user(UserStatus.ACTIVE));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn423_whenMeRequestHasLockedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setAttribute("currentUser", user(UserStatus.LOCKED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(423);
    }

    @Test
    void shouldReturn401_whenMeRequestHasDeletedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setAttribute("currentUser", user(UserStatus.DELETED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldSkip_whenLoginRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldSkip_whenVerifyEmailRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/verify-email");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
