package com.first.app.security;

import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private JwtAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthFilter(jwtService, userRepository);
        chain = mock(FilterChain.class);
    }

    private User activeUser() {
        return User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldSetUserIdAttribute_whenMeRequestHasValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie("access_token", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser()));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isEqualTo(1L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldSetUserIdAttribute_whenPasswordRequestHasValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");
        request.setCookies(new Cookie("access_token", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser()));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isEqualTo(1L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldSkipTokenProcessing_whenLoginRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isNull();
        verify(jwtService, never()).validateToken(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldSkipTokenProcessing_whenRefreshRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isNull();
        verify(jwtService, never()).validateToken(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotSetUserId_whenMeRequestHasNoToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isNull();
        verify(chain).doFilter(request, response);
    }
}
