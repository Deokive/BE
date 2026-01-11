package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final PostRepository postRepository; // Native Query용 (Join Update가 필요한 경우)
    private final PostStatsRepository postStatsRepository; // JPQL용

    // 10초마다 실행 (실시간성과 정합성 사이의 타협점)
    @Scheduled(fixedRateString = "${scheduler.like-interval:10000}")
    @Transactional
    public void syncLikeCounts() {
        // [전략] PostLikeCount(원본)와 PostStats(타겟)의 값이 다른 경우에만 업데이트
        // 이를 위해 PostRepository에 정의했던 Native Query 사용 권장
        // (JPQL로는 두 테이블 Join Update가 어렵습니다)

        int updatedCount = postRepository.syncLikeCountsFromTable();

        if (updatedCount > 0) {
            log.info("❤️ [Like Sync] Synced {} posts like counts (PostLikeCount -> PostStats)", updatedCount);
        }
    }
}