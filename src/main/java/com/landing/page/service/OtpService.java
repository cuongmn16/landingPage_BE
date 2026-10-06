package com.landing.page.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "OTP:";
    private static final String ATTEMPTS_PREFIX = "OTP_ATTEMPTS:";
    private static final String VERIFIED_PREFIX = "OTP_VERIFIED:";
    private static final String COOLDOWN_PREFIX = "OTP_COOLDOWN:";
    private static final String COUNT_PREFIX = "OTP_COUNT:";

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // In-memory fallback if Redis connection fails
    private final Map<String, OtpData> fallbackOtpCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> fallbackVerified = new ConcurrentHashMap<>();
    private final Map<String, RateLimitTracker> fallbackRateLimitMap = new ConcurrentHashMap<>();

    private static class OtpData {
        final String code;
        final LocalDateTime expiryTime;
        int attempts = 0;

        OtpData(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }

    private static class RateLimitTracker {
        LocalDateTime lastSentTime;
        int hourlyCount = 0;
        LocalDateTime windowStartTime = LocalDateTime.now();

        synchronized void checkAndRecord() {
            LocalDateTime now = LocalDateTime.now();
            if (Duration.between(windowStartTime, now).toMinutes() >= 60) {
                windowStartTime = now;
                hourlyCount = 0;
            }
            if (lastSentTime != null) {
                long elapsedSeconds = Duration.between(lastSentTime, now).getSeconds();
                if (elapsedSeconds < 60) {
                    long waitSeconds = 60 - elapsedSeconds;
                    throw new IllegalArgumentException(
                            String.format("Vui lòng đợi %d giây trước khi yêu cầu gửi lại mã OTP tiếp theo.", waitSeconds)
                    );
                }
            }
            if (hourlyCount >= 5) {
                throw new IllegalArgumentException("Bạn đã vượt quá giới hạn 5 lần yêu cầu OTP trong 1 giờ. Vui lòng thử lại sau.");
            }
            lastSentTime = now;
            hourlyCount++;
        }
    }

    private static String clean(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /**
     * Generate 6-digit OTP code, enforce Rate Limiting (60s cooldown & 5 req/hour),
     * store in Redis for 5 minutes (with in-memory fallback), and send via Email.
     */
    public void generateAndSendOtp(String email) {
        String cleanEmail = clean(email);
        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));

        try {
            // 1. Redis Rate Limiting - Cooldown (60s)
            String cooldownKey = COOLDOWN_PREFIX + cleanEmail;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
                Long expireSec = redisTemplate.getExpire(cooldownKey);
                long waitSeconds = (expireSec != null && expireSec > 0) ? expireSec : 60;
                throw new IllegalArgumentException(
                        String.format("Vui lòng đợi %d giây trước khi yêu cầu gửi lại mã OTP tiếp theo.", waitSeconds)
                );
            }

            // 2. Redis Rate Limiting - Hourly Count (Max 5 req/hr)
            String countKey = COUNT_PREFIX + cleanEmail;
            String currentCountStr = redisTemplate.opsForValue().get(countKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            if (currentCount >= 5) {
                throw new IllegalArgumentException("Bạn đã vượt quá giới hạn 5 lần yêu cầu OTP trong 1 giờ. Vui lòng thử lại sau.");
            }

            // 3. Save OTP (5 mins), reset attempts, cooldown (60s) & increment count
            redisTemplate.opsForValue().set(OTP_PREFIX + cleanEmail, otpCode, OTP_TTL);
            redisTemplate.delete(ATTEMPTS_PREFIX + cleanEmail);
            redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));

            if (currentCount == 0) {
                redisTemplate.opsForValue().set(countKey, "1", Duration.ofHours(1));
            } else {
                redisTemplate.opsForValue().increment(countKey);
            }
            log.info("🔒 [Redis] OTP stored for email: {} (TTL 5m)", cleanEmail);
        } catch (IllegalArgumentException e) {
            throw e; // Pass rate-limiting validation errors through directly
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable ({}), using fallback in-memory cache", e.getMessage());
            fallbackRateLimitMap.computeIfAbsent(cleanEmail, k -> new RateLimitTracker()).checkAndRecord();
            fallbackOtpCache.put(cleanEmail, new OtpData(otpCode, LocalDateTime.now().plus(OTP_TTL)));
        }

        emailService.sendOtpEmail(cleanEmail, otpCode);
    }

    /**
     * Verify 6-digit OTP code. On success the email is marked as verified for 30 minutes
     * so that registration can check it server-side.
     */
    public boolean verifyOtp(String email, String inputCode) {
        String cleanEmail = clean(email);
        String trimmedInput = inputCode == null ? "" : inputCode.trim();
        String otpKey = OTP_PREFIX + cleanEmail;
        String attemptsKey = ATTEMPTS_PREFIX + cleanEmail;

        String cachedCode;
        try {
            cachedCode = redisTemplate.opsForValue().get(otpKey);
        } catch (Exception e) {
            log.warn("⚠️ Redis read error ({}), attempting verification via fallback cache", e.getMessage());
            return verifyFallback(cleanEmail, trimmedInput);
        }

        if (cachedCode == null) {
            if (fallbackOtpCache.containsKey(cleanEmail)) {
                return verifyFallback(cleanEmail, trimmedInput);
            }
            throw new IllegalArgumentException("Mã OTP không tồn tại hoặc đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
        }

        if (!cachedCode.equals(trimmedInput)) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, OTP_TTL);
            if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                redisTemplate.delete(otpKey);
                redisTemplate.delete(attemptsKey);
                throw new IllegalArgumentException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu mã mới.");
            }
            throw new IllegalArgumentException("Mã OTP không chính xác. Vui lòng kiểm tra lại.");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        markVerified(cleanEmail);
        log.info("✅ OTP verified successfully for email {}", cleanEmail);
        return true;
    }

    private boolean verifyFallback(String cleanEmail, String trimmedInput) {
        OtpData data = fallbackOtpCache.get(cleanEmail);
        if (data == null) {
            throw new IllegalArgumentException("Mã OTP không tồn tại hoặc đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
        }
        if (LocalDateTime.now().isAfter(data.expiryTime)) {
            fallbackOtpCache.remove(cleanEmail);
            throw new IllegalArgumentException("Mã OTP đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
        }
        synchronized (data) {
            if (!data.code.equals(trimmedInput)) {
                data.attempts++;
                if (data.attempts >= MAX_VERIFY_ATTEMPTS) {
                    fallbackOtpCache.remove(cleanEmail);
                    throw new IllegalArgumentException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu mã mới.");
                }
                throw new IllegalArgumentException("Mã OTP không chính xác. Vui lòng kiểm tra lại.");
            }
        }
        fallbackOtpCache.remove(cleanEmail);
        markVerified(cleanEmail);
        return true;
    }

    private void markVerified(String cleanEmail) {
        try {
            redisTemplate.opsForValue().set(VERIFIED_PREFIX + cleanEmail, "1", VERIFIED_TTL);
        } catch (Exception e) {
            fallbackVerified.put(cleanEmail, LocalDateTime.now().plus(VERIFIED_TTL));
        }
    }

    /**
     * Consume the "email verified by OTP" flag. Returns true only once per successful verification.
     */
    public boolean consumeVerifiedEmail(String email) {
        String cleanEmail = clean(email);
        try {
            Boolean deleted = redisTemplate.delete(VERIFIED_PREFIX + cleanEmail);
            if (Boolean.TRUE.equals(deleted)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable while checking verified email: {}", e.getMessage());
        }
        LocalDateTime expiry = fallbackVerified.remove(cleanEmail);
        return expiry != null && LocalDateTime.now().isBefore(expiry);
    }
}
