package com.depth.deokive.domain.notification.dto.event;


import com.depth.deokive.domain.notification.entity.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "실시간 알림 데이터 페이로드")
public class NotificationEvent {
    @Schema(description = "알림 수신자 ID", example = "10")
    private Long receiverId;

    @Schema(description = "알림 발신자 ID (시스템 알림일 경우 null 가능)", example = "5")
    private Long senderId;

    @Schema(description = "알림 유형 (FRIEND_REQUEST, FRIEND_ACCEPT)", example = "FRIEND_REQUEST")
    private NotificationType type;

    @Schema(description = "알림 메시지 내용", example = "홍길동님이 친구 요청을 보냈습니다.")
    private String content;

    @Schema(description = "관련 리소스 URL")
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
