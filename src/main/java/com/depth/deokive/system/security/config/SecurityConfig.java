package com.depth.deokive.system.security.config;

import com.depth.deokive.domain.oauth2.handler.CustomFailureHandler;
import com.depth.deokive.domain.oauth2.handler.CustomSuccessHandler;
import com.depth.deokive.domain.oauth2.service.CustomOAuth2UserService;
import com.depth.deokive.system.exception.dto.ErrorResponse;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.security.jwt.config.JwtAuthenticationFilter;
import com.depth.deokive.system.security.util.PropertiesParserUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomSuccessHandler customSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestMatcherHolder requestMatcherHolder;
    private final CustomFailureHandler customFailureHandler;
    private final CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver;

    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    @Value("${app.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> {
                    // front-base-url의 첫 번째 값을 기본값으로 사용 (동적 처리는 CustomFailureHandler에서 수행)
                    List<String> allowedBaseUrls = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);
                    String defaultBaseUrl = allowedBaseUrls.isEmpty() ? "http://localhost:5173" : allowedBaseUrls.get(0);
                    oauth2.loginPage(defaultBaseUrl + "/login"); // ✅ 기본 로그인 페이지 비활성화 + 프론트로 유도
                    oauth2.authorizationEndpoint(authorization -> 
                        authorization.authorizationRequestResolver(customOAuth2AuthorizationRequestResolver)
                    );
                    oauth2.userInfoEndpoint(user -> user.userService(customOAuth2UserService));
                    oauth2.successHandler(customSuccessHandler);
                    oauth2.failureHandler(customFailureHandler);
                })
                .authorizeHttpRequests((auth) -> auth
                        // 0. SSE 엔드포인트의 ASYNC dispatch만 permitAll (타임아웃/완료 처리)
                        // - ASYNC는 이미 인증된 REQUEST에서 파생되므로 안전
                        // - SSE 경로에만 한정하여 최소 권한 원칙 적용
                        .requestMatchers(request ->
                            request.getDispatcherType() == DispatcherType.ASYNC
                            && request.getRequestURI().startsWith("/api/v1/sse/")
                        ).permitAll()
                        // 1. 비인증 경로들 (RequestMatcherHolder에서 관리)
                        .requestMatchers(requestMatcherHolder.getRequestMatchersByMinRole(null)).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/diary/{diaryId}",
                                "/api/v1/events/{eventId}",
                                "/api/v1/events/monthly/{archiveId}",
                                "/api/v1/archives/{archiveId}",
                                "/api/v1/gallery/{archiveId}",
                                "/api/v1/tickets/{ticketId}",
                                "/api/v1/tickets/book/{archiveId}",
                                "/api/v1/repost/{archiveId}",
                                "/api/v1/diary/book/{archiveId}",
                                "/api/v1/posts/{postId}",
                                "/api/v1/posts/{postId}/comments"
                                ).permitAll()
                        // .requestMatchers(requestMatcherHolder.getRequestMatchersForVisibilityByMinRole(null)).permitAll()
                        // 2. /api/v1/**로 시작하는 경로 중 permitAll에 없는 것들은 인증 필요
                        .requestMatchers(requestMatcherHolder.getApiRequestMatcher()).authenticated()
                        // 3. 그 외 모든 요청 차단
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> {
                            // log.error("⚠️ Access Denied - 403 Forbidden. RequestURI: {}", request.getRequestURI());
                            writeErrorResponse(response, ErrorCode.JWT_MISSING);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // log.error("⚠️ Access Denied - 403 Forbidden. RequestURI: {}", request.getRequestURI());
                            writeErrorResponse(response, ErrorCode.AUTH_FORBIDDEN);
                        }))
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        new ObjectMapper().writeValue(response.getWriter(), errorResponse);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = PropertiesParserUtils.propertiesParser(allowedOrigins);

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Collections.singletonList("Set-Cookie"));
        configuration.setExposedHeaders(Collections.singletonList("Authorization"));

        log.info("🌐 CORS 허용 Origin: {}, AllowCredentials: {}", origins, configuration.getAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
