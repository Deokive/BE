package com.depth.deokive.system.security.jwt.config;

import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.security.config.RequestMatcherHolder;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.exception.*;
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
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenResolver jwtTokenResolver;
    private final UserLoadService userLoadService;
    private final JwtTokenValidator jwtTokenValidator;
    private final RequestMatcherHolder requestMatcherHolder;
    private final ObjectMapper objectMapper;


    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return requestMatcherHolder.getRequestMatchersByMinRole(null).matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Parse Token From Request
            var nullableToken = jwtTokenResolver.parseTokenFromRequest(request);
            if (nullableToken.isEmpty()) { filterChain.doFilter(request, response); return; }

            // Extract JWT Payload with Validation (Token 자체의 유효성 검증)
            JwtDto.TokenPayload payload = jwtTokenResolver.resolveToken(nullableToken.get());

            // ATK Validation: isAtk? isValidJti? isBlacklist? (사용 목적에 따른 유효성 검증)
            jwtTokenValidator.validateAtk(payload);

            // Define UserPrincipal
            UserPrincipal userPrincipal = userLoadService.loadUserById(Long.valueOf(payload.getSubject()))
                    .orElseThrow(JwtInvalidException::new);

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
            log.debug("⚪ No JWT token found in request");
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_MISSING);
            return;
        } catch (JwtExpiredException e) {
            log.warn("⚠️ JWT token has expired", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_EXPIRED);
            return;
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
