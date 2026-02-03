package com.depth.deokive.domain.post.dto;

import com.depth.deokive.domain.post.entity.enums.RepostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Repost OG 추출 완료 이벤트
 * - Redis Pub/Sub을 통해 전파
 * - SSE로 클라이언트에게 전송
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepostCompletedEvent {

    private Long userId;
    private Long repostId;
    private RepostStatus status;
    private String title;
    private String thumbnailUrl;

    public static RepostCompletedEvent completed(Long userId, Long repostId, String title, String thumbnailUrl) {
        return RepostCompletedEvent.builder()
                .userId(userId)
                .repostId(repostId)
                .status(RepostStatus.COMPLETED)
                .title(title)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    public static RepostCompletedEvent failed(Long userId, Long repostId) {
        return RepostCompletedEvent.builder()
                .userId(userId)
                .repostId(repostId)
                .status(RepostStatus.FAILED)
                .title(null)
                .thumbnailUrl(null)
                .build();
    }
}
