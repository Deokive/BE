package com.depth.deokive.domain.oauth2.dto;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.depth.deokive.common.util.MapUtils.*;

@RequiredArgsConstructor
public class KaKaoResponse implements OAuth2Response {
    private final Map<String, Object> attributes;

    @Override public String getProvider() { return "kakao"; }
    @Override public String getProviderId() {
        Object id = attributes.get("id");
        return (id == null) ? null : String.valueOf(id);
    }

    @Override
    public String getEmail() {
        Map<String, Object> acc = getMap(attributes, "kakao_account");
        return getString(acc, "email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> acc = getMap(attributes, "kakao_account");
        Map<String, Object> profile = getMap(acc, "profile");
        return getString(profile, "nickname");
    }

    @Override
    public Boolean isEmailVerified() {
        Map<String, Object> acc = getMap(attributes, "kakao_account");
        Object verified = acc.get("is_email_verified");
        if (verified == null) return null;
        return Boolean.parseBoolean(verified.toString());
    }

    @Override
    public Boolean isEmailValid() {
        Map<String, Object> acc = getMap(attributes, "kakao_account");
        Object valid = acc.get("is_email_valid");
        if (valid == null) return null;
        return Boolean.parseBoolean(valid.toString());
    }
}
