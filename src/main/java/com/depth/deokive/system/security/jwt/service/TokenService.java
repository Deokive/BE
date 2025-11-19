package com.depth.deokive.system.security.jwt.service;

import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

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
        log.info("✅ Rotate Tokens");
        
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
        
        // 3) RTK 유효성 검증 (타입, 블랙리스트 등)
        jwtTokenValidator.validateRtk(rtkPayload);
        
        // 4) ATK가 있으면 기존 Tokens 제거 (ATK 없으면 RTK만 처리)
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

        // 5) 사용자 로드
        String subject = rtkPayload.getSubject();
        UserPrincipal principal = resolveUser(subject);

        log.info("🔥 UserPrincipal resolved for token rotation");

        JwtDto.TokenOptionWrapper newTokenOption
                = JwtDto.TokenOptionWrapper.of(principal, tokenOption.isRememberMe());

        // 6) 새 토큰 페어 생성
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(newTokenOption);

        // 7) 새 RTK 화이트리스트 등록
        Duration newRtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
        tokenRedisRepository.allowRtk(subject, extractRefreshUuid(tokenPair), newRtTtl);

        // 8) 새 ATK/RTK 쿠키로 재설정
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
