package com.first.app.security;

import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;
    private static final String SECRET = "this-is-a-test-secret-key-for-dev-only-change-in-production";
    private static final long ACCESS_EXPIRATION = 900000L;
    private static final long REFRESH_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        user = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void generateAccessToken_shouldSetSubjectAndExpiry() {
        String token = jwtService.generateAccessToken(user);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email")).isEqualTo("alice@example.com");
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void generateRefreshToken_shouldSetSubjectAndExpiry() {
        String token = jwtService.generateRefreshToken(user);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("type")).isEqualTo("refresh");
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        // Create service with 0ms expiration
        JwtService expiredService = new JwtService(SECRET, 0L, REFRESH_EXPIRATION);
        String token = expiredService.generateAccessToken(user);

        // Token should be expired immediately
        assertThat(expiredService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 1) + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');
        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    @Test
    void extractUserId_shouldReturnCorrectId() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractUserId(token)).isEqualTo(1L);
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
    }
}
