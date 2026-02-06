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
import com.depth.deokive.system.security.util.FrontUrlResolver;
import com.depth.deokive.system.security.util.PropertiesParserUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
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

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    @Value("${app.backend-base-url:}")
    private String backendBaseUrlConfig;

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

    /**
     * Provider별 로그아웃 URL 생성
     */
    @Transactional(readOnly = true)
    public AuthDto.ProviderLogoutUrlResponse getProviderLogoutUrls(HttpServletRequest request) {
        // 1. Kakao URL 생성
        ClientRegistration kakaoRegistration = clientRegistrationRepository.findByRegistrationId("kakao");
        String kakaoClientId = kakaoRegistration.getClientId();

        // 2. 백엔드 콜백 URL 생성
        String baseUrl;
        // 2-1. 설정 파일에 백엔드 주소가 명시되어 있다면 그걸 최우선으로 사용 (배포 환경)
        if (StringUtils.hasText(backendBaseUrlConfig)) {
            baseUrl = backendBaseUrlConfig;
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        // 2-2. 없다면(로컬 개발 환경), 기존처럼 Request 정보를 사용
        } else {
            baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                    .replacePath(null)
                    .build()
                    .toUriString();
        }

        String kakaoLogoutRedirectUri = baseUrl + "/api/v1/auth/logout/callback";

        // 3. 현재 요청을 보낸 프론트엔드 URL 식별 (state 생성을 위해)
        List<String> allowedBaseUrls = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);
        // 기본값은 운영 도메인(또는 첫번째)이지만, 요청 헤더(Origin/Referer)가 있으면 그걸 우선함
        String currentFrontUrl = FrontUrlResolver.resolveUrl(request, allowedBaseUrls,
                allowedBaseUrls.isEmpty() ? "http://localhost:5173" : allowedBaseUrls.getFirst());

        // 4. 프론트엔드 URL을 Base64로 인코딩하여 state 파라미터 생성
        String state = Base64.getEncoder().encodeToString(currentFrontUrl.getBytes(StandardCharsets.UTF_8));

        // 5. 카카오 URL 조립 (state 포함)
        String kakaoUrl = "https://kauth.kakao.com/oauth/logout"
                + "?client_id=" + kakaoClientId
                + "&logout_redirect_uri=" + kakaoLogoutRedirectUri
                + "&state=" + state;

        return AuthDto.ProviderLogoutUrlResponse.builder()
                .kakaoLogoutUrl(kakaoUrl)
                .build();
    }

    /**
     * 카카오 로그아웃 Callback 처리 -> 프론트엔드로 최종 리다이렉트
     */
    public void handleLogoutCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // 프론트엔드 URL 파싱 (여러 개일 경우 첫 번째 사용)
        List<String> allowedBaseUrls = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);

        // 1. 우선순위: state 파라미터 확인 (카카오가 돌려준 값)
        String state = request.getParameter("state");
        String targetBaseUrl = null;

        if (StringUtils.hasText(state)) {
            try {
                String decodedUrl = new String(Base64.getDecoder().decode(state), StandardCharsets.UTF_8);

                // 보안 검증: 디코딩된 URL이 우리 허용 목록에 있는가?
                boolean isAllowed = allowedBaseUrls.stream().anyMatch(allowed ->
                        decodedUrl.equals(allowed) || decodedUrl.startsWith(allowed));

                if (isAllowed) {
                    targetBaseUrl = decodedUrl;
                    log.info("✅ [Logout Callback] Restore redirect URL from state: {}", targetBaseUrl);
                } else {
                    log.warn("⚠️ [Logout Callback] Invalid redirect URL in state: {}", decodedUrl);
                }
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ [Logout Callback] Invalid Base64 state: {}", state);
            }
        }

        // 2. state가 없거나 유효하지 않으면 기존 로직(환경변수 기반 추론)으로 Fallback (문제 상황 방지용)
        if (targetBaseUrl == null) {
            // 현재 백엔드가 로컬 환경인지 배포 환경인지 판단
            boolean isLocalBackend = backendBaseUrlConfig.contains("localhost") || backendBaseUrlConfig.contains("127.0.0.1");

            if (isLocalBackend) {
                // 로컬 환경 : localhost를 우선적으로 찾음
                targetBaseUrl = allowedBaseUrls.stream()
                        .filter(url -> url.contains("localhost") || url.contains("127.0.0.1"))
                        .findFirst()
                        .orElse(allowedBaseUrls.isEmpty() ? "http://localhost:5173" : allowedBaseUrls.get(0));
            } else {
                // 배포 환경 : localhost가 아닌 운영 도메인을 우선적으로 찾음
                targetBaseUrl = allowedBaseUrls.stream()
                        .filter(url -> !url.contains("localhost") && !url.contains("127.0.0.1"))
                        .findFirst()
                        .orElse(allowedBaseUrls.isEmpty() ? "http://localhost:5173" : allowedBaseUrls.get(0));
            }
        }

        // 최종적으로 FE의 /logged-out 페이지로 이동
        String targetUrl = targetBaseUrl + "/logged-out";

        log.info("✅ [Logout Callback] Redirecting to Frontend: {}", targetUrl);

        response.sendRedirect(targetUrl);
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
