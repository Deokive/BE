package com.depth.deokive.domain.oauth2.dto;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GoogleResponse implements OAuth2Response {
    private final Map<String, Object> attributes;

    @Override public String getProvider() { return "google"; }
    @Override public String getProviderId() { return attributes.get("sub").toString(); }
    @Override public String getEmail() { return attributes.get("email").toString(); }
    @Override public String getNickname() { return attributes.get("name").toString(); }

    @Override
    public Boolean isEmailVerified() {
        Object verified = attributes.get("email_verified");
        if (verified == null) return null;
        return Boolean.parseBoolean(verified.toString());
    }
}
