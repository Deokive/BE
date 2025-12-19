package com.depth.deokive.domain.user.dto;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Slf4j
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
        @Schema(description = "사용자 역할", example = "USER | ADMIN")
        private Role role;
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;
        @Schema(description = "계정 생성 시간", example = "KST Datetime")
        private LocalDateTime createdAt;
        @Schema(description = "마지막 정보 수정 시간", example = "KST Datetime")
        private LocalDateTime lastModifiedAt;

        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .nickname(user.getNickname())
                    .createdAt(user.getCreatedAt())
                    .lastModifiedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 업데이트 요청 DTO")
    public static class UserUpdateRequest {
        @Pattern(regexp = "^[가-힣a-zA-Z0-9-_]{2,10}$", message = "닉네임 조건에 충족되지 않습니다.")
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$", message = "비밀번호는 8~16자 사이에 영문, 숫자, 특수문자를 포함해야 합니다.")
        @Schema(description = "사용자 비밀번호", example = "password content")
        private String password;

        public void encodePassword(PasswordEncoder passwordEncoder) {
            try {
                this.password = passwordEncoder.encode(this.password);
            } catch (Exception e) {
                log.info("🔴 Null Password -> Passing this Loop & will Check in the User Entity");
            }

        }
    }
}
