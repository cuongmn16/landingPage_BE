package com.landing.page.security;

import com.landing.page.entity.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_PREFIX = "REFRESH_TOKEN:";

    public JwtTokenProvider(
            @Value("${jwt.secret:ctin25yearstreasurehunthelticsecretkey2026supersecurekey1234567890}") String secret,
            @Value("${jwt.access-token-expiration-ms:86400000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs,
            StringRedisTemplate redisTemplate
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate Access Token (JWT) containing user claims
     */
    public String generateAccessToken(Employee employee) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(employee.getEmail())
                .claim("employeeId", employee.getId())
                .claim("employeeCode", employee.getEmployeeCode())
                .claim("role", employee.getRole().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Generate Refresh Token (JWT) & persist in Redis for instant validation/revocation
     */
    public String generateRefreshToken(Employee employee) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        String refreshToken = Jwts.builder()
                .subject(employee.getEmail())
                .claim("tokenType", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();

        // Store refresh token in Redis with TTL
        try {
            String redisKey = REFRESH_PREFIX + employee.getEmail().trim().toLowerCase();
            redisTemplate.opsForValue().set(redisKey, refreshToken, Duration.ofMillis(refreshTokenExpirationMs));
            log.info("🔑 [JwtTokenProvider] Refresh token stored in Redis for user {}", employee.getEmail());
        } catch (Exception e) {
            log.warn("⚠️ [JwtTokenProvider] Failed to store refresh token in Redis: {}", e.getMessage());
        }

        return refreshToken;
    }

    /**
     * Validate JWT Access or Refresh Token signature & expiration
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("⚠️ [JwtTokenProvider] Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate Refresh Token against Redis store
     */
    public boolean validateRefreshToken(String email, String refreshToken) {
        if (!validateToken(refreshToken)) {
            return false;
        }
        try {
            String redisKey = REFRESH_PREFIX + email.trim().toLowerCase();
            String storedToken = redisTemplate.opsForValue().get(redisKey);
            return storedToken != null && storedToken.equals(refreshToken);
        } catch (Exception e) {
            log.warn("⚠️ [JwtTokenProvider] Redis read failed during refresh token validation: {}", e.getMessage());
            return true; // Fallback to JWT signature validation if Redis is down
        }
    }

    /**
     * Extract Email (subject) from JWT
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Revoke Refresh Token from Redis (Logout)
     */
    public void revokeRefreshToken(String email) {
        try {
            String redisKey = REFRESH_PREFIX + email.trim().toLowerCase();
            redisTemplate.delete(redisKey);
            log.info("🚪 [JwtTokenProvider] Revoked refresh token for user {}", email);
        } catch (Exception e) {
            log.warn("⚠️ [JwtTokenProvider] Could not delete refresh token from Redis: {}", e.getMessage());
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
