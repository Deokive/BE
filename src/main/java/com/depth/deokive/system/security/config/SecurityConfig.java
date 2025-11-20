package com.depth.deokive.system.security.config;

import com.depth.deokive.domain.oauth2.handler.CustomFailureHandler;
import com.depth.deokive.domain.oauth2.handler.CustomSuccessHandler;
import com.depth.deokive.domain.oauth2.service.CustomOAuth2UserService;
import com.depth.deokive.system.security.jwt.config.JwtAuthenticationFilter;
import com.depth.deokive.system.security.util.OriginUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

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
                    oauth2.loginPage(frontBaseUrl + "/login"); // ✅ 기본 로그인 페이지 비활성화 + 프론트로 유도
                    oauth2.userInfoEndpoint(user -> user.userService(customOAuth2UserService));
                    oauth2.successHandler(customSuccessHandler);
                    oauth2.failureHandler(customFailureHandler);
                })
                .authorizeHttpRequests((auth) -> auth
                        // 1. 비인증 경로들 (RequestMatcherHolder에서 관리)
                        .requestMatchers(requestMatcherHolder.getRequestMatchersByMinRole(null)).permitAll()
                        // 2. /api/v1/**로 시작하는 경로 중 permitAll에 없는 것들은 인증 필요
                        .requestMatchers(requestMatcherHolder.getApiRequestMatcher()).authenticated()
                        // 3. 그 외 모든 요청 차단
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.error("⚠️ Access Denied - 403 Forbidden. RequestURI: {}", request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        }))
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = OriginUtils.originListParser(allowedOrigins);

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
