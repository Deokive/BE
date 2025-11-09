package com.depth.deokive.domain.oauth2.dto;

import java.util.Map;
import static com.depth.deokive.common.util.MapUtils.*;

public class NaverResponse implements OAuth2Response {
    private final Map<String, Object> attribute;

    public NaverResponse(Map<String, Object> attribute) {
        this.attribute = asStringObjectMap(attribute.get("response"));
    }

    @Override public String getProvider() { return "naver"; }
    @Override public String getProviderId() { return getString(attribute, "id"); }
    @Override public String getEmail() { return getString(attribute, "email"); }
    @Override public String getNickname() { return getString(attribute, "name"); }
}
