package com.depth.deokive.domain.notification.dto.event;


import com.depth.deokive.domain.notification.entity.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationEvent {
    private Long receiverId;
    private Long senderId;
    private NotificationType type;
    private String content;
    private String relatedUrl;

    public static NotificationEvent of(Long receiverId, Long senderId, NotificationType type, String content, String relatedUrl) {
        return NotificationEvent.builder()
                .receiverId(receiverId)
                .senderId(senderId)
                .type(type)
                .content(content)
                .relatedUrl(relatedUrl)
                .build();
    }
}
