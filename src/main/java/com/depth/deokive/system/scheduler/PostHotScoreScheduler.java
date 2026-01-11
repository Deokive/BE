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
        int updatedRows = postRepository.updateHotScoreBulkInStats(4, 6, 0.05);
        log.info("✅ [Scheduler] Post Hot Score Update Completed. (Rows: {})", updatedRows);
    }
}