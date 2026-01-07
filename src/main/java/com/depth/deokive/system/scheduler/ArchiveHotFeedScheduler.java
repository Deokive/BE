package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveHotFeedScheduler {

    private final ArchiveRepository archiveRepository;

    @Scheduled(cron = "${scheduler.hot-score-cron}") // 매시 정각 (ex: 13:00, 14:00)
    @Transactional
    public void updateHotScores() {
        log.info("🔥 [Scheduler] Starting Hot Score Update...");

        int updatedRows = archiveRepository.updateHotScoreBulk(
            4,    // w1 (좋아요 가중치)
            6,    // w2 (조회수 가중치)
            0.05  // L (시간 감쇠 계수)
        );

        log.info("✅ [Scheduler] Hot Score Update Completed. (Updated Rows: {})", updatedRows);
    }
}