package com.first.app.service;

import com.first.app.dto.*;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.exception.*;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public AuthResponse register(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new DuplicateEmailException("Email already exists: " + request.getEmail());
        });

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.EMAIL_UNVERIFIED)
                .failedLoginAttempts(0)
                .verificationToken(UUID.randomUUID().toString())
                .build();

        User saved = userRepository.save(user);
        return AuthResponse.from(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new AccountDeletedException("Invalid email or password");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedAt() != null &&
                    user.getLockedAt().plusMinutes(LOCK_DURATION_MINUTES).isBefore(LocalDateTime.now())) {
                // Lock expired — unlock and proceed
                user.setStatus(UserStatus.ACTIVE);
                user.setFailedLoginAttempts(0);
                user.setLockedAt(null);
            } else {
                throw new AccountLockedException("Account is locked. Please try again later.");
            }
        }

        if (user.getStatus() == UserStatus.EMAIL_UNVERIFIED) {
            throw new EmailNotVerifiedException(
                    "Email not verified. Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedAt(LocalDateTime.now());
            }
            userRepository.save(user);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Successful login — reset failed attempts
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        return AuthResponse.from(user);
    }

    public String refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        Long userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException("Account is locked. Please try again later.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        if (user.getStatus() == UserStatus.EMAIL_UNVERIFIED) {
            throw new EmailNotVerifiedException(
                    "Email not verified. Please verify your email before logging in.");
        }

        return jwtService.generateAccessToken(user);
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired verification token"));

        if (user.getStatus() == UserStatus.EMAIL_UNVERIFIED) {
            user.setStatus(UserStatus.ACTIVE);
            user.setVerificationToken(null);
            userRepository.save(user);
        }
        // If already verified, silently succeed
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public AuthResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return AuthResponse.from(user);
    }
}
