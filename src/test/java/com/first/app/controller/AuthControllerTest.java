package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.*;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.exception.*;
import com.first.app.security.JwtService;
import com.first.app.repository.UserRepository;
import com.first.app.service.AuthService;
import com.first.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .id(1L).name("Alice").email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .build();
    }

    // ========== REGISTER ==========

    @Test
    void register_shouldReturn201() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void register_shouldReturn400_whenMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn409_whenDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("Email already exists: alice@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists: alice@example.com"));
    }

    // ========== LOGIN ==========

    @Test
    void login_shouldReturn200WithCookies() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(buildAuthResponse());
        when(userRepository.findByEmail(anyString())).thenReturn(
                java.util.Optional.of(User.builder().id(1L).email("alice@example.com").passwordHash("h").status(UserStatus.ACTIVE).build()));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token-value");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token-value");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().path("refresh_token", "/api/auth/refresh"));
    }

    @Test
    void login_shouldReturn401_whenInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_shouldReturn423_whenAccountLocked() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountLockedException("Account is locked. Please try again later."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.message").value("Account is locked. Please try again later."));
    }

    @Test
    void login_shouldReturn401_whenAccountDeleted() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountDeletedException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"deleted@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldReturn403_whenEmailNotVerified() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new EmailNotVerifiedException("Email not verified."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unverified@example.com","password":"SecureP@ss1"}"""))
                .andExpect(status().isForbidden());
    }

    // ========== REFRESH ==========

    @Test
    void refresh_shouldReturn200WithNewAccessToken() throws Exception {
        when(authService.refresh(anyString())).thenReturn("new-access-token-value");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(jsonPath("$.message").value("Token refreshed"));
    }

    @Test
    void refresh_shouldReturn401_whenInvalidToken() throws Exception {
        when(authService.refresh(anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // ========== VERIFY EMAIL ==========

    @Test
    void verifyEmail_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    void verifyEmail_shouldReturn400_whenInvalidToken() throws Exception {
        doThrow(new InvalidRequestException("Invalid or expired verification token"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad-token\"}"))
                .andExpect(status().isBadRequest());
    }

    // ========== LOGOUT ==========

    @Test
    void logout_shouldReturn200AndClearCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // ========== GET ME ==========

    @Test
    void getMe_shouldReturn200() throws Exception {
        when(authService.getCurrentUser(1L)).thenReturn(buildAuthResponse());

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    // ========== CHANGE PASSWORD ==========

    @Test
    void changePassword_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"OldP@ss1","newPassword":"NewP@ss2"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void changePassword_shouldReturn401_whenWrongCurrentPassword() throws Exception {
        doThrow(new InvalidCredentialsException("Current password is incorrect"))
                .when(authService).changePassword(anyLong(), any());

        mockMvc.perform(put("/api/auth/password")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Wrong","newPassword":"NewP@ss2"}"""))
                .andExpect(status().isUnauthorized());
    }
}
