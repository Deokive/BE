package com.depth.deokive.domain.oauth2.dto;

public interface OAuth2Response {
    String getProvider();
    String getProviderId();
    String getEmail();
    String getNickname();
    default Boolean isEmailVerified() { return null; } // Naver의 경우 Null
    default Boolean isEmailValid() { return null; } // Naver, Google의 경우 Null
}
