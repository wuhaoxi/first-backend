package com.first.app.service;

import com.first.app.dto.*;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.exception.*;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User activeUser;
    private User lockedUser;
    private User deletedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("SecureP@ss1")
                .build();

        loginRequest = LoginRequest.builder()
                .email("alice@example.com")
                .password("SecureP@ss1")
                .build();

        activeUser = User.builder()
                .id(1L).name("Alice").email("alice@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        lockedUser = User.builder()
                .id(2L).name("Bob").email("bob@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.LOCKED)
                .failedLoginAttempts(5)
                .lockedAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        deletedUser = User.builder()
                .id(3L).name("Charlie").email("charlie@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.DELETED)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        unverifiedUser = User.builder()
                .id(4L).name("Diana").email("diana@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.EMAIL_UNVERIFIED)
                .failedLoginAttempts(0)
                .verificationToken(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========== REGISTER ==========

    @Test
    void register_shouldCreateUserWithEmailUnverified() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecureP@ss1")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse result = authService.register(registerRequest);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.EMAIL_UNVERIFIED);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowDuplicateEmailException_whenEmailExists() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("alice@example.com");
        verify(userRepository, never()).save(any());
    }

    // ========== LOGIN ==========

    @Test
    void login_shouldReturnAuthResponse_whenValidCredentialsAndActive() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("SecureP@ss1", "$2a$10$hashed")).thenReturn(true);

        AuthResponse result = authService.login(loginRequest);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(activeUser); // reset failedLoginAttempts
    }

    @Test
    void login_shouldResetFailedAttempts_onSuccess() {
        activeUser.setFailedLoginAttempts(3);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("SecureP@ss1", "$2a$10$hashed")).thenReturn(true);

        authService.login(loginRequest);

        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(0);
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenPasswordWrong() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("SecureP@ss1", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
        assertThat(activeUser.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(activeUser);
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenUserNotFound() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldThrowAccountLocked_whenLocked() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(lockedUser));

        LoginRequest bobLogin = LoginRequest.builder()
                .email("bob@example.com").password("SecureP@ss1").build();

        assertThatThrownBy(() -> authService.login(bobLogin))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_shouldUnlockAndSucceed_whenLockExpired() {
        lockedUser.setLockedAt(LocalDateTime.now().minusMinutes(20)); // expired
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.matches("SecureP@ss1", "$2a$10$hashed")).thenReturn(true);

        LoginRequest bobLogin = LoginRequest.builder()
                .email("bob@example.com").password("SecureP@ss1").build();

        AuthResponse result = authService.login(bobLogin);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(lockedUser.getLockedAt()).isNull();
    }

    @Test
    void login_shouldThrowAccountDeleted_whenDeleted() {
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(deletedUser));

        LoginRequest charlieLogin = LoginRequest.builder()
                .email("charlie@example.com").password("SecureP@ss1").build();

        assertThatThrownBy(() -> authService.login(charlieLogin))
                .isInstanceOf(AccountDeletedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldThrowEmailNotVerified_whenUnverified() {
        when(userRepository.findByEmail("diana@example.com")).thenReturn(Optional.of(unverifiedUser));

        LoginRequest dianaLogin = LoginRequest.builder()
                .email("diana@example.com").password("SecureP@ss1").build();

        assertThatThrownBy(() -> authService.login(dianaLogin))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_shouldLockAccount_afterFiveFailedAttempts() {
        User user = User.builder()
                .id(5L).name("Eve").email("eve@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(4)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("eve@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginRequest eveLogin = LoginRequest.builder()
                .email("eve@example.com").password("wrong").build();

        assertThatThrownBy(() -> authService.login(eveLogin))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedAt()).isNotNull();
    }

    // ========== REFRESH ==========

    @Test
    void refresh_shouldReturnNewAccessToken_whenValidRefreshToken() {
        when(jwtService.validateToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractUserId("valid-refresh")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(jwtService.generateAccessToken(activeUser)).thenReturn("new-access-token");

        String result = authService.refresh("valid-refresh");

        assertThat(result).isEqualTo("new-access-token");
    }

    @Test
    void refresh_shouldThrowInvalidCredentials_whenTokenInvalid() {
        when(jwtService.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refresh_shouldThrowAccountLocked_whenUserLocked() {
        when(jwtService.validateToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractUserId("valid-refresh")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(AccountLockedException.class);
    }

    // ========== VERIFY EMAIL ==========

    @Test
    void verifyEmail_shouldTransitionToActive_whenValidToken() {
        when(userRepository.findByVerificationToken("valid-token")).thenReturn(Optional.of(unverifiedUser));

        authService.verifyEmail("valid-token");

        assertThat(unverifiedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(unverifiedUser.getVerificationToken()).isNull();
        verify(userRepository).save(unverifiedUser);
    }

    @Test
    void verifyEmail_shouldThrowInvalidRequest_whenTokenNotFound() {
        when(userRepository.findByVerificationToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid or expired verification token");
    }

    @Test
    void verifyEmail_shouldReturnSuccess_whenAlreadyVerified() {
        when(userRepository.findByVerificationToken("valid-token")).thenReturn(Optional.of(activeUser));

        // Should not throw, just return
        authService.verifyEmail("valid-token");
        verify(userRepository, never()).save(any());
    }

    // ========== CHANGE PASSWORD ==========

    @Test
    void changePassword_shouldUpdatePasswordHash_whenCurrentPasswordCorrect() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("OldP@ss1", "$2a$10$hashed")).thenReturn(true);
        when(passwordEncoder.encode("NewP@ss2")).thenReturn("$2a$10$newhash");

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldP@ss1")
                .newPassword("NewP@ss2")
                .build();

        authService.changePassword(1L, request);

        assertThat(activeUser.getPasswordHash()).isEqualTo("$2a$10$newhash");
        verify(userRepository).save(activeUser);
    }

    @Test
    void changePassword_shouldThrowInvalidCredentials_whenCurrentPasswordWrong() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongP@ss", "$2a$10$hashed")).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("WrongP@ss")
                .newPassword("NewP@ss2")
                .build();

        assertThatThrownBy(() -> authService.changePassword(1L, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Current password is incorrect");
        verify(userRepository, never()).save(any());
    }

    // ========== GET CURRENT USER ==========

    @Test
    void getCurrentUser_shouldReturnAuthResponse_whenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        AuthResponse result = authService.getCurrentUser(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void getCurrentUser_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
