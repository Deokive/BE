package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.repository.RepostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repost OG 메타데이터 DB 업데이트 서비스
 *
 * [분리 이유]
 * - RepostOgConsumer에서 @Transactional 메서드를 내부 호출하면 프록시가 작동하지 않음
 * - 별도 빈으로 분리해야 트랜잭션이 정상 적용됨
 *
 * [트랜잭션 범위]
 * - DB 조회 + 업데이트만 (~50ms)
 * - OG 추출(1.5초)은 Consumer에서 트랜잭션 없이 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepostOgUpdateService {

    private final RepostRepository repostRepository;

    /**
     * Repost 메타데이터 완료 처리
     * - 트랜잭션 범위: DB 조회 + Dirty Checking (~50ms)
     */
    @Transactional
    public void completeMetadata(Long repostId, String title, String thumbnailUrl) {
        Repost repost = repostRepository.findById(repostId).orElse(null);
        if (repost == null) {
            log.warn("[OG UpdateService] Repost ID={} 존재하지 않음 (이미 삭제됨?)", repostId);
            return;
        }
        repost.completeMetadata(title, thumbnailUrl);
    }

    /**
     * Repost 실패 처리
     * - 트랜잭션 범위: DB 조회 + Dirty Checking (~50ms)
     */
    @Transactional
    public void markAsFailed(Long repostId, String reason) {
        try {
            Repost repost = repostRepository.findById(repostId).orElse(null);
            if (repost != null) {
                repost.markAsFailed();
                log.warn("[OG UpdateService] Repost ID={} 실패 처리: {}", repostId, reason);
            }
        } catch (Exception e) {
            log.error("[OG UpdateService] Repost ID={} 실패 처리 중 오류", repostId, e);
        }
    }
}
