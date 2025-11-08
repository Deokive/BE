package com.depth.deokive.domain.oauth2.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.warn("OAuth2 로그인 실패: {}", exception.getMessage());

        String code = "auth_failed";

        if (exception instanceof OAuth2AuthenticationException oae
                && oae.getError() != null
                && oae.getError().getErrorCode() != null) {
            code = oae.getError().getErrorCode();
        }

        String target = frontBaseUrl + "/login?error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);

        response.sendRedirect(target);
    }
}
