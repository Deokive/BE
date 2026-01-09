package com.depth.deokive.system.scheduler;

import com.depth.deokive.common.enums.ViewDomain;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.common.service.RedisViewService;
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
    private final PostRepository postRepository;
    private final ArchiveRepository archiveRepository;

    @Scheduled(fixedRateString = "${scheduler.view-interval:300000}") // Default : 5분
    public void syncAllViewCounts() {
        log.info("🔥 [Scheduler] Starting View Count Sync...");
        syncPostViews();
        syncArchiveViews();
        log.info("✅ [Scheduler] View Count Sync Finished.");
    }

    private void syncPostViews() {
        Map<Long, Long> counts = redisViewService.getAndFlushViewCounts(ViewDomain.POST);
        if (counts.isEmpty()) return;

        counts.forEach((id, count) -> {
            if (count > 0) {
                try {
                    postRepository.incrementViewCount(id, count);
                    redisViewService.decrementCount(ViewDomain.POST, id, count);
                } catch (Exception e) {
                    log.error("🔴 Post View Sync Failed ID: {}", id, e);
                }
            }
        });
        log.info("✅ Synced Post Views: {} items", counts.size());
    }

    private void syncArchiveViews() {
        Map<Long, Long> counts = redisViewService.getAndFlushViewCounts(ViewDomain.ARCHIVE);
        if (counts.isEmpty()) return;

        counts.forEach((id, count) -> {
            if (count > 0) {
                try {
                    archiveRepository.incrementViewCount(id, count);
                    redisViewService.decrementCount(ViewDomain.ARCHIVE, id, count);
                } catch (Exception e) {
                    log.error("🔴 Archive View Sync Failed ID: {}", id, e);
                }
            }
        });
        log.info("✅ Synced Archive Views: {} items", counts.size());
    }
}