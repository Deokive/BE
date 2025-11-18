package com.depth.deokive.system.security.jwt.service;

import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
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
        // 1) 쿠키에서 ATK/RTK 파싱 // ATK는 만료여도 괜찮음 -> 쿠키 생명을 RTK만큼 부여해야함
        JwtDto.TokenStringPair tokenStringPair
                = jwtTokenResolver.resolveTokenStringPair(tokenOption.getHttpServletRequest());

        // 2) 파싱/검증 및 기존 Tokens 제거
        clearTokensByAtkWithValidation(tokenStringPair.getAccessToken(), tokenStringPair.getRefreshToken());

        // 3) 사용자 로드
        var payload = jwtTokenResolver.resolveToken(tokenStringPair.getRefreshToken());
        String subject = payload.getSubject();
        UserPrincipal principal = resolveUser(subject);

        log.info("🔥 UserPrincipal resolved for token rotation");

        JwtDto.TokenOptionWrapper newTokenOption
                = JwtDto.TokenOptionWrapper.of(principal, tokenOption.isRememberMe());

        // 4) 새 토큰 페어 생성
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(newTokenOption);

        // 5) 이전 RTK UUID 블랙리스트로 이동 (남은 TTL만큼)
        Duration oldRtTtl = Duration.between(LocalDateTime.now(), payload.getExpiredAt());
        tokenRedisRepository.setBlacklistRtk(payload.getRefreshUuid(), oldRtTtl);

        // 6) 새 RTK 화이트리스트 등록
        Duration newRtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
        tokenRedisRepository.allowRtk(subject, extractRefreshUuid(tokenPair), newRtTtl);

        // 7) 새 ATK/RTK 쿠키로 재설정
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
        if (rtTtl.isNegative() || rtTtl.isZero()) {
            rtTtl = atTtl; // RTK가 이미 만료 상태면 ATK TTL 정도로 보수적으로 묶어준다
        }

        // 6) 블랙리스트 등록 및 허용 RTK 제거
        tokenRedisRepository.setBlacklistAtkJti(atkPayload.getJti(), atTtl);
        tokenRedisRepository.setBlacklistRtk(rtkPayload.getRefreshUuid(), rtTtl);
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
        // 1) ATK 파싱/검증
        var atkPayload = jwtTokenResolver.resolveToken(accessToken);
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
