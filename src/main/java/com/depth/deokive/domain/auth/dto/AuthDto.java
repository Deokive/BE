package com.depth.deokive.domain.auth.dto;

import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class AuthDto {
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Schema(description = "회원가입 요청 DTO")
    public static class SignUpRequest {
        @NotBlank(message = "이메일 형식으로 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Schema(description = "이메일", example = "user@email.com")
        private String email;

        @NotBlank(message = "특수문자를 제외한 2~10자리의 닉네임을 입력해주세요.")
        @Pattern(regexp = "^[ㄱ-ㅎ가-힣a-zA-Z0-9-_]{2,10}$", message = "닉네임 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 닉네임", example = "user nickname")
        private String nickname;

        @NotBlank(message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$", message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;

        public User toEntity(PasswordEncoder encoder) {
            return User.builder()
                    .email(email)
                    .nickname(nickname)
                    .username(usernameGenerator(email))
                    .password(encoder.encode(password))
                    .role(Role.USER)
                    .isEmailVerified(true)
                    .build();
        }
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Data
    @Schema(description = "로그인 요청 DTO")
    public static class LoginRequest {
        @NotBlank(message = "이메일 형식으로 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Schema(description = "이메일", example = "user@email.com")
        private String email;

        @NotBlank(message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$", message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;

        @Schema(description = "자동 로그인", example = "true")
        @Builder.Default
        private boolean rememberMe = false;
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    @Schema(description = "로그인 응답 DTO")
    public static class LoginResponse {
        @Schema(description = "로그인한 사용자 정보", implementation = UserDto.UserResponse.class)
        private UserDto.UserResponse user;

        @Schema(description = "발급된 토큰 만료시간 정보", implementation = JwtDto.TokenExpiresInfo.class)
        private JwtDto.TokenExpiresInfo tokenExpiresInfo;

        public static LoginResponse of(UserDto.UserResponse user, JwtDto.TokenExpiresInfo tokenExpiresInfo) {
            return LoginResponse.builder()
                    .user(user)
                    .tokenExpiresInfo(tokenExpiresInfo)
                    .build();
        }
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    @Schema(description = "비밀번호 재설정 요청 DTO")
    public static class ResetPasswordRequest {
        @NotBlank(message = "이메일 형식으로 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Schema(description = "이메일", example = "user@email.com")
        private String email;

        @NotBlank(message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$", message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;

        public void encodePassword(PasswordEncoder passwordEncoder) {
            this.password = passwordEncoder.encode(this.password);
        }
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "중복 확인 응답 DTO")
    public static class ExistResponse {
        @Builder.Default
        @JsonProperty("isExist")
        @Schema(description = "중복 여부 (true: 이미 존재함/사용불가, false: 사용가능)", example = "false")
        private Boolean exist = false;

        @Schema(description = "확인 대상 값 (이메일/닉네임/아이디)", example = "user123@example.com, nickname123, user_id123")
        private String value;

        public static ExistResponse of(boolean exist, String value) {
            return ExistResponse.builder()
                    .exist(exist)
                    .value(value)
                    .build();
        }

        public static ExistResponse ofEmail(boolean exist, String email) { return of(exist, email);}
        public static ExistResponse ofUsername(boolean exist, String username) { return of(exist, username); }
        public static ExistResponse ofNickname(boolean exist, String nickname) { return of(exist, nickname); }
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SoftDeleteDto {
        private String username;
        private String nickname;
        private String email;
        private String password;

        public static AuthDto.SoftDeleteDto of(User user) {
            return AuthDto.SoftDeleteDto.builder()
                    .username(generateBase64(user.getId().toString(), user.getUsername()))
                    .nickname("(알 수 없음)")
                    .email(generateBase64(user.getId().toString(), user.getEmail()))
                    .password("DELETE")
                    .build();
        }
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "이메일 인증 요청 DTO")
    public static class VerifyEmailRequest {
        @NotBlank @Email
        @Schema(description = "이메일", example = "user@email.com")
        private String email;

        @NotBlank
        @Schema(description = "인증 코드", example = "123456")
        @Pattern(regexp = "^[0-9]{6}$")
        private String code;

        private EmailPurpose purpose; // SIGNUP 또는 PASSWORD_RESET
    }

    // Helper Methods
    private static String usernameGenerator(String email){
        return email + "_" + UUID.randomUUID().toString();
    }
    private static String generateBase64(String s1, String s2) {
        String combined = s1 + ":" + s2;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Provider 로그아웃 URL 응답 DTO")
    public static class ProviderLogoutUrlResponse {
        @Schema(description = "카카오 로그아웃 URL (리다이렉트 필요)", example = "https://kauth.kakao.com/oauth/logout?client_id=...&logout_redirect_uri=...")
        private String kakaoLogoutUrl;
    }
}
