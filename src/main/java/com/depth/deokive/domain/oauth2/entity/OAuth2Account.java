package com.depth.deokive.domain.oauth2.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.oauth2.entity.enums.ProviderType;
import com.depth.deokive.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Table(
        name = "oauth2_account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_type_id",
                        columnNames = {"provider_type", "provider_id"}
                )
        }
)
public class OAuth2Account extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ProviderType providerType;

    @Column(name = "provider_id", nullable = false)
    private String providerId; // Hashed Data Using HmacUtils

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_oauth2_account_user"))
    private User user;

    public static OAuth2Account create(ProviderType providerType, String hashedProviderId, User user) {
        if (providerType == null || hashedProviderId == null || user == null) {
            throw new IllegalArgumentException("ProviderType, hashedProviderId, User는 null일 수 없습니다.");
        }

        return OAuth2Account.builder()
                .providerType(providerType)
                .providerId(hashedProviderId)
                .user(user)
                .build();
    }
}
