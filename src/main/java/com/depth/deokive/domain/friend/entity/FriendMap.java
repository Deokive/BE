package com.depth.deokive.domain.friend.entity;

import com.depth.deokive.common.auditor.TimeBaseEntity;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(
    name = "friend_map",
    uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_friend_map_user_friend",
                columnNames = {"user_id", "friend_id"}
        )
    },
    indexes = {
            @Index(name = "idx_user_status_time", columnList = "user_id, friend_status, accepted_at, friend_id")
    }
)
public class FriendMap extends TimeBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendStatus friendStatus;

    @Column
    private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Column(name = "friend_id", insertable = false, updatable = false)
    private Long friendId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    public void updateStatus(FriendStatus friendStatus) {
        this.friendStatus = friendStatus;
    }

    public void updateRequestedBy(User requestedBy) {
        this.requestedBy = requestedBy;
    }
}

