package com.depth.deokive.system.security.jwt.util;

import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.dto.TokenType;
import com.depth.deokive.system.security.jwt.exception.JwtBlacklistException;
import com.depth.deokive.system.security.jwt.exception.JwtExpiredException;
import com.depth.deokive.system.security.jwt.exception.JwtInvalidException;
import com.depth.deokive.system.security.jwt.exception.JwtMalformedException;
import com.depth.deokive.system.security.jwt.repository.TokenRedisRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenValidator {
    private final TokenRedisRepository tokenRedisRepository;
    private final SecretKey secretKey;

    public void validateRtk(JwtDto.TokenPayload payload) {
        if (payload.getTokenType() != TokenType.REFRESH) throw new JwtInvalidException();
        if (payload.getSubject() == null || payload.getSubject().isEmpty()) throw new JwtInvalidException();
        if (payload.getRefreshUuid() == null || payload.getRefreshUuid().isEmpty()) throw new JwtInvalidException();

        String submittedUuid = payload.getRefreshUuid();
        String allowedRtk = tokenRedisRepository.getAllowedRtk(payload.getSubject());

        if (allowedRtk == null || !allowedRtk.equals(submittedUuid)) throw new JwtInvalidException();
        if (tokenRedisRepository.isRtkBlacklisted(submittedUuid)) throw new JwtBlacklistException();
    }

    public void validateAtk(JwtDto.TokenPayload payload) {
        if (payload.getTokenType() != TokenType.ACCESS) throw new JwtInvalidException();
        if (payload.getJti() == null) throw new JwtInvalidException();
        if (tokenRedisRepository.isAtkBlacklisted(payload.getJti())) throw new JwtBlacklistException();
    }

    public Jws<Claims> parseClaimsWithValidation(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        } catch (SecurityException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new JwtInvalidException(e);
        } catch (MalformedJwtException e) {
            throw new JwtMalformedException(e);
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException(e);
        }
    }
}
