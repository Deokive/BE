package com.depth.deokive.system.scheduler;

import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.domain.archive.repository.ArchiveStatsRepository;
import com.depth.deokive.common.service.RedisViewService;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountScheduler {

    private final RedisViewService redisViewService;
    private final PostStatsRepository postStatsRepository;
    private final ArchiveStatsRepository archiveStatsRepository;

    private static final int BATCH_SIZE = 5000;

    @Scheduled(cron = "${scheduler.post-view-cron}")
    public void syncPostViews() {
        log.info("🔥 [Scheduler] Starting Post View Count Sync...");
        try {
            syncViews(ViewLikeDomain.POST, postStatsRepository::incrementViewCount);
            log.info("✅ [Scheduler] Synced Post Views");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Post View Sync Failed", e);
        }
    }

    @Scheduled(cron = "${scheduler.archive-view-cron}")
    public void syncArchiveViews() {
        log.info("🔥 [Scheduler] Starting Archive View Count Sync...");
        try {
            syncViews(ViewLikeDomain.ARCHIVE, archiveStatsRepository::incrementViewCount);
            log.info("✅ [Scheduler] Synced Archive Views");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Archive View Sync Failed", e);
        }
    }

    private void syncViews(ViewLikeDomain domain, BiConsumer<Long, Long> dbUpdater) {
        Map<Long, Long> counts = redisViewService.getAndFlushViewCounts(domain, BATCH_SIZE);
        if (counts.isEmpty()) return;

        counts.forEach((id, count) -> {
            if (count > 0) {
                try {
                    // 1. DB 업데이트 (함수형 인터페이스 실행)
                    dbUpdater.accept(id, count);

                    // 2. Redis 차감 (DB 성공 시에만)
                    redisViewService.decrementCount(domain, id, count);
                } catch (Exception e) {
                    log.error("🔴 View Sync Error ID: {} ({})", id, domain, e);
                }
            } else {
                // 3. Zombie Key 정리 (Count <= 0)
                redisViewService.deleteViewCountKey(domain, id);
            }
        });
        log.info("✅ Synced {} Views: {} items", domain, counts.size());
    }
}