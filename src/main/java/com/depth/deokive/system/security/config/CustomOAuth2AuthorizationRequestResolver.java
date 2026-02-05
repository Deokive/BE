package com.depth.deokive.system.security.config;

import com.depth.deokive.system.security.util.FrontUrlResolver;
import com.depth.deokive.system.security.util.PropertiesParserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 인증 요청 시 State 파라미터에 프론트엔드 URL을 포함시키는 커스텀 리졸버
 * 이를 통해 OAuth2 콜백에서 원래 요청한 프론트엔드 URL을 복원할 수 있습니다.
 */
@Slf4j
@Component
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String OAUTH2_AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";

    private final OAuth2AuthorizationRequestResolver defaultResolver;
    
    @Value("${app.front-redirect-uri}")
    private String frontRedirectUriConfig;

    public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, 
                "/oauth2/authorization"
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return resolve(request, null);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        // OAuth2 인증 요청 경로인지 확인
        String requestPath = request.getRequestURI();
        if (requestPath == null || !requestPath.startsWith(OAUTH2_AUTHORIZATION_PATH_PREFIX)) {
            return defaultResolver.resolve(request, clientRegistrationId);
        }

        // registrationId 추출
        if (clientRegistrationId == null) {
            clientRegistrationId = extractRegistrationId(request);
        }

        if (clientRegistrationId == null) {
            return defaultResolver.resolve(request, null);
        }

        // 기본 리졸버로 OAuth2AuthorizationRequest 생성
        OAuth2AuthorizationRequest defaultRequest = defaultResolver.resolve(request, clientRegistrationId);
        if (defaultRequest == null) {
            return null;
        }

        // 요청 커스터마이징 시작
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(defaultRequest);

        // 프론트엔드 URL 추출 (우선순위: 쿼리 파라미터 > Origin/Referer 헤더)
        List<String> allowedRedirectUris = PropertiesParserUtils.propertiesParser(frontRedirectUriConfig);
        String frontendUrl = extractFrontendUrl(request, allowedRedirectUris);
        
        log.info("🔍 [CustomOAuth2AuthorizationRequestResolver] OAuth2 인증 요청 처리");

        // State 파라미터에 프론트엔드 URL 포함
        String originalState = defaultRequest.getState();
        String stateWithFrontendUrl = encodeStateWithFrontendUrl(originalState, frontendUrl);
        builder.state(stateWithFrontendUrl);

        Map<String, Object> additionalParameters = new HashMap<>(defaultRequest.getAdditionalParameters());

        if ("naver".equalsIgnoreCase(clientRegistrationId)) {
            // 네이버 재인증 강제 로직 추가
            additionalParameters.put("auth_type", "reauthenticate");
            log.info("✅ [Naver] auth_type=reauthenticate 파라미터 추가 완료");
        } else if ("google".equalsIgnoreCase(clientRegistrationId)) {
            // 구글: 계정 선택 화면 강제 (자동 로그인 방지)
            additionalParameters.put("prompt", "select_account");
            log.info("✅ [Google] prompt=select_account 파라미터 추가");
        }

        builder.additionalParameters(additionalParameters);
        OAuth2AuthorizationRequest customizedRequest = builder.build();

        log.info("✅ [Final Request] State: {}, Auth URL: {}", customizedRequest.getState(), customizedRequest.getAuthorizationRequestUri());
        
        return customizedRequest;
    }

    private String extractRegistrationId(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        if (requestPath == null || !requestPath.startsWith(OAUTH2_AUTHORIZATION_PATH_PREFIX)) {
            return null;
        }
        String registrationId = requestPath.substring(OAUTH2_AUTHORIZATION_PATH_PREFIX.length());
        return registrationId.isEmpty() ? null : registrationId;
    }

    /**
     * 프론트엔드 URL 추출 (우선순위: 쿼리 파라미터 > Origin/Referer 헤더)
     */
    private String extractFrontendUrl(HttpServletRequest request, List<String> allowedRedirectUris) {
        // 1. 쿼리 파라미터에서 redirect_uri 확인 (프론트엔드에서 명시적으로 지정한 경우)
        String redirectUriParam = request.getParameter("redirect_uri");
        if (redirectUriParam != null && !redirectUriParam.isBlank()) {
            // 허용된 URL 목록과 매칭 확인
            String matched = findMatchingUrl(redirectUriParam, allowedRedirectUris);
            if (matched != null) {
                log.info("   - 쿼리 파라미터 redirect_uri에서 프론트엔드 URL 추출: {}", matched);
                return matched;
            } else {
                log.warn("⚠️ 쿼리 파라미터 redirect_uri가 허용 목록에 없음: {}", redirectUriParam);
            }
        }
        
        // 2. Origin/Referer 헤더에서 추출 (기존 방식)
        return FrontUrlResolver.resolveUrl(request, allowedRedirectUris, allowedRedirectUris.get(0));
    }
    
    /**
     * URL 매칭 (FrontUrlResolver의 findMatchingUrl과 동일한 로직)
     */
    private String findMatchingUrl(String candidateUrl, List<String> allowedUrls) {
        if (candidateUrl == null || candidateUrl.isBlank() || allowedUrls == null || allowedUrls.isEmpty()) {
            return null;
        }
        
        // 정확히 일치하는 경우
        String normalizedCandidate = normalizeUrl(candidateUrl);
        for (String allowedUrl : allowedUrls) {
            String normalizedAllowed = normalizeUrl(allowedUrl);
            if (normalizedCandidate.equals(normalizedAllowed)) {
                return allowedUrl;
            }
        }
        
        // 도메인만 비교 (포트 제외)
        String candidateDomain = extractDomain(candidateUrl);
        if (candidateDomain != null) {
            for (String allowedUrl : allowedUrls) {
                String allowedDomain = extractDomain(allowedUrl);
                if (candidateDomain.equals(allowedDomain)) {
                    return allowedUrl;
                }
            }
        }
        
        return null;
    }
    
    private String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
    
    private String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            String domain = scheme + "://" + host;
            if (uri.getPort() != -1) {
                domain += ":" + uri.getPort();
            }
            return domain;
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /**
     * State 파라미터에 프론트엔드 URL을 포함하여 인코딩
     * 형식: base64(originalState|frontendUrl)
     */
    private String encodeStateWithFrontendUrl(String originalState, String frontendUrl) {
        String combined = originalState + "|" + frontendUrl;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined.getBytes());
    }
}
