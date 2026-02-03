package com.depth.deokive.domain.post.entity.enums;

/**
 * Repost OG 메타데이터 추출 상태
 * - PENDING: OG 추출 대기 중 (RabbitMQ Queue에 등록됨)
 * - COMPLETED: OG 추출 완료 (썸네일 + 타이틀 업데이트 완료)
 * - FAILED: OG 추출 실패 (타임아웃, 네트워크 오류, 파싱 실패 등)
 */
public enum RepostStatus {
    PENDING,    // 생성 직후, OG 추출 대기
    COMPLETED,  // OG 추출 완료
    FAILED      // OG 추출 실패 (복구 불가)
}
