package com.first.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.ErrorResponse;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StateCheckFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/verify-email",
            "/api/auth/logout"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Object userAttr = request.getAttribute("currentUser");
        if (userAttr instanceof User user) {
            UserStatus status = user.getStatus();
            switch (status) {
                case ACTIVE -> filterChain.doFilter(request, response);
                case LOCKED -> writeError(response, 423, "Account is locked. Please try again later.");
                case DELETED -> writeError(response, 401, "Invalid email or password");
                case EMAIL_UNVERIFIED ->
                        writeError(response, 403, "Email not verified. Please verify your email before logging in.");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.builder().message(message).status(status).build());
    }
}
