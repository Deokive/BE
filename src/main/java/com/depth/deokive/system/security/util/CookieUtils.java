package com.depth.deokive.system.security.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class CookieUtils {

    @Value("${app.cookie.cookie-atk}") private String cookieAtkKey;
    @Value("${app.cookie.cookie-rtk}") private String cookieRtkKey;
    @Value("${app.cookie.secure}") private boolean cookieSecureOnHttps;
    @Value("${app.cookie.same-site}") private String cookieSameSite;
    @Value("${app.cookie.atk-apply-path}") private String cookieAtkApplyPath;
    @Value("${app.cookie.rtk-apply-path}") private String cookieRtkApplyPath;
    @Value("${app.cookie.domain:}") private String cookieDomain;

    public void addCookie(
            HttpServletResponse res,
            String cookieKey,
            String token,
            LocalDateTime expiresAt,
            String path
    ) {
        // SameSite 값 검증 및 정규화
        String normalizedSameSite = normalizeSameSite(cookieSameSite);

        // maxAge 계산 (과거 시간이면 0으로 설정)
        Duration duration = Duration.between(LocalDateTime.now(), expiresAt);
        long maxAgeSeconds = duration.isNegative() ? 0 : duration.getSeconds();

        var cookieBuilder = ResponseCookie.from(cookieKey, token)
                .httpOnly(true)
                .secure(cookieSecureOnHttps)
                .path(path)
                .maxAge(maxAgeSeconds);

        // Domain 설정 추가
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookieBuilder.domain(cookieDomain);
        }

        // SameSite 설정 (Spring Boot 3.x에서는 문자열을 직접 사용)
        if (normalizedSameSite != null && !normalizedSameSite.isEmpty()) {
            cookieBuilder.sameSite(normalizedSameSite);
        }

        var cookie = cookieBuilder.build();

        res.addHeader("Set-Cookie", cookie.toString());

        log.info("🍪 Cookie 설정 완료 - Key: {}, Path: {}, SameSite: {}, Secure: {}, MaxAge: {}초",
                cookieKey, path, normalizedSameSite, cookieSecureOnHttps, maxAgeSeconds);
    }

    private String normalizeSameSite(String sameSite) {
        if (sameSite == null || sameSite.isEmpty()) {
            return "Lax";
        }
        String upper = sameSite.toUpperCase();
        return switch (upper) {
            case "NONE", "STRICT", "LAX" -> upper;
            default -> {
                log.warn("⚠️ 알 수 없는 SameSite 값: {}. 기본값 LAX 사용", sameSite);
                yield "Lax";
            }
        };
    }

    public void clearCookie(
            HttpServletResponse res,
            String cookieKey,
            String path
    ) {
        String normalizedSameSite = normalizeSameSite(cookieSameSite);

        var cookieBuilder = ResponseCookie.from(cookieKey, "")
                .httpOnly(true)
                .secure(cookieSecureOnHttps)
                .path(path)
                .maxAge(0); // 핵심: maxAge=0 → 즉시 삭제

        if (normalizedSameSite != null && !normalizedSameSite.isEmpty()) {
            cookieBuilder.sameSite(normalizedSameSite);
        }

        var cookie = cookieBuilder.build();

        res.addHeader("Set-Cookie", cookie.toString());

        log.info("🍪 Cookie 삭제 완료 - Key: {}, Path: {}", cookieKey, path);
    }

    public void addAccessTokenCookie(HttpServletResponse res, String token, LocalDateTime exp) {
        addCookie(res, cookieAtkKey, token, exp, cookieAtkApplyPath); // 모든 API 요청에 자동으로 ATK 쿠키 설정
    }

    public void addRefreshTokenCookie(HttpServletResponse res, String token, LocalDateTime exp) {
        addCookie(res, cookieRtkKey, token, exp, cookieRtkApplyPath); // RTK는 회전 엔드포인트 전용
    }

    public void clearAccessTokenCookie(HttpServletResponse res) {
        clearCookie(res, cookieAtkKey, cookieAtkApplyPath);
    }

    public void clearRefreshTokenCookie(HttpServletResponse res) {
        clearCookie(res, cookieRtkKey, cookieRtkApplyPath);
    }

    // HttpServletRequest 에서 쿠키 value 읽기
    public String getCookieValue(HttpServletRequest req, String name) {
        var cookie = WebUtils.getCookie(req, name);
        return cookie != null ? cookie.getValue() : null;
    }
}
