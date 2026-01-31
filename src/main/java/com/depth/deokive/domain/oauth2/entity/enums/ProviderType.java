package com.depth.deokive.domain.oauth2.entity.enums;

import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Arrays;

@Getter
public enum ProviderType {

    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver");

    private final String provider;

    ProviderType(String provider) {
        this.provider = provider;
    }

    public static ProviderType from(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            throw new OAuth2AuthenticationException("OAuth2 provider is missing");
        }

        return Arrays.stream(values())
                .filter(p -> p.provider.equalsIgnoreCase(providerType))
                .findFirst()
                .orElseThrow(() ->
                        new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + providerType)
                );
    }
}
