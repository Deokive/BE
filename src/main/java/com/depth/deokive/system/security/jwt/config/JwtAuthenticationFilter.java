package com.depth.deokive.system.security.jwt.config;

import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.security.config.RequestMatcherHolder;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.exception.*;
import com.depth.deokive.system.security.jwt.service.TokenService;
import com.depth.deokive.system.security.jwt.util.JwtTokenResolver;
import com.depth.deokive.system.security.jwt.util.JwtTokenValidator;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.depth.deokive.system.security.util.UserLoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenResolver jwtTokenResolver;
    private final UserLoadService userLoadService;
    private final JwtTokenValidator jwtTokenValidator;
    private final RequestMatcherHolder requestMatcherHolder;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // 1. RequestMatcherHolder의 permitAll 경로는 필터 스킵
        if (requestMatcherHolder.getRequestMatchersByMinRole(null).matches(request)) {
            return true;
        }

        // 2. /api/v1/**가 아닌 경로는 필터 스킵 (SecurityConfig에서 denyAll()로 차단됨)
        String uri = request.getRequestURI();
        if (uri != null && !uri.startsWith("/api/v1/")) {
            return true; // 필터 스킵 (SecurityConfig에서 처리)
        }

        // 3. /api/v1/** 경로는 필터 통과 (인증 필요)
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 디버깅: 쿠키가 없을 때만 상세 로그 출력
        // /api/** 경로만 필터를 통과하므로 여기서는 정상적인 API 요청만 처리
        if (request.getCookies() == null || request.getCookies().length == 0) {
            String uri = request.getRequestURI();
            // /api/** 경로는 정상적인 API 요청이므로 WARN 레벨 유지
            log.warn("⚠️ No cookies in request - URI: {}, Method: {}, Origin: {}, Referer: {}, Cookie Header: {}, All Headers: {}", 
                    uri, 
                    request.getMethod(),
                    request.getHeader("Origin"),
                    request.getHeader("Referer"),
                    request.getHeader("Cookie"),
                    Collections.list(request.getHeaderNames()).stream()
                            .map(name -> name + "=" + request.getHeader(name))
                            .collect(Collectors.joining(", ")));
        }

        try {
            // Parse Token From Request
            var nullableToken = jwtTokenResolver.parseTokenFromRequest(request);
            // if (nullableToken.isEmpty()) { throw new JwtMissingException(); }
            if (nullableToken.isEmpty()) { filterChain.doFilter(request, response); return; }

            // Extract JWT Payload with Validation (Token 자체의 유효성 검증)
            String tokenString = nullableToken.get();
            log.debug("🔍 Attempting to resolve token - Token length: {}", tokenString.length());
            JwtDto.TokenPayload payload;
            try {
                payload = jwtTokenResolver.resolveToken(tokenString);
                log.debug("✅ Token resolved successfully - Subject: {}, Type: {}", payload.getSubject(), payload.getTokenType());
            } catch (Exception e) {
                log.error("❌ Token resolution failed - Error: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }

            // ATK Validation: isAtk? isValidJti? isBlacklist? (사용 목적에 따른 유효성 검증)
            try {
                jwtTokenValidator.validateAtk(payload);
                log.debug("✅ ATK validation passed - JTI: {}", payload.getJti());
            } catch (Exception e) {
                log.error("❌ ATK validation failed - Error: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }

            // Define UserPrincipal
            UserPrincipal userPrincipal;
            try {
                userPrincipal = userLoadService.loadUserById(Long.valueOf(payload.getSubject()))
                        .orElseThrow(() -> {
                            log.error("❌ User not found - Subject: {}", payload.getSubject());
                            return new JwtInvalidException();
                        });
                log.debug("✅ UserPrincipal loaded - UserId: {}, Username: {}", userPrincipal.getUserId(), userPrincipal.getUsername());
            } catch (NumberFormatException e) {
                log.error("❌ Invalid subject format - Subject: {}", payload.getSubject());
                throw new JwtInvalidException(e);
            }

            // Create Authentication Instance
            Authentication authentication = createAuthentication(userPrincipal);

            // Register Authentication to SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("🟢 JWT authentication successful for user: {}", userPrincipal.getUsername());
        } catch (JwtInvalidException e) {
            log.error("⚠️ JWT authentication failed", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_INVALID);
            return;
        } catch (JwtMissingException e) {
            // /api/** 경로만 필터를 통과하므로 여기서는 정상적인 API 요청만 처리
            String uri = request.getRequestURI();
            log.warn("⚠️ No JWT token found in request - URI: {}, Method: {}", uri, request.getMethod());
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_MISSING);
            return;
        } catch (JwtExpiredException e) {
            log.warn("⚠️ JWT token has expired, checking refresh token for auto-login", e.getMessage());

            // TODO: Refactoring 필요 -> 별도의 Helper Methods 로 분리할 것
            // ATK 만료 시 RTK 확인 및 검증 (자동 로그인 지원)
            try {
                // 1. RTK 존재 여부 확인 (ATK는 없어도 RTK만 있으면 자동 Refresh 가능)
                var nullableRtk = jwtTokenResolver.parseRefreshTokenFromRequest(request);
                if (nullableRtk.isEmpty()) {
                    log.debug("⚪ No refresh token found, cannot auto-refresh");
                    SecurityContextHolder.clearContext();
                    writeErrorResponse(response, ErrorCode.JWT_MISSING);
                    return;
                }
                
                // 2. RTK 파싱 및 검증
                JwtDto.TokenPayload rtkPayload = jwtTokenResolver.resolveToken(nullableRtk.get());
                jwtTokenValidator.validateRtk(rtkPayload);
                
                // 3. RTK가 유효하면 자동 Refresh 처리
                log.info("🟢 Valid refresh token found, performing auto-refresh");
                try {
                    boolean rememberMe = rtkPayload.getRememberMe() != null && rtkPayload.getRememberMe();
                    
                    // TokenService를 통해 자동 Refresh
                    JwtDto.TokenOptionWrapper tokenOption = JwtDto.TokenOptionWrapper.of(request, response, rememberMe);
                    JwtDto.TokenInfo tokenInfo = tokenService.rotateByRtkWithValidation(tokenOption);
                    
                    // 새로 발급된 RTK를 request attribute에 저장 (같은 요청에서 사용하기 위해)
                    request.setAttribute("NEW_REFRESH_TOKEN", tokenInfo.getRefreshToken());
                    
                    // 새로 발급된 ATK를 직접 사용 (쿠키에서 읽지 않음 - 같은 요청에서는 쿠키가 반영되지 않음)
                    String newAccessToken = tokenInfo.getAccessToken();
                    JwtDto.TokenPayload newPayload = jwtTokenResolver.resolveToken(newAccessToken);
                    jwtTokenValidator.validateAtk(newPayload);
                    
                    UserPrincipal userPrincipal = userLoadService.loadUserById(Long.valueOf(newPayload.getSubject()))
                            .orElseThrow(JwtInvalidException::new);
                    
                    Authentication authentication = createAuthentication(userPrincipal);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.info("🟢 Auto-refresh successful, new authentication set for user: {}", userPrincipal.getUsername());
                    filterChain.doFilter(request, response);
                    return;
                } catch (Exception refreshException) {
                    log.error("⚠️ Auto-refresh failed: {}", refreshException.getMessage(), refreshException);
                    SecurityContextHolder.clearContext();
                    writeErrorResponse(response, ErrorCode.JWT_EXPIRED);
                    return;
                }
                
            } catch (Exception rtkException) {
                // RTK 검증 실패 또는 기타 예외
                log.warn("⚠️ Refresh token validation failed: {}", rtkException.getMessage());
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ErrorCode.JWT_EXPIRED);
                return;
            }
        } catch (JwtMalformedException e) {
            log.error("⚠️ JWT token is malformed", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_MALFORMED);
            return;
        } catch (JwtBlacklistException e) {
            log.error("⚠️ JWT token is blacklisted", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_BLACKLIST);
            return;
        } catch (Exception e) {
            log.error("⚠️ Unexpected error during JWT authentication", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Authentication createAuthentication(UserPrincipal userPrincipal) {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(userPrincipal.getRole().name()));

        return new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.setStatus(errorResponse.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}

