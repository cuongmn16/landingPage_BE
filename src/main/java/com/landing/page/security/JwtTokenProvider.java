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
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";
    public static final String TYPE_FILE = "FILE";

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_OBJECT_KEY = "objectKey";
    private static final String REFRESH_PREFIX = "REFRESH_TOKEN:";
    private static final long FILE_TOKEN_EXPIRATION_MS = Duration.ofHours(2).toMillis();

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final StringRedisTemplate redisTemplate;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:86400000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs,
            StringRedisTemplate redisTemplate
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET phải được cấu hình và dài tối thiểu 32 ký tự.");
        }
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
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
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
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();

        try {
            String redisKey = REFRESH_PREFIX + employee.getEmail().trim().toLowerCase();
            redisTemplate.opsForValue().set(redisKey, refreshToken, Duration.ofMillis(refreshTokenExpirationMs));
        } catch (Exception e) {
            log.warn("⚠️ [JwtTokenProvider] Failed to store refresh token in Redis: {}", e.getMessage());
        }

        return refreshToken;
    }

    /**
     * Short-lived token that authorizes downloading one specific MinIO object.
     * It is only embedded in responses sent to users allowed to see that submission.
     */
    public String generateFileToken(String objectKey) {
        Date now = new Date();
        return Jwts.builder()
                .subject(objectKey)
                .claim(CLAIM_TOKEN_TYPE, TYPE_FILE)
                .claim(CLAIM_OBJECT_KEY, objectKey)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + FILE_TOKEN_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public boolean validateFileToken(String token, String objectKey) {
        return parseClaims(token)
                .filter(c -> TYPE_FILE.equals(c.get(CLAIM_TOKEN_TYPE, String.class)))
                .map(c -> objectKey != null && objectKey.equals(c.get(CLAIM_OBJECT_KEY, String.class)))
                .orElse(false);
    }

    /**
     * Parse & verify signature/expiration. Empty when the token is invalid.
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Claims of a valid ACCESS token, empty otherwise (refresh/file tokens are rejected).
     */
    public Optional<Claims> parseAccessToken(String token) {
        return parseClaims(token)
                .filter(c -> TYPE_ACCESS.equals(c.get(CLAIM_TOKEN_TYPE, String.class)));
    }

    /**
     * Returns the email of a valid, non-revoked refresh token.
     */
    public Optional<String> validateRefreshToken(String refreshToken) {
        Optional<Claims> claims = parseClaims(refreshToken)
                .filter(c -> TYPE_REFRESH.equals(c.get(CLAIM_TOKEN_TYPE, String.class)));
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        String email = claims.get().getSubject();
        try {
            String storedToken = redisTemplate.opsForValue().get(REFRESH_PREFIX + email.trim().toLowerCase());
            return refreshToken.equals(storedToken) ? Optional.of(email) : Optional.empty();
        } catch (Exception e) {
            // Fail closed: without Redis we cannot know whether the token was revoked
            log.warn("⚠️ [JwtTokenProvider] Redis read failed during refresh token validation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Revoke Refresh Token from Redis (Logout)
     */
    public void revokeRefreshToken(String email) {
        try {
            redisTemplate.delete(REFRESH_PREFIX + email.trim().toLowerCase());
        } catch (Exception e) {
            log.warn("⚠️ [JwtTokenProvider] Could not delete refresh token from Redis: {}", e.getMessage());
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
