package com.depth.deokive.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class EmailRedisRepository {
    @Qualifier("emailTemplate")
    private final StringRedisTemplate emailTemplate;

    // Redis Key
    private static final String CODE_PREFIX = "email:code:";
    private static final String SIGNUP_VERIFIED_PREFIX = "signup:verified:";
    private static final String RESET_PW_CODE_PREFIX = "reset-pw:verified:";

    // TTL
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(15);

    // Common Methods
    public void saveVerificationCode(String email, String code) {
        emailTemplate.opsForValue().set(CODE_PREFIX + email, code, CODE_TTL);
    }

    public String getVerificationCode(String email) {
        return emailTemplate.opsForValue().get(CODE_PREFIX + email);
    }

    public void deleteVerificationCode(String email) {
        emailTemplate.delete(CODE_PREFIX + email);
    }

    // Signup
    public void markVerifiedForSignup(String email) {
        emailTemplate.opsForValue().set(SIGNUP_VERIFIED_PREFIX + email, "1", VERIFIED_TTL);
    }

    public boolean hasVerifiedForSignup(String email) {
        return Boolean.TRUE.equals(emailTemplate.hasKey(SIGNUP_VERIFIED_PREFIX + email));
    }

    public void clearVerifiedForSignup(String email) {
        emailTemplate.delete(SIGNUP_VERIFIED_PREFIX + email);
    }

    // Reset Password
    public void markVerifiedForPasswordReset(String email) {
        emailTemplate.opsForValue().set(RESET_PW_CODE_PREFIX + email, "1", VERIFIED_TTL);
    }

    public boolean hasVerifiedForPasswordReset(String email) {
        return Boolean.TRUE.equals(emailTemplate.hasKey(RESET_PW_CODE_PREFIX + email));
    }

    public void clearVerifiedForPasswordReset(String email) {
        emailTemplate.delete(RESET_PW_CODE_PREFIX + email);
    }
}
