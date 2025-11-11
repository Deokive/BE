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
import jakarta.servlet.http.HttpServletResponse;
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

    public JwtDto.TokenInfo issueTokens(UserPrincipal userPrincipal) {
        log.info("🔥 Issue Tokens");
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(userPrincipal);
        String subject = userPrincipal.getUserId() != null
                ? userPrincipal.getUserId().toString()
                : userPrincipal.getUsername();

        Duration rtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
        tokenRedisRepository.allowRtk(subject, extractRefreshUuid(tokenPair), rtTtl);
        return JwtDto.TokenInfo.of(tokenPair);
    }

    public JwtDto.TokenInfo rotateByRtkWithValidation(HttpServletRequest request, HttpServletResponse response) {
        log.info("✅ Rotate Tokens");
        // 1) 쿠키에서 ATK/RTK 파싱
        String accessToken = jwtTokenResolver.parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        // 2) 파싱/검증 및 기존 Tokens 제거
        clearTokensByAtkWithValidation(accessToken, refreshToken);

        // 3) 사용자 로드
        var payload = jwtTokenResolver.resolveToken(refreshToken);
        String subject = payload.getSubject();
        UserPrincipal principal = resolveUser(subject);

        log.info("🔥 UserPrincipal resolved for token rotation");

        // 4) 새 토큰 페어 생성
        JwtDto.TokenPair tokenPair = jwtTokenProvider.createTokenPair(principal);

        // 5) 이전 RTK UUID 블랙리스트로 이동 (남은 TTL만큼)
        Duration oldRtTtl = Duration.between(LocalDateTime.now(), payload.getExpiredAt());
        tokenRedisRepository.setBlacklistRtk(payload.getRefreshUuid(), oldRtTtl);

        // 6) 새 RTK 화이트리스트 등록
        Duration newRtTtl = Duration.between(LocalDateTime.now(), tokenPair.getRefreshToken().getExpiredAt());
        tokenRedisRepository.allowRtk(subject, extractRefreshUuid(tokenPair), newRtTtl);

        // 7) 새 ATK/RTK 쿠키로 재설정
        cookieUtils.addAccessTokenCookie(
                response,
                tokenPair.getAccessToken().getToken(),
                tokenPair.getAccessToken().getExpiredAt()
        );
        cookieUtils.addRefreshTokenCookie(
                response,
                tokenPair.getRefreshToken().getToken(),
                tokenPair.getRefreshToken().getExpiredAt()
        );

        return JwtDto.TokenInfo.of(tokenPair);
    }

    public void clearTokensByAtkWithValidation(String accessToken, String refreshToken) {
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
            return;
        }

        // 4) 제출된 RTK의 UUID와 Redis의 허용 UUID 일치성 확인
        String submittedUuid = rtkPayload.getRefreshUuid();
        if (submittedUuid == null || !submittedUuid.equals(allowedRtkUuid)) {
            // 허용된 RTK가 아닌 토큰으로 로그아웃을 시도
            throw new RestException(ErrorCode.JWT_INVALID); // 혹은 별도 에러코드
        }

        // 5) TTL 계산
        Duration atTtl = Duration.between(LocalDateTime.now(), atkPayload.getExpiredAt());
        Duration rtTtl = Duration.between(LocalDateTime.now(), rtkPayload.getExpiredAt());
        if (rtTtl.isNegative() || rtTtl.isZero()) {
            rtTtl = atTtl; // RTK가 이미 만료 상태면 ATK TTL 정도로 보수적으로 묶어준다
        }

        // 6) 블랙리스트 등록 및 허용 RTK 제거
        tokenRedisRepository.setBlacklistAtkJti(atkPayload.getJti(), atTtl);
        tokenRedisRepository.setBlacklistRtk(allowedRtkUuid, rtTtl);
        tokenRedisRepository.clearAllowedRtk(subject);
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
}
