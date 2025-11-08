package com.depth.deokive.domain.oauth2.dto;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.security.util.HmacUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class OAuth2UserDto {
    private Long userId;
    private Role role;
    private String nickname;
    private String email;

    public static OAuth2UserDto of(Role role, OAuth2Response oAuth2Response, HmacUtil hmacUtil) {
        return OAuth2UserDto.builder()
                .userId(null) // OAuth2Response 엔 userId가 없음 -> user 등록 하고 해당 DTO update 해야 함.
                .role(role)
                .nickname(suggestNickname(oAuth2Response, hmacUtil))
                .email(oAuth2Response.getEmail())
                .build();
    }

    public static OAuth2UserDto from(User user) {
        return OAuth2UserDto.builder()
                .userId(user.getId()) // user 등록된 이후 DTO Update 됨
                .role(user.getRole())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .build();
    }

    public User toUser() {
        return User.builder()
                .role(this.role)
                .nickname(this.nickname)
                .email(this.email)
                .userType(UserType.OAUTH2)
                .build();
    }

    private static String suggestNickname(OAuth2Response oAuth2Response, HmacUtil hmacUtil) {
        // 닉네임 null/blank 시 대체.
        String nickname = oAuth2Response.getNickname();

        if (!nickname.isEmpty()) return nickname;

        String hashedId = hmacUtil.hmacSha256Base64(oAuth2Response.getProviderId());

        return "user_" + oAuth2Response.getProvider() + "_" + hashedId;
    }
}
