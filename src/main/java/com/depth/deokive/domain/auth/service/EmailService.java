package com.depth.deokive.domain.auth.service;

import com.depth.deokive.domain.auth.dto.EmailPurpose;
import com.depth.deokive.domain.auth.repository.EmailRedisRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailRedisRepository emailRedisRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String EMAIL_SUBJECT = " Deokive 이메일 인증";

    @Value("${spring.mail.username}") private String mailUsername;
    @Value("${spring.mail.group}") private String mailGroup;

    @Async("mailTaskExecutor")
    public void sendEmail(String receiverEmail) {
        String normalizedEmail = emailNormalize(receiverEmail);
        String verificationCode = null;

        try {
            log.info("🟡 Trying to send Email to {}", receiverEmail);

            // p0: Message, p1: multipart 사용 여부, p2: encoding
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            // From To
            mimeMessageHelper.setFrom(new InternetAddress(mailUsername, mailGroup, "UTF-8"));
            mimeMessageHelper.setTo(normalizedEmail);

            // Generate Verification Code
            verificationCode = generateVerificationCode();

            // Email Contents
            mimeMessageHelper.setSubject(EMAIL_SUBJECT); // 메일 제목
            mimeMessageHelper.setText(setContext(verificationCode), true); // 메일 본문 내용, HTML 여부

            // Send
            javaMailSender.send(mimeMessage);

            // Register Code into Redis
            emailRedisRepository.saveVerificationCode(normalizedEmail, verificationCode);

            log.info("🟢 Success to Send Email to {}", receiverEmail);
        } catch (MessagingException e) {
            log.error("🔴 Failed to Send Email to {}: {}", receiverEmail, e.getMessage(), e);
            throw new RuntimeException("이메일 전송 실패: " + e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            log.error("🔴 Failed to Send Email to {}: {}", receiverEmail, e.getMessage(), e);
            throw new RuntimeException("인코딩 실패: " + e.getMessage(), e);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (이메일 발송 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (이메일 발송 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public void verifyEmailCode(String email, String code, EmailPurpose purpose) {
        try {
            log.info("🟡 Trying to verify Email to {}", email);
            String normalizedEmail = emailNormalize(email);
            String savedCode = emailRedisRepository.getVerificationCode(normalizedEmail);

            validateSavedCode(savedCode, code);

            emailRedisRepository.deleteVerificationCode(normalizedEmail);
            setFlagByPurpose(purpose, normalizedEmail);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (인증 코드 검증 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (인증 코드 검증 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public boolean hasVerifiedForSignup(String email) {
        try {
            log.info("🟡 Trying to Check Email Flag of {} For SignUp", email);
            return emailRedisRepository.hasVerifiedForSignup(emailNormalize(email));
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public boolean hasVerifiedForPasswordReset(String email) {
        try {
            log.info("🟡 Trying to Check Email Flag of {} For ResetPassword", email);
            return emailRedisRepository.hasVerifiedForPasswordReset(emailNormalize(email));
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 확인 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    // 플래그 삭제 메서드
    public void clearVerifiedForSignup(String email) {
        try {
            log.info("🟡 Trying to clear Email Flag of {} For SignUp", email);
            emailRedisRepository.clearVerifiedForSignup(emailNormalize(email));
            log.info("🟢 Successful Clear Email Flag of {} For SignUp", email);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    public void clearVerifiedForPasswordReset(String email) {
        try {
            log.info("🟡 Trying to clear Email Flag of {} For ResetPassword", email);
            emailRedisRepository.clearVerifiedForPasswordReset(emailNormalize(email));
            log.info("🟢 Successful Clear Email Flag of {} For ResetPassword", email);
        } catch (RedisConnectionFailureException e) {
            log.error("🔴 Redis 연결 실패 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_CONNECTION_FAILED);
        } catch (DataAccessException e) {
            log.error("🔴 Redis 데이터 접근 오류 (플래그 삭제 중): {}", e.getMessage(), e);
            throw new RestException(ErrorCode.REDIS_ERROR);
        }
    }

    // ------  Helper Methods ---------
    private String generateVerificationCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000); // 100000 ~ 999999 사이 숫자 생성
        return String.valueOf(code);
    }

    // Thymeleaf 를 통한 HTML 적용
    private String setContext(String verificationCode) {
        Context context = new Context();
        context.setVariable("date", LocalDateTime.now());
        context.setVariable("verificationCode", verificationCode);
        return templateEngine.process("emailVerification", context);
    }

    private void validateSavedCode(String savedCode, String verificationCode) {
        if (savedCode == null) { throw new RestException(ErrorCode.AUTH_EMAIL_CODE_INVALID); }
        if (!savedCode.equals(verificationCode)) { throw new RestException(ErrorCode.AUTH_EMAIL_CODE_NOT_MATCHED); }

        log.info("🟢 Successful Verification Email Code {} : {}", savedCode, verificationCode);
    }

    private String emailNormalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void setFlagByPurpose(EmailPurpose purpose, String normalizedEmail) {
        if (purpose == EmailPurpose.SIGNUP) {
            emailRedisRepository.markVerifiedForSignup(normalizedEmail);
        } else if (purpose == EmailPurpose.PASSWORD_RESET) {
            emailRedisRepository.markVerifiedForPasswordReset(normalizedEmail);
        }
    }
}
