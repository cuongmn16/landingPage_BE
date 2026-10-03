package com.landing.page.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "OTP:";
    private static final String COOLDOWN_PREFIX = "OTP_COOLDOWN:";
    private static final String COUNT_PREFIX = "OTP_COUNT:";

    // In-memory fallback if Redis connection fails
    private final Map<String, OtpData> fallbackOtpCache = new ConcurrentHashMap<>();
    private final Map<String, RateLimitTracker> fallbackRateLimitMap = new ConcurrentHashMap<>();

    private static class OtpData {
        final String code;
        final LocalDateTime expiryTime;

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

    /**
     * Generate 6-digit OTP code, enforce Rate Limiting (60s cooldown & 5 req/hour),
     * store in Redis for 5 minutes (with in-memory fallback), and send via Email.
     */
    public void generateAndSendOtp(String email) {
        String cleanEmail = email.trim().toLowerCase();
        String otpCode = String.format("%06d", new Random().nextInt(900000) + 100000);

        boolean redisSuccess = false;
        try {
            // 1. Redis Rate Limiting - Cooldown (60s)
            String cooldownKey = COOLDOWN_PREFIX + cleanEmail;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
                Long expireSec = redisTemplate.getExpire(cooldownKey);
                long waitSeconds = (expireSec != null && expireSec > 0) ? expireSec : 60;
                log.warn("⛔ [Redis RateLimiter] Cooldown active for {}. Wait: {}s", cleanEmail, waitSeconds);
                throw new IllegalArgumentException(
                        String.format("Vui lòng đợi %d giây trước khi yêu cầu gửi lại mã OTP tiếp theo.", waitSeconds)
                );
            }

            // 2. Redis Rate Limiting - Hourly Count (Max 5 req/hr)
            String countKey = COUNT_PREFIX + cleanEmail;
            String currentCountStr = redisTemplate.opsForValue().get(countKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            if (currentCount >= 5) {
                log.warn("⛔ [Redis RateLimiter] Hourly cap (5/hr) reached for {}", cleanEmail);
                throw new IllegalArgumentException("Bạn đã vượt quá giới hạn 5 lần yêu cầu OTP trong 1 giờ. Vui lòng thử lại sau.");
            }

            // 3. Save OTP (5 mins) & Cooldown (60s) & Increment Count in Redis
            String otpKey = OTP_PREFIX + cleanEmail;
            redisTemplate.opsForValue().set(otpKey, otpCode, Duration.ofMinutes(5));
            redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));

            if (currentCount == 0) {
                redisTemplate.opsForValue().set(countKey, "1", Duration.ofHours(1));
            } else {
                redisTemplate.opsForValue().increment(countKey);
            }

            redisSuccess = true;
            log.info("🔒 [Redis] Successfully stored OTP [{}] for email: {} (TTL 5m)", otpCode, cleanEmail);
        } catch (IllegalArgumentException e) {
            throw e; // Pass rate-limiting validation errors through directly
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable ({}), using fallback in-memory cache", e.getMessage());
            fallbackRateLimitMap.computeIfAbsent(cleanEmail, k -> new RateLimitTracker()).checkAndRecord();
            fallbackOtpCache.put(cleanEmail, new OtpData(otpCode, LocalDateTime.now().plusMinutes(5)));
        }

        // 4. Send email with OTP code
        emailService.sendOtpEmail(cleanEmail, otpCode);
    }

    /**
     * Verify 6-digit OTP code for email using Redis (with in-memory fallback).
     */
    public boolean verifyOtp(String email, String inputCode) {
        String cleanEmail = email.trim().toLowerCase();
        String trimmedInput = inputCode.trim();
        String otpKey = OTP_PREFIX + cleanEmail;

        try {
            String cachedCode = redisTemplate.opsForValue().get(otpKey);

            if (cachedCode == null) {
                // Check if it exists in fallback cache before failing
                OtpData fallbackData = fallbackOtpCache.get(cleanEmail);
                if (fallbackData != null) {
                    return verifyFallback(cleanEmail, trimmedInput, fallbackData);
                }
                log.warn("OTP verification failed: No OTP found in Redis for email {}", cleanEmail);
                throw new IllegalArgumentException("Mã OTP không tồn tại hoặc đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
            }

            if (!cachedCode.equals(trimmedInput)) {
                log.warn("OTP verification failed: Invalid OTP for email {}", cleanEmail);
                throw new IllegalArgumentException("Mã OTP không chính xác. Vui lòng kiểm tra lại.");
            }

            // Verified successfully, delete key from Redis
            redisTemplate.delete(otpKey);
            log.info("✅ [Redis] OTP verified successfully for email {}", cleanEmail);
            return true;

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw exact validation exception for AuthController to catch
        } catch (Exception e) {
            log.warn("⚠️ Redis read error ({}), attempting verification via fallback cache", e.getMessage());
            OtpData fallbackData = fallbackOtpCache.get(cleanEmail);
            if (fallbackData == null) {
                throw new IllegalArgumentException("Mã OTP không tồn tại hoặc đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
            }
            return verifyFallback(cleanEmail, trimmedInput, fallbackData);
        }
    }

    private boolean verifyFallback(String cleanEmail, String trimmedInput, OtpData fallbackData) {
        if (LocalDateTime.now().isAfter(fallbackData.expiryTime)) {
            fallbackOtpCache.remove(cleanEmail);
            log.warn("OTP verification failed: Fallback OTP expired for email {}", cleanEmail);
            throw new IllegalArgumentException("Mã OTP đã hết hạn (5 phút). Vui lòng nhấn Gửi lại mã.");
        }

        if (!fallbackData.code.equals(trimmedInput)) {
            log.warn("OTP verification failed: Invalid Fallback OTP for email {}", cleanEmail);
            throw new IllegalArgumentException("Mã OTP không chính xác. Vui lòng kiểm tra lại.");
        }

        fallbackOtpCache.remove(cleanEmail);
        log.info("✅ [Fallback Cache] OTP verified successfully for email {}", cleanEmail);
        return true;
    }
}
