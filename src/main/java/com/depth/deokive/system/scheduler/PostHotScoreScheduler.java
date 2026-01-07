package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.post.repository.PostRepository;
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
    @Transactional
    public void updatePostHotScores() {
        log.info("🔥 [Scheduler] Starting Post Hot Score Update...");
        long start = System.currentTimeMillis();

        int updatedRows = postRepository.updateHotScoreBulk(4, 6, 0.05);

        long end = System.currentTimeMillis();
        log.info("✅ [Scheduler] Post Hot Score Update Completed. (Rows: {}, Time: {}ms)", updatedRows, (end - start));
    }
}