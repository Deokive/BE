package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostHotScoreScheduler {

    private final PostRepository postRepository;

    @Scheduled(cron = "${scheduler.hot-score-cron}")
    @ExecutionTime
    @Transactional
    public void updatePostHotScores() {
        log.info("🔥 [Scheduler] Starting Post Hot Score Update...");
        int updatedRows = postRepository.updateHotScoreBulk(4, 6, 0.05);
        log.info("✅ [Scheduler] Post Hot Score Update Completed. (Rows: {})", updatedRows);

        // PostHotScoreScheduler.java (간략 예시)
            // 1. PostStatsRepository에서 대상 조회
            // 2. 점수 계산
            // 3. PostStatsRepository.updateHotScore() 호출 (Atomic Update)
    }
}