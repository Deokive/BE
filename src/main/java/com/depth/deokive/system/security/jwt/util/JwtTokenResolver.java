package com.depth.deokive.system.security.jwt.util;

import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.dto.TokenType;
import com.depth.deokive.system.security.util.CookieUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenResolver {
    private final JwtTokenValidator jwtTokenValidator;
    private final CookieUtils cookieUtils;

    @Value("${app.cookie.cookie-atk}") private String cookieAtkKey;
    @Value("${app.cookie.cookie-rtk}") private String cookieRtkKey;

    public Optional<String> parseTokenFromRequest(HttpServletRequest request) {
        try {
            // 1. Authorization Header 우선 (API 테스트용)
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                log.debug("🟢 Authorization Header Token found in JwtTokenResolver");
                return Optional.of(token);
            }

            // 2. Cookie에서 토큰 읽기 (브라우저용)
            String atkFromCookie = cookieUtils.getCookieValue(request, cookieAtkKey);
            if (atkFromCookie != null && !atkFromCookie.isBlank()) {
                log.debug("🟢 Cookie Token found in JwtTokenResolver - Key: {}, Value length: {}", cookieAtkKey, atkFromCookie.length());
                return Optional.of(atkFromCookie);
            }

            // 디버깅: 쿠키를 찾지 못한 경우 상세 로그 출력
            // 일반적인 봇/스캐너 요청은 DEBUG 레벨로 처리
            String uri = request.getRequestURI();
            if (isCommonBotOrScannerPath(uri)) {
                log.debug("🔍 Bot/scanner request (no ATK cookie) - URI: {}", uri);
            } else {
                log.warn("⚠️ Access Token Cookie not found - Key: {}, Request URI: {}, Available cookies: {}", 
                        cookieAtkKey, 
                        uri,
                        request.getCookies() != null ? Arrays.stream(request.getCookies())
                                .map(c -> c.getName() + "=" + (c.getValue().length() > 20 ? c.getValue().substring(0, 20) + "..." : c.getValue()))
                                .collect(Collectors.joining(", ")) : "null");
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("⚠️ Exception while parsing token from request: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<String> parseRefreshTokenFromRequest(HttpServletRequest request) {
        try {
            // 1. Request Attribute 우선 확인 (자동 Refresh 직후 같은 요청에서 사용)
            String newRtk = (String) request.getAttribute("NEW_REFRESH_TOKEN");
            if (newRtk != null && !newRtk.isBlank()) {
                log.debug("🟢 New RefreshToken from request attribute (auto-refresh)");
                return Optional.of(newRtk);
            }
            
            // 2. Cookie에서 읽기
            String rtkFromCookie = cookieUtils.getCookieValue(request, cookieRtkKey);
            if (rtkFromCookie != null && !rtkFromCookie.isBlank()) {
                log.debug("🟢 Cookie RefreshToken found in JwtTokenResolver");
                return Optional.of(rtkFromCookie);
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public JwtDto.TokenPayload resolveToken(String token) {
        Claims payload = jwtTokenValidator.parseClaimsWithValidation(token).getPayload();
        LocalDateTime exp = payload.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        String type = payload.get("type", String.class);
        String role = payload.get("role", String.class);
        Boolean rememberMe = payload.get("rememberMe", Boolean.class);

        return JwtDto.TokenPayload.builder()
                .subject(payload.getSubject())
                .expiredAt(exp)
                .tokenType(type == null ? null : TokenType.valueOf(type))
                .role(role == null ? null : Role.valueOf(role))
                .refreshUuid(payload.get("refreshUuid", String.class))
                .jti(payload.getId())
                .rememberMe(rememberMe)
                .build();
    }

    public JwtDto.TokenPayload resolveExpiredToken(String token) {
        Claims payload = jwtTokenValidator.parseExpiredTokenClaims(token);
        LocalDateTime exp = payload.getExpiration() != null
                ? payload.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;

        String type = payload.get("type", String.class);
        String role = payload.get("role", String.class);
        Boolean rememberMe = payload.get("rememberMe", Boolean.class);

        return JwtDto.TokenPayload.builder()
                .subject(payload.getSubject())
                .expiredAt(exp)
                .tokenType(type == null ? null : TokenType.valueOf(type))
                .role(role == null ? null : Role.valueOf(role))
                .refreshUuid(payload.get("refreshUuid", String.class))
                .jti(payload.getId())
                .rememberMe(rememberMe)
                .build();
    }

    public JwtDto.TokenStringPair resolveTokenStringPair(HttpServletRequest request) {
        String accessToken = parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        return JwtDto.TokenStringPair.of(accessToken, refreshToken);
    }

    /**
     * 일반적인 봇/스캐너가 요청하는 경로인지 확인
     * @param uri 요청 URI
     * @return 봇/스캐너 경로면 true
     */
    private boolean isCommonBotOrScannerPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }

        // .well-known 경로 (RFC 8615)
        if (uri.startsWith("/.well-known/")) {
            return true;
        }

        // 확장자 기반 체크
        String lowerUri = uri.toLowerCase();
        if (lowerUri.endsWith(".txt") || 
            lowerUri.endsWith("accesspolicy.xml") ||
            (lowerUri.contains("sitemap") && lowerUri.endsWith(".xml"))) {
            return true;
        }

        // 특정 파일명 패턴
        if (lowerUri.equals("/security.txt") ||
            lowerUri.equals("/robots.txt") ||
            lowerUri.equals("/favicon.ico") ||
            (lowerUri.startsWith("/sitemap") && lowerUri.endsWith(".xml"))) {
            return true;
        }

        return false;
    }
}
