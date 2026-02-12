package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FriendCleanupScheduler {

    private final FriendMapRepository friendMapRepository;

    private static final List<FriendStatus> CLEANUP_STATUSES = List.of(
            FriendStatus.CANCELED,
            FriendStatus.REJECTED
    );
    private static final int RETENTION_DAYS = 14;

    @Scheduled(cron = "${scheduler.friend-cleanup-cron}")
    @ExecutionTime
    @Transactional
    public void cleanupExpiredFriendRecords() {
        log.info("🧹 [Scheduler] Starting Friend Cleanup (CANCELED/REJECTED, {}+ days old)...", RETENTION_DAYS);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deletedCount = friendMapRepository.deleteExpiredByStatuses(CLEANUP_STATUSES, cutoff);

        log.info("✅ [Scheduler] Friend Cleanup Completed. Deleted {} records (cutoff: {})", deletedCount, cutoff);
    }
}
