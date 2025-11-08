package com.depth.deokive.domain.user.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Locale;

@SuperBuilder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "USER_USERNAME", columnNames = "username"),
        @UniqueConstraint(name = "USER_EMAIL", columnNames = "email")
})
public class User extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String username; // Client에는 노출되지 않는 히든 필드임 (OAuth2와 일반 케이스를 모두 아우르려면 이게 깔끔)

    @Column(nullable = false)
    private String nickname;

    @Column // OAuth2의 경우라면 password가 nullable -> 그리고 이걸 RequestDTO에서 NotBlank로 설정하면 Login API 우회 불가
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserType userType = UserType.COMMON;

    @PrePersist // INSERT 되기 전 실행 (새로운 User 저장 시)
    @PreUpdate  // UPDATE 되기 전 실행 (기존 User 수정 시)
    private void normalize() {
        if (this.nickname != null) this.nickname = this.nickname.trim(); // "hades " 같은 공백 포함 문자열 방지
        if (this.email != null) this.email = this.email.trim().toLowerCase(Locale.ROOT);
    }

    // JPA Dirty Checking
    public void update(UserDto.UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null) return;

        this.nickname = nonBlankOrDefault(userUpdateRequest.getNickname(), this.nickname);
        this.password = nonBlankOrDefault(userUpdateRequest.getPassword(), this.password); // encoded password
        this.email = nonBlankOrDefault(userUpdateRequest.getEmail(), this.email);
    }

    // OAuth2 사용자 정보 업데이트
    public void updateOAuth2Info(String nickname, String email) {
        this.nickname = nonBlankOrDefault(nickname, this.nickname);
        this.email = nonBlankOrDefault(email, this.email);
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
