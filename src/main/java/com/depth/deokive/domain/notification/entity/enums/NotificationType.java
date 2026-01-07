package com.depth.deokive.domain.notification.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationType {
    FRIEND_REQUEST("친구 요청"),
    FRIEND_ACCEPT("친구 수락");

    private final String description;
}