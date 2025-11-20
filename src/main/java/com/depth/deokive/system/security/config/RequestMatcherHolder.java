package com.depth.deokive.system.security.config;

import com.depth.deokive.domain.user.entity.enums.Role;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestMatcherHolder {

    // PathPattern 파서를 한 번만 준비 (thread-safe)
    private static final PathPatternParser PARSER = new PathPatternParser();

    private static final List<RequestInfo> REQUEST_INFO_LIST = List.of(
            // auth
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/register", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/login", null),
            new RequestInfo(HttpMethod.GET, "/api/v1/auth/email-exist", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/refresh", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/is-blacklisted-rtk", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/is-blacklisted-atk", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/reset-pw", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/email/send", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/email/verify", null),

            // user

            // system
            new RequestInfo(null, "/api/system/**", null),

            // static resources
            new RequestInfo(HttpMethod.GET,  "/docs/**", null),
            new RequestInfo(HttpMethod.GET,  "/*.ico", null),
            new RequestInfo(HttpMethod.GET,  "/resources/**", null),
            new RequestInfo(HttpMethod.GET,  "/error", null),
            new RequestInfo(HttpMethod.GET,  "/swagger-ui/**", null),
            new RequestInfo(HttpMethod.GET,  "/v3/api-docs/**", null),
            new RequestInfo(HttpMethod.GET,  "/", null),

            // robots (JWT 미적용 / permitAll)
            new RequestInfo(HttpMethod.GET, "/robots.txt", null)

    );

    private final ConcurrentHashMap<String, RequestMatcher> reqMatcherCacheMap = new ConcurrentHashMap<>();

    /**
     * 최소 권한이 주어진 요청에 대한 RequestMatcher 반환 (캐시)
     */
    public RequestMatcher getRequestMatchersByMinRole(@Nullable Role minRole) {
        var key = (minRole == null ? "VISITOR" : minRole.name());
        return reqMatcherCacheMap.computeIfAbsent(key, k -> {
            var matchers = REQUEST_INFO_LIST.stream()
                    .filter(req -> Objects.equals(req.minRole(), minRole))
                    .map(this::toRequestMatcher)     // ← PathPattern 기반 매처로 변환
                    .toArray(RequestMatcher[]::new);
            return new OrRequestMatcher(matchers);
        });
    }

    /**
     * /api/**로 시작하는 모든 경로에 대한 RequestMatcher 반환 (캐시)
     * @return /api/**로 시작하는 경로면 true
     */
    public RequestMatcher getApiRequestMatcher() {
        return reqMatcherCacheMap.computeIfAbsent("API_PREFIX", k -> {
            final PathPattern apiPattern = PARSER.parse("/api/v1/**");
            return (HttpServletRequest request) -> {
                String uri = request.getRequestURI();
                String contextPath = request.getContextPath();
                if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                    uri = uri.substring(contextPath.length());
                }
                return apiPattern.matches(PathContainer.parsePath(uri));
            };
        });
    }

    /**
     * 단일 항목을 PathPattern 기반 RequestMatcher 로 변환
     */
    private RequestMatcher toRequestMatcher(RequestInfo info) {
        final PathPattern pattern = PARSER.parse(info.pattern());
        final HttpMethod method = info.method();

        return (HttpServletRequest request) -> {
            // 1) HTTP Method 체크
            if (method != null && !method.name().equalsIgnoreCase(request.getMethod())) { return false; }

            // 2) context-path 제거 후 PathPattern 매칭
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }
            return pattern.matches(PathContainer.parsePath(uri));
        };
    }

    private record RequestInfo(HttpMethod method, String pattern, Role minRole) { }
}
