package com.depth.deokive.domain.user.dto;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 정보 응답 DTO")
    public static class UserResponse {
        @Schema(description = "PK", example = "1")
        private Long id;
        @Schema(description = "이메일", example = "user@email.com")
        private String email;
        @Schema(description = "사용자 성별", example = "USER | MANAGER | ADMIN")
        private Role role;
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;

        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .nickname(user.getNickname())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 업데이트 요청 DTO")
    public static class UserUpdateRequest {
        @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z]).{8,}", message = "비밀번호 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;
        @Pattern(regexp = "^[ㄱ-ㅎ가-힣a-zA-Z0-9-_]{2,10}$", message = "닉네임 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;

        public void encodePassword(PasswordEncoder passwordEncoder) {
            this.password = passwordEncoder.encode(this.password);
        }
    }
}
