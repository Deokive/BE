package com.depth.deokive.system.security.jwt.service;

import com.depth.deokive.common.util.ClientUtils;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.dto.TokenType;
import com.depth.deokive.system.security.jwt.exception.JwtBlacklistException;
import com.depth.deokive.system.security.jwt.exception.JwtExpiredException;
import com.depth.deokive.system.security.jwt.exception.JwtInvalidException;
import com.depth.deokive.system.security.jwt.repository.TokenRedisRepository;
import com.depth.deokive.system.security.jwt.util.JwtTokenProvider;
import com.depth.deokive.system.security.jwt.util.JwtTokenResolver;
import com.depth.deokive.system.security.jwt.util.JwtTokenValidator;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.depth.deokive.system.security.util.CookieUtils;
import com.depth.deokive.system.security.util.UserLoadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenResolver jwtTokenResolver;
    private final TokenRedisRepository tokenRedisRepository;
    private final UserLoadService userLoadService;
    private final JwtTokenValidator jwtTokenValidator;
    private final CookieUtils cookieUtils;
    private final RedissonClient redissonClient;

    public JwtDto.TokenInfo issueTokens(JwtDto.TokenOptionWrapper tokenOptions) {
        log.info("🔥 Issue Tokens");
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(tokenOptions);

        UserPrincipal userPrincipal = tokenOptions.getUserPrincipal();
        String subject = userPrincipal.getUserId() != null
                ? userPrincipal.getUserId().toString()
                : userPrincipal.getUsername();

        Duration rtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
        tokenRedisRepository.allowRtk(subject, extractRefreshUuid(tokenPair), rtTtl);
        return JwtDto.TokenInfo.from(tokenPair);
    }

    public JwtDto.TokenInfo rotateByRtkWithValidation(JwtDto.TokenOptionWrapper tokenOption) {
        log.info("✅ Rotate Tokens - START");

        // 1) RTK 파싱 (ATK는 없어도 가능)
        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(tokenOption.getHttpServletRequest())
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        // 2) RTK 파싱 및 만료 검증 (만료된 RTK는 refresh 불가)
        JwtDto.TokenPayload rtkPayload;
        try {
            rtkPayload = jwtTokenResolver.resolveToken(refreshToken);
        } catch (JwtExpiredException e) {
            log.warn("⚠️ Refresh token has expired, cannot rotate tokens");
            throw new RestException(ErrorCode.JWT_EXPIRED);
        }

        // 3) RTK 기본 유효성 검증 (타입 체크만 - 블랙리스트/UUID는 Lock 내부에서 재검증)
        if (rtkPayload.getTokenType() != TokenType.REFRESH) {
            log.warn("⚠️ RTK validation failed: TokenType이 REFRESH가 아님 - {}", rtkPayload.getTokenType());
            throw new JwtInvalidException();
        }
        if (rtkPayload.getSubject() == null || rtkPayload.getSubject().isEmpty()) {
            log.warn("⚠️ RTK validation failed: Subject가 null이거나 비어있음");
            throw new JwtInvalidException();
        }
        if (rtkPayload.getRefreshUuid() == null || rtkPayload.getRefreshUuid().isEmpty()) {
            log.warn("⚠️ RTK validation failed: RefreshUuid가 null이거나 비어있음");
            throw new JwtInvalidException();
        }

        String subject = rtkPayload.getSubject();
        String submittedUuid = rtkPayload.getRefreshUuid();
        String lockKey = "lock:rtk:rotate:" + subject;

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 4) 분산 락 획득 (Race Condition 방지)
            // - waitTime: 1초 (토큰 갱신은 보통 500ms 이내, Graceful Fallback으로 실패 시에도 성공)
            // - leaseTime: 3초 (데드락 방지)
            if (!lock.tryLock(1, 3, TimeUnit.SECONDS)) {
                log.error("⚠️ Failed to acquire lock for RTK rotation (timeout) - Subject: {}", subject);
                throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
            }

            log.debug("🔒 Lock acquired for RTK rotation - Subject: {}", subject);

            // 5) Double-Check: Lock 획득 후 RTK UUID 재검증 (중복 갱신 방지)
            String currentAllowedUuid = tokenRedisRepository.getAllowedRtk(subject);

            if (currentAllowedUuid == null) {
                log.warn("⚠️ RTK validation failed: Redis에 허용된 RTK가 없음 (이미 로그아웃됨) - Subject: {}", subject);
                throw new JwtInvalidException();
            }

            // 5-1) IP 로깅 (보안 모니터링)
            String clientIp = ClientUtils.getClientIp(tokenOption.getHttpServletRequest());

            if (!currentAllowedUuid.equals(submittedUuid)) {
                // Graceful Fallback: Race Condition 패배자 처리
                log.warn("⚠️ RTK UUID 불일치 감지 (Race Condition) - Subject: {}, Submitted: {}, Current: {}, IP: {}",
                        subject, submittedUuid, currentAllowedUuid, clientIp);

                // 1. old RTK가 블랙리스트에 있는지 확인 (정상적인 Race Condition인지 검증)
                if (!tokenRedisRepository.isRtkBlacklisted(submittedUuid)) {
                    // 블랙리스트에 없는데 UUID 불일치 → 비정상 (탈취 의심)
                    log.error("🚨 SECURITY ALERT: RTK UUID 불일치 but NOT blacklisted - Subject: {}, UUID: {}, IP: {}",
                            subject, submittedUuid, clientIp);
                    throw new JwtInvalidException();
                }

                // 2. Grace Period 체크: 블랙리스트 등록 후 30초 이내만 허용
                Duration remainingTtl = tokenRedisRepository.getBlacklistRtkTtl(submittedUuid);
                Duration originalTtl = Duration.between(LocalDateTime.now(), rtkPayload.getExpiredAt());
                Duration elapsed = originalTtl.minus(remainingTtl);

                final long GRACE_PERIOD_SECONDS = 30;
                if (elapsed.getSeconds() > GRACE_PERIOD_SECONDS) {
                    // Grace Period 초과 → 의심스러운 요청
                    log.error("🚨 SECURITY ALERT: Old RTK 사용 (Grace Period 초과) - Elapsed: {}s, IP: {}",
                            elapsed.getSeconds(), clientIp);
                    throw new JwtInvalidException();
                }

                // 3. Retry Counter 증가 및 체크 (동일 old RTK로 3번까지만 허용)
                Long retryCount = tokenRedisRepository.incrementRtkRetryCount(
                        submittedUuid,
                        Duration.ofSeconds(GRACE_PERIOD_SECONDS)
                );

                final long MAX_RETRY_COUNT = 3;
                if (retryCount > MAX_RETRY_COUNT) {
                    log.error("🚨 SECURITY ALERT: Old RTK 과도한 재시도 - Count: {}, IP: {}", retryCount, clientIp);
                    throw new JwtInvalidException();
                }

                // 4. Graceful Fallback: 현재 허용된 RTK로 새 토큰 발급 (갱신하지 않음!)
                log.info("✅ Graceful Fallback - 현재 RTK로 토큰 발급 (갱신 없음) - Retry: {}/{}, Elapsed: {}s, IP: {}",
                        retryCount, MAX_RETRY_COUNT, elapsed.getSeconds(), clientIp);

                return issueTokensWithCurrentRtk(subject, tokenOption);
            }

            log.debug("✅ RTK UUID matched - proceeding with rotation - Client IP: {}", clientIp);

            // 6) 블랙리스트 검증
            if (tokenRedisRepository.isRtkBlacklisted(submittedUuid)) {
                log.warn("⚠️ RTK validation failed: 블랙리스트에 등록된 토큰 - UUID: {}", submittedUuid);
                throw new JwtBlacklistException();
            }

            // 7) ATK가 있으면 기존 Tokens 제거 (ATK 없으면 RTK만 처리)
            var nullableAtk = jwtTokenResolver.parseTokenFromRequest(tokenOption.getHttpServletRequest());
            if (nullableAtk.isPresent()) {
                try {
                    clearTokensByAtkWithValidation(nullableAtk.get(), refreshToken);
                } catch (Exception e) {
                    // ATK가 만료되었거나 유효하지 않아도 RTK만으로 Refresh 가능
                    log.debug("⚠️ Failed to clear old tokens, but continuing with refresh: {}", e.getMessage());
                }
            } else {
                // ATK가 없으면 이전 RTK만 블랙리스트 처리
                Duration rtTtl = Duration.between(LocalDateTime.now(), rtkPayload.getExpiredAt());
                if (rtTtl.isPositive()) {
                    tokenRedisRepository.setBlacklistRtk(rtkPayload.getRefreshUuid(), rtTtl);
                } else {
                    Duration minTtl = Duration.ofMinutes(1);
                    tokenRedisRepository.setBlacklistRtk(rtkPayload.getRefreshUuid(), minTtl);
                }
            }

            // 8) 사용자 로드
            UserPrincipal principal = resolveUser(subject);
            log.info("🔥 UserPrincipal resolved for token rotation");

            JwtDto.TokenOptionWrapper newTokenOption
                    = JwtDto.TokenOptionWrapper.of(principal, tokenOption.isRememberMe());

            // 9) 새 토큰 페어 생성
            JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(newTokenOption);

            // 10) 새 RTK 화이트리스트 등록 (원자적으로 갱신)
            Duration newRtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
            String newRefreshUuid = extractRefreshUuid(tokenPair);
            tokenRedisRepository.allowRtk(subject, newRefreshUuid, newRtTtl);

            log.info("🔄 RTK rotated successfully - Subject: {}, Old UUID: {}, New UUID: {}, Client IP: {}",
                    subject, submittedUuid, newRefreshUuid, clientIp);

            // 11) 새 ATK/RTK 쿠키로 재설정
            cookieUtils.addAccessTokenCookie(
                    tokenOption.getHttpServletResponse(),
                    tokenPair.getAccessToken().getToken(),
                    tokenPair.getRefreshToken().getExpiredAt()
            );
            cookieUtils.addRefreshTokenCookie(
                    tokenOption.getHttpServletResponse(),
                    tokenPair.getRefreshToken().getToken(),
                    tokenPair.getRefreshToken().getExpiredAt()
            );

            return JwtDto.TokenInfo.from(tokenPair);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("⚠️ Thread interrupted while waiting for RTK rotation lock - Subject: {}", subject, e);
            throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 Lock released for RTK rotation - Subject: {}", subject);
            }
        }
    }

    public void clearTokensByAtkWithValidation(String accessToken, String refreshToken) {
        JwtDto.TokenOptionWrapper validatedPayloadPair = validatedPayloadPair(accessToken, refreshToken);
        if (validatedPayloadPair == null) return;

        JwtDto.TokenPayload atkPayload = validatedPayloadPair.getAtkPayload();
        JwtDto.TokenPayload rtkPayload = validatedPayloadPair.getRtkPayload();

        Duration atTtl = Duration.between(LocalDateTime.now(), atkPayload.getExpiredAt());
        Duration rtTtl = Duration.between(LocalDateTime.now(), rtkPayload.getExpiredAt());
        
        // ATK 블랙리스트 등록: 만료되지 않은 경우에만 등록
        // 만료된 ATK는 이미 사용 불가능하므로 블랙리스트에 등록할 필요 없음
        if (atTtl.isPositive()) {
            tokenRedisRepository.setBlacklistAtkJti(atkPayload.getJti(), atTtl);
        } else {
            log.debug("⚠️ ATK already expired, skipping blacklist registration for jti: {}", atkPayload.getJti());
        }
        
        // RTK 블랙리스트 등록: 유효한 경우에만 등록
        if (rtTtl.isPositive()) {
            tokenRedisRepository.setBlacklistRtk(rtkPayload.getRefreshUuid(), rtTtl);
        } else {
            // RTK가 이미 만료된 경우, 최소 TTL로 등록 (보수적 처리)
            Duration minTtl = Duration.ofMinutes(1);
            tokenRedisRepository.setBlacklistRtk(rtkPayload.getRefreshUuid(), minTtl);
            log.debug("⚠️ RTK already expired, using minimum TTL for blacklist: {}", rtkPayload.getRefreshUuid());
        }
        
        // 허용 RTK 제거
        tokenRedisRepository.clearAllowedRtk(atkPayload.getSubject());
    }

    public boolean isRtkBlacklisted(String refreshToken) {
        var rtkPayload = jwtTokenResolver.resolveToken(refreshToken);
        String submittedUuid = rtkPayload.getRefreshUuid();

        return tokenRedisRepository.isRtkBlacklisted(submittedUuid);
    }

    public boolean isAtkBlacklisted(String accessToken) {
        var atkPayload = jwtTokenResolver.resolveToken(accessToken);
        String jti = atkPayload.getJti();

        return tokenRedisRepository.isAtkBlacklisted(jti);
    }

    public JwtDto.TokenExpiresInfo getTokenExpiresInfo(HttpServletRequest request) {
        // 1). Parse Token from Cookies
        JwtDto.TokenStringPair tokenStringPair
                = jwtTokenResolver.resolveTokenStringPair(request);

        // 2). Validation & Get Payloads
        JwtDto.TokenOptionWrapper validatedPayloadPair
                = validatedPayloadPair(tokenStringPair.getAccessToken(), tokenStringPair.getRefreshToken());
        if (validatedPayloadPair == null) return null;

        return JwtDto.TokenExpiresInfo.of(validatedPayloadPair.getAtkPayload(), validatedPayloadPair.getRtkPayload());
    }

    public boolean validateTokens(HttpServletRequest request) {
        try {
            JwtDto.TokenStringPair tokenStringPair = jwtTokenResolver.resolveTokenStringPair(request);
            JwtDto.TokenOptionWrapper validated
                    = validatedPayloadPair(tokenStringPair.getAccessToken(), tokenStringPair.getRefreshToken());
            return validated != null;
        } catch (Exception e) {
            log.error("🔴validateTokens {}",e.getMessage(), e);
            return false;
        }
    }

    // Helper Methods
    private UserPrincipal resolveUser(String subject) {
        try {
            Long id = Long.valueOf(subject);
            return userLoadService.loadUserById(id).orElseThrow(JwtInvalidException::new);
        } catch (NumberFormatException nfe) {
            return userLoadService.loadUserByUsername(subject).orElseThrow(JwtInvalidException::new);
        }
    }

    private String extractRefreshUuid(JwtDto.TokenPair tokenPair) {
        var payload = jwtTokenResolver.resolveToken(tokenPair.getRefreshToken().getToken());
        return payload.getRefreshUuid();
    }

    /**
     * 현재 허용된 RTK로 새 토큰 발급 (갱신하지 않음)
     * Race Condition 패배자를 위한 Graceful Fallback
     */
    private JwtDto.TokenInfo issueTokensWithCurrentRtk(
            String subject,
            JwtDto.TokenOptionWrapper tokenOption
    ) {
        // 1) 사용자 로드
        UserPrincipal principal = resolveUser(subject);

        // 2) 새 토큰 페어 생성 (현재 허용된 RTK 기준)
        JwtDto.TokenOptionWrapper newTokenOption
                = JwtDto.TokenOptionWrapper.of(principal, tokenOption.isRememberMe());
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(newTokenOption);

        // 3) RTK는 이미 Redis에 등록되어 있음 (req1이 갱신함)
        // 따라서 allowRtk 호출 불필요 → 중복 갱신 방지

        // 4) 쿠키 설정
        cookieUtils.addAccessTokenCookie(
                tokenOption.getHttpServletResponse(),
                tokenPair.getAccessToken().getToken(),
                tokenPair.getRefreshToken().getExpiredAt()
        );
        cookieUtils.addRefreshTokenCookie(
                tokenOption.getHttpServletResponse(),
                tokenPair.getRefreshToken().getToken(),
                tokenPair.getRefreshToken().getExpiredAt()
        );

        return JwtDto.TokenInfo.from(tokenPair);
    }

    private JwtDto.TokenOptionWrapper validatedPayloadPair(String accessToken, String refreshToken) {
        // 1) ATK 파싱/검증 (만료되어도 파싱 가능)
        JwtDto.TokenPayload atkPayload;
        try {
            atkPayload = jwtTokenResolver.resolveToken(accessToken);
        } catch (JwtExpiredException e) {
            // ATK가 만료되어도 정보 추출 가능 (자동 로그인 지원)
            atkPayload = jwtTokenResolver.resolveExpiredToken(accessToken);
        }
        // 블랙리스트 검증 (만료 여부와 무관하게 검증)
        jwtTokenValidator.validateAtk(atkPayload);

        // 2) RTK 파싱/검증
        var rtkPayload = jwtTokenResolver.resolveToken(refreshToken);
        jwtTokenValidator.validateRtk(rtkPayload);

        // 3) Redis에서 허용된 RTK UUID 조회
        String subject = atkPayload.getSubject();        // ATK의 subject 기준으로 조회
        String allowedRtkUuid = tokenRedisRepository.getAllowedRtk(subject);

        // 3-1) 허용 RTK가 없다면(이미 만료/제거) 서버 상태만 정리하고 빠진다
        if (allowedRtkUuid == null) {
            Duration atTtl = Duration.between(LocalDateTime.now(), atkPayload.getExpiredAt());
            tokenRedisRepository.setBlacklistAtkJti(atkPayload.getJti(), atTtl);
            tokenRedisRepository.clearAllowedRtk(subject);
            return null;
        }

        // 4) 제출된 RTK의 UUID와 Redis의 허용 UUID 일치성 확인
        String submittedUuid = rtkPayload.getRefreshUuid();
        if (submittedUuid == null || !submittedUuid.equals(allowedRtkUuid)) {
            // 허용된 RTK가 아닌 토큰으로 로그아웃을 시도
            throw new RestException(ErrorCode.JWT_INVALID); // 혹은 별도 에러코드
        }

        return JwtDto.TokenOptionWrapper.of(atkPayload, rtkPayload);
    }
}
