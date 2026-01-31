package com.depth.deokive.domain.oauth2.service;

import com.depth.deokive.domain.oauth2.dto.*;
import com.depth.deokive.domain.oauth2.entity.CustomOAuth2User;
import com.depth.deokive.domain.oauth2.entity.OAuth2Account;
import com.depth.deokive.domain.oauth2.entity.enums.ProviderType;
import com.depth.deokive.domain.oauth2.repository.OAuth2AccountRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.security.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final OAuth2AccountRepository oAuth2AccountRepository;
    private final HmacUtil hmacUtil;

    @Transactional
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // Request 기반으로 OAuth2User 정의
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // OAuth2User + Request 기반으로 Response 생성
        OAuth2Response oAuth2Response = getOAuth2Response(userRequest, oAuth2User);
        log.info("🟢 OAuth2 User nickname: {}", oAuth2Response.getNickname());

        // Response 할 DTO
        final OAuth2UserDto oAuth2UserDto = OAuth2UserDto.of(Role.USER, oAuth2Response, hmacUtil);

        User user = null;

        log.info("1️⃣. Check Whether if the user is already SocialUser");
        // DB에는 해시된 providerId가 저장되어 있으므로, 비교 전에 해시 처리 필요
        String hashedProviderId = hmacUtil.hmacSha256Base64(oAuth2Response.getProviderId());
        ProviderType providerType = ProviderType.from(oAuth2Response.getProvider());

        OAuth2Account oAuth2Account = oAuth2AccountRepository.findByProviderIdAndProviderType(
                hashedProviderId,
                providerType
        ).orElse(null);

        if (oAuth2Account != null) {
            log.info("🟢 Find existing OAuth2User");
            return new CustomOAuth2User(OAuth2UserDto.from(oAuth2Account.getUser()));
        }

        log.info("2️⃣. Not existing OAuth2User. Checking isEmailVerified field");
        if (oAuth2UserDto.isEmailVerified()) {
            // oAuth2UserDto 내부에서 Provider별 isEmailVerified 검사함
            log.info("3️⃣. Email is Verified. Check whether if the user is existed");
            user = userRepository.findByEmail(oAuth2UserDto.getEmail()).orElse(null);
        } else {
            throw new OAuth2AuthenticationException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED.getMessage());
        }

        if (user != null) {
            log.info("4️⃣. The same user is exist. Connecting a new SocialAccount with a existed user");
            oAuth2AccountRepository.save(OAuth2Account.create(providerType, hashedProviderId, user));
            return new CustomOAuth2User(OAuth2UserDto.from(user));
        }

        log.info("5️⃣. The same user is not exist. Considering you are new social user");
        user = userRepository.save(oAuth2UserDto.toUser());
        oAuth2AccountRepository.save(OAuth2Account.create(providerType, hashedProviderId, user));

        return new CustomOAuth2User(OAuth2UserDto.from(user));
    }

    private static OAuth2Response getOAuth2Response(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response oAuth2Response = null;

        switch (registrationId) {
            case "google" -> oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
            case "naver" -> oAuth2Response = new NaverResponse(oAuth2User.getAttributes());
            case "kakao" -> oAuth2Response = new KaKaoResponse(oAuth2User.getAttributes());
            default -> throw new OAuth2AuthenticationException(ErrorCode.OAUTH_BAD_REQUEST.getMessage());
        }
        return oAuth2Response;
    }
}
