package com.depth.deokive.system.security.jwt.util;

import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.dto.TokenType;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final SecretKey secretKey;

    @Value("${jwt.access-token-expiration-minutes}")
    private int accessTokenExpirationMinutes;

    @Value("${jwt.refresh-token-expiration-weeks}")
    private int refreshTokenExpirationWeeks;

    public JwtDto.TokenData createRefreshToken(UserPrincipal userPrincipal, String refreshUuid) {
        String jti = UUID.randomUUID().toString();
        LocalDateTime exp = LocalDateTime.now().plusWeeks(refreshTokenExpirationWeeks);

        String token = Jwts.builder()
                .subject(getSubject(userPrincipal))
                .claim("refreshUuid", refreshUuid)
                .claim("type", TokenType.REFRESH.name())
                .id(jti)
                .issuedAt(new Date())
                .expiration(Date.from(exp.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey)
                .compact();

        return JwtDto.TokenData.builder()
                .token(token)
                .expiredAt(exp)
                .jti(jti)
                .build();
    }

    public JwtDto.TokenData createAccessToken(UserPrincipal userPrincipal, String refreshUuid) {
        String jti = UUID.randomUUID().toString();
        LocalDateTime exp = LocalDateTime.now().plusMinutes(accessTokenExpirationMinutes);

        String token = Jwts.builder()
                .subject(getSubject(userPrincipal))
                .claim("role", userPrincipal.getRole().name())
                .claim("refreshUuid", refreshUuid)
                .claim("type", TokenType.ACCESS.name())
                .id(jti)
                .issuedAt(new Date())
                .expiration(Date.from(exp.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey)
                .compact();

        return JwtDto.TokenData.builder()
                .token(token)
                .expiredAt(exp)
                .jti(jti)
                .build();
    }

    public JwtDto.TokenPair createTokenPair(UserPrincipal userPrincipal) {
        String refreshUuid = java.util.UUID.randomUUID().toString();
        JwtDto.TokenData accessToken = createAccessToken(userPrincipal, refreshUuid);
        JwtDto.TokenData refreshToken = createRefreshToken(userPrincipal, refreshUuid);

        return JwtDto.TokenPair.of(refreshToken, accessToken);
    }

    private String getSubject(UserPrincipal userPrincipal) {
        // OAuth2 사용자의 경우 userId가 null이므로 email을 subject로 사용 (username이 email임)
        return userPrincipal.getUserId() != null
                ? userPrincipal.getUserId().toString()
                : userPrincipal.getUsername();
    }
}
