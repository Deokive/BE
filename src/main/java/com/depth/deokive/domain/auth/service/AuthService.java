package com.depth.deokive.domain.auth.service;

import com.depth.deokive.domain.auth.dto.AuthDto;
import com.depth.deokive.domain.oauth2.repository.OAuth2AccountRepository;
import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.service.TokenService;
import com.depth.deokive.system.security.jwt.util.JwtTokenResolver;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.depth.deokive.system.security.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenResolver jwtTokenResolver;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OAuth2AccountRepository oAuth2AccountRepository;
    private final TokenService tokenService;
    private final CookieUtils cookieUtils;
    private final EmailService emailService;

    @Transactional
    public UserDto.UserResponse signUp(AuthDto.SignUpRequest request) {
        validateVerifiedEmailForSignUp(request);
        validateUser(request);

        User savedUser = userRepository.save(request.toEntity(passwordEncoder));

        emailService.clearVerifiedForSignup(request.getEmail());

        return UserDto.UserResponse.from(savedUser);
    }

    @Transactional
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request, HttpServletResponse response) {
        User validatedUser = getValidatedLoginUser(request, passwordEncoder);

        JwtDto.TokenOptionWrapper tokenOption
                = JwtDto.TokenOptionWrapper.of(UserPrincipal.from(validatedUser), request.isRememberMe());

        JwtDto.TokenInfo tokenInfo = tokenService.issueTokens(tokenOption);

        // Cookie Setup
        setCookies(response, tokenInfo);

        return AuthDto.LoginResponse.of(
                UserDto.UserResponse.from(validatedUser),
                JwtDto.TokenExpiresInfo.from(tokenInfo)
        );
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = jwtTokenResolver.parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        // 1) 서버 측 토큰(ATK/RTK) 무효화 (예: Redis blacklist)
        tokenService.clearTokensByAtkWithValidation(accessToken, refreshToken);

        // 2) 브라우저 쿠키 제거 (same name, same path, domain 등으로)
        clearCookies(response);
    }

    @Transactional
    public void delete(UserPrincipal userPrincipal, HttpServletRequest request, HttpServletResponse response) {
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenResolver.parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        tokenService.clearTokensByAtkWithValidation(accessToken, refreshToken);

        // Soft Delete 처리
        foundUser.softDelete(AuthDto.SoftDeleteDto.of(foundUser));

        // 연관된 소셜 계정은 Hard Delete (Soft Delete는 Cascade 기대하기 힘드므로, Bulk 연산으로 명시적으로 한번에 지운다.)
        oAuth2AccountRepository.deleteByUserId(foundUser.getId());

        clearCookies(response);
    }

    @Transactional(readOnly = true)
    public AuthDto.ExistResponse checkEmailExist(String email) {
        boolean exists = userRepository.existsByEmail(email);
        return AuthDto.ExistResponse.ofEmail(exists, email);
    }

    @Transactional(readOnly = true)
    public AuthDto.ExistResponse checkUsernameExist(String username) {
        boolean exists = userRepository.existsByUsername(username);
        return AuthDto.ExistResponse.ofUsername(exists, username);
    }

    @Transactional
    public JwtDto.TokenExpiresInfo refreshTokens(
            HttpServletRequest request,
            HttpServletResponse response,
            boolean rememberMe) {

        // 소셜 로그인을 위한 처리 -> 자동 로그인이면 QueryParam으로 입력받는다.
        JwtDto.TokenOptionWrapper tokenOption =
                JwtDto.TokenOptionWrapper.of(request, response, rememberMe);
        JwtDto.TokenInfo tokenInfo = tokenService.rotateByRtkWithValidation(tokenOption);
        return JwtDto.TokenExpiresInfo.from(tokenInfo);
    }

    @Transactional
    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        validateVerifiedEmailForResetPassword(request);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));

        request.encodePassword(passwordEncoder);
        user.resetPassword(request);

        emailService.clearVerifiedForPasswordReset(request.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthDto.LoginResponse socialRetrieve(UserPrincipal userPrincipal, HttpServletRequest request) {
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        JwtDto.TokenExpiresInfo tokenExpiresInfo = tokenService.getTokenExpiresInfo(request);

        return AuthDto.LoginResponse.of(
                UserDto.UserResponse.from(foundUser),
                tokenExpiresInfo
        );
    }


    public boolean isRtkBlacklisted(String refreshToken) {
        return tokenService.isRtkBlacklisted(refreshToken);
    }

    public boolean isAtkBlacklisted(String accessToken) {
        return tokenService.isAtkBlacklisted(accessToken);
    }

    public boolean isTokenActive(HttpServletRequest request) {
        return tokenService.validateTokens(request);
    }

    // Helper Methods
    private void validateUser(AuthDto.SignUpRequest request) {
        boolean isAlreadyUser = userRepository.existsByEmail(request.getEmail());
        if (isAlreadyUser) throw new RestException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
    }

    private User getValidatedLoginUser(AuthDto.LoginRequest request, PasswordEncoder passwordEncoder) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RestException(ErrorCode.AUTH_PASSWORD_NOT_MATCH);
        }

        return user;
    }

    private void clearCookies(HttpServletResponse response) {
        cookieUtils.clearAccessTokenCookie(response);
        cookieUtils.clearRefreshTokenCookie(response);
    }

    private void setCookies(HttpServletResponse response, JwtDto.TokenInfo tokenInfo) {
        cookieUtils.addAccessTokenCookie(response, tokenInfo.getAccessToken(), tokenInfo.getRefreshTokenExpiresAt());
        cookieUtils.addRefreshTokenCookie(response, tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpiresAt());
    }

    private void validateVerifiedEmailForSignUp(AuthDto.SignUpRequest request) {
        if (!emailService.hasVerifiedForSignup(request.getEmail())) {
            throw new RestException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
    }

    private void validateVerifiedEmailForResetPassword(AuthDto.ResetPasswordRequest request) {
        if (!emailService.hasVerifiedForPasswordReset(request.getEmail())) {
            throw new RestException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
    }
}
