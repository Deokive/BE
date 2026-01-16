package com.depth.deokive.system.security.jwt.config;

import com.depth.deokive.system.security.config.RequestMatcherHolder;
import com.depth.deokive.system.security.jwt.repository.TokenRedisRepository;
import com.depth.deokive.system.security.jwt.service.TokenService;
import com.depth.deokive.system.security.jwt.util.JwtTokenProvider;
import com.depth.deokive.system.security.jwt.util.JwtTokenResolver;
import com.depth.deokive.system.security.jwt.util.JwtTokenValidator;
import com.depth.deokive.system.security.util.CookieUtils;
import com.depth.deokive.system.security.util.UserLoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class JwtConfig {
    private final SecretKey secretKey;

    public JwtConfig(
            @Value("${jwt.secret:${JWT_SECRET_KEY:}}") String secret
    ){
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("⚠️ Jwt Secret 이 존재하지 않습니다.");
        }

        // Decode as Base64 if possible, otherwise use raw bytes
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) { // HS256 requires at least 256-bit (32-byte) key strength
            throw new IllegalStateException("⚠️ Jwt Secret 은 32바이트(256비트) 이상이어야 합니다.");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        
        // 시크릿 키 일관성 확인을 위한 로깅 (보안을 위해 해시값만 로깅)
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(keyBytes);
            String hashHex = java.util.HexFormat.of().formatHex(hash);
            log.info("🔑 JWT Secret Key initialized - Hash: {} (Length: {} bytes)", hashHex.substring(0, 16) + "...", keyBytes.length);
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("⚠️ Failed to generate secret key hash for logging: {}", e.getMessage());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(secretKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenValidator jwtTokenValidator(TokenRedisRepository tokenRedisRepository) {
        return new JwtTokenValidator(tokenRedisRepository, secretKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenResolver jwtTokenResolver(
            JwtTokenValidator jwtTokenValidator,
            CookieUtils cookieUtils
    ) {
        return new JwtTokenResolver(jwtTokenValidator, cookieUtils);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter JwtAuthenticationFilter(
            JwtTokenResolver jwtTokenResolver,
            UserLoadService userLoadService,
            JwtTokenValidator jwtTokenValidator,
            RequestMatcherHolder requestMatcherHolder,
            ObjectMapper objectMapper,
            TokenService tokenService
    ) {
        return new JwtAuthenticationFilter(jwtTokenResolver, userLoadService, jwtTokenValidator, requestMatcherHolder, objectMapper, tokenService);
    }
}
