package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.archive.repository.ArchiveRepository;
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
public class HotScoreScheduler {

    private final PostRepository postRepository;
    private final ArchiveRepository archiveRepository;

    @Scheduled(cron = "${scheduler.post-hot-score-cron}")
    @ExecutionTime
    @Transactional
    public void updatePostHotScores() {
        log.info("🔥 [Scheduler] Starting Post Hot Score Update...");
        int updatedRows = postRepository.updateHotScoreBulkInStats(4, 6, 0.05);
        log.info("✅ [Scheduler] Post Hot Score Update Completed. (Rows: {})", updatedRows);
    }

    @ExecutionTime
    @Scheduled(cron = "${scheduler.archive-hot-score-cron}")
    @Transactional
    public void updateHotScores() {
        log.info("🔥 [Scheduler] Starting Hot Score Update...");

        int updatedRows = archiveRepository.updateHotScoreBulkInStats(
                4,    // w1 (좋아요 가중치)
                6,    // w2 (조회수 가중치)
                0.05  // L (시간 감쇠 계수)
        );

        log.info("✅ [Scheduler] Hot Score Update Completed. (Updated Rows: {})", updatedRows);
    }
}