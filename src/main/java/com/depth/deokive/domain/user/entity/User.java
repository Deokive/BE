package com.depth.deokive.domain.user.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "USER_EMAIL", columnNames = "email")
})
public class User extends TimeBaseEntity {

    // ---------- Main Fields ------------
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column // OAuth2의 경우라면 password가 nullable -> 그리고 이걸 RequestDTO에서 NotBlank로 설정하면 Login API 우회 불가
    private String password;

    @Column(nullable = false)
    private String nickname;

    // ----------- Sub Fields ------------

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserType userType = UserType.COMMON;

    // TODO: DTO 정의 후 추가 로직 필요

}
