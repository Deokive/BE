package com.depth.deokive.system.scheduler;

import com.depth.deokive.common.enums.ViewDomain;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.repository.ArchiveStatsRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.common.service.RedisViewService;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountScheduler {

    private final RedisViewService redisViewService;
    private final PostStatsRepository postStatsRepository;
    private final ArchiveStatsRepository archiveStatsRepository;

    private static final int BATCH_SIZE = 5000;

    @Scheduled(fixedRateString = "${scheduler.view-interval:300000}") // Default : 5분
    public void syncAllViewCounts() {
        log.info("🔥 [Scheduler] Starting View Count Sync...");
        syncPostViews();
        syncArchiveViews();
        log.info("✅ [Scheduler] View Count Sync Finished.");
    }

    private void syncPostViews() {
        Map<Long, Long> counts = redisViewService.getAndFlushViewCounts(ViewDomain.POST, BATCH_SIZE);
        if (counts.isEmpty()) return;

        counts.forEach((id, count) -> {
            if (count > 0) {
                try {
                    postStatsRepository.incrementViewCount(id, count);
                    redisViewService.decrementCount(ViewDomain.POST, id, count);
                } catch (Exception e) {
                    log.error("🔴 Post View Sync Failed ID: {}", id, e);
                }
            } else {
                // 조회수가 0 이하인 경우 DB 업데이트 없이 Redis 키만 삭제 (Zombie Key 정리)
                redisViewService.deleteViewCountKey(ViewDomain.POST, id);
            }
        });
        log.info("✅ Synced Post Views: {} items", counts.size());
    }

    private void syncArchiveViews() {
        Map<Long, Long> counts = redisViewService.getAndFlushViewCounts(ViewDomain.ARCHIVE, BATCH_SIZE);
        if (counts.isEmpty()) return;

        counts.forEach((id, count) -> {
            if (count > 0) {
                try {
                    archiveStatsRepository.incrementViewCount(id, count);
                    redisViewService.decrementCount(ViewDomain.ARCHIVE, id, count);
                } catch (Exception e) {
                    log.error("🔴 Archive View Sync Failed ID: {}", id, e);
                }
            } else {
                // 조회수가 0 이하인 경우 DB 업데이트 없이 Redis 키만 삭제 (Zombie Key 정리)
                redisViewService.deleteViewCountKey(ViewDomain.POST, id);
            }
        });
        log.info("✅ Synced Archive Views: {} items", counts.size());
    }
}