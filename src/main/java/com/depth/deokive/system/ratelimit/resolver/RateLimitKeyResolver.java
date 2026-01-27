package com.depth.deokive.system.ratelimit.resolver;

import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import com.depth.deokive.system.ratelimit.config.RateLimitProperties;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.depth.deokive.system.security.util.HmacUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitKeyResolver {

    private static final String EMAIL_PARAM = "email";
    private static final String XFF = "X-Forwarded-For";

    private final RateLimitProperties props;
    private final HmacUtil hmacUtil;

    public String resolveKey(String keyPrefix, RateLimitType type, ProceedingJoinPoint joinPoint) {
        String identifier = resolveIdentifier(type, joinPoint);
        String identifierType = resolveIdentifierType(type);

        return String.format("%s:%s:%s:%s",
                props.getKey().getNamespace(),
                keyPrefix,
                identifierType,
                identifier
        );
    }

    private String resolveIdentifierType(RateLimitType type) {
        return switch (type) {
            case USER -> props.getKey().getUserPrefix();
            case IP -> props.getKey().getIpPrefix();
            case EMAIL -> props.getKey().getEmailPrefix();
            case AUTO -> (tryGetUserId() != null) ? props.getKey().getUserPrefix() : props.getKey().getIpPrefix();
        };
    }

    private String resolveIdentifier(RateLimitType type, ProceedingJoinPoint joinPoint) {
        return switch (type) {
            case USER -> getUserId();
            case IP -> getClientIpWithTrustBoundary();
            case EMAIL -> getEmailHmac(joinPoint);
            case AUTO -> {
                String userId = tryGetUserId();
                yield userId != null ? userId : getClientIpWithTrustBoundary();
            }
        };
    }

    private String getUserId() {
        String userId = tryGetUserId();
        if (userId == null) {
            throw new IllegalStateException(
                    "RateLimitType.USER requires authenticated user, but no authentication found");
        }
        return userId;
    }

    private String tryGetUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            Long userId = userPrincipal.getUserId();
            return userId != null ? userId.toString() : null;
        }

        if (principal instanceof String && "anonymousUser".equals(principal)) {
            return null;
        }

        return null;
    }

    /**
     * ALB Trusted CIDR 조건에서만 XFF를 신뢰한다.
     */
    private String getClientIpWithTrustBoundary() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            // 웹 요청 맥락이 없으면 rate-limit 자체가 의미 없으므로 "unknown"처럼 합치지 않는다.
            throw new IllegalStateException("HttpServletRequest not found in RequestContextHolder");
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            throw new IllegalStateException("remoteAddr is null/blank");
        }

        if (!isTrustedProxy(remoteAddr)) {
            // 직통 호출(또는 신뢰되지 않은 프록시): XFF 무시
            String xff = request.getHeader(XFF);
            if (xff != null && !xff.isBlank()) {
                // 신뢰되지 않은 프록시인데 XFF 헤더가 있음 → 의심스러운 요청
                log.warn("X-Forwarded-For header from untrusted proxy. remoteAddr={}, xff={}",
                        remoteAddr, xff);
            }
            return remoteAddr;
        }

        // 신뢰된 프록시(ALB)인 경우만 XFF 사용
        String xff = request.getHeader(XFF);
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }

        // XFF는 "client, proxy1, proxy2" 형태이므로 첫 번째를 사용
        String first = xff.split(",")[0].trim();
        return first.isBlank() ? remoteAddr : first;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        List<String> cidrs = props.getProxy().trustedCidrsAsList();
        if (cidrs == null || cidrs.isEmpty()) {
            // 설정이 없으면 XFF를 절대 신뢰하지 않는다.
            return false;
        }
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) continue;
            try {
                if (new IpAddressMatcher(cidr.trim()).matches(remoteAddr)) {
                    return true;
                }
            } catch (Exception ex) {
                log.warn("Invalid trusted CIDR: {}", cidr);
            }
        }
        return false;
    }

    /**
     * EMAIL은 PII 보호를 위해 HMAC(Base64url)로 변환해 key에 사용한다.
     *
     * 우선순위:
     * 1) joinPoint args 중 getEmail() 보유 객체에서 추출
     * 2) request param "email"에서 추출
     * 3) 없으면 예외(인증 API에서 EMAIL 제한을 선언했는데 못 뽑으면 정책 결함)
     */
    private String getEmailHmac(ProceedingJoinPoint joinPoint) {
        String rawEmail = extractEmailFromArgs(joinPoint)
                .orElseGet(this::extractEmailFromRequestParam);

        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalStateException("EMAIL rate-limit requires email, but not found");
        }

        String normalized = rawEmail.trim().toLowerCase();
        return hmacUtil.hmacSha256Base64(normalized);
    }

    private Optional<String> extractEmailFromArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return Optional.empty();
        }

        for (Object arg : args) {
            if (arg == null) continue;
            try {
                Method m = arg.getClass().getMethod("getEmail");
                Object val = m.invoke(arg);
                if (val instanceof String s && !s.isBlank()) {
                    return Optional.of(s);
                }
            } catch (NoSuchMethodException ignored) {
                // DTO가 아닐 수 있음 -> 다음 후보 탐색
            } catch (Exception ex) {
                // getEmail이 있는데 호출 실패면 개발 결함(일단 로깅)
                log.warn("Failed to extract email via getEmail() from arg type={}", arg.getClass().getName());
            }
        }
        return Optional.empty();
    }

    private String extractEmailFromRequestParam() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String email = request.getParameter(EMAIL_PARAM);
        return (email == null || email.isBlank()) ? null : email;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
