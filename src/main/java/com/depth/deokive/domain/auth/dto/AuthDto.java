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

        @NotBlank(message = "대소문자 영문자와 숫자를 포함한 8자리 이상의 비밀번호를 입력해주세요.")
        @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z]).{8,}", message = "비밀번호 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;

        public User toEntity(PasswordEncoder encoder) {
            return User.builder()
                    .email(email)
                    .nickname(nickname)
                    .username(usernameGenerator(email))
                    .password(encoder.encode(password))
                    .role(Role.USER)
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

        @NotBlank(message = "대소문자 영문자와 숫자를 포함한 8자리 이상의 비밀번호를 입력해주세요.")
        @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z]).{8,}", message = "비밀번호 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;
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

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExistResponse {
        @JsonProperty("isExist")
        @Schema(description = "중복/존재 여부", example = "false")
        private boolean exist;

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

    private static String usernameGenerator(String email){
        return email + "_" + UUID.randomUUID().toString();
    }
}
