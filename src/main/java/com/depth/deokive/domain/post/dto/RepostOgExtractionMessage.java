package com.depth.deokive.domain.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ 메시지: Repost OG 메타데이터 추출 요청
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RepostOgExtractionMessage {
    private Long repostId;
    private Long userId;  // SSE 알림 전송을 위한 사용자 ID
    private String url;
}
