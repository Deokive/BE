package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.archive.repository.ArchiveStatsRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostStatsRepository postStatsRepository;
    private final ArchiveStatsRepository archiveStatsRepository;

    // 매분 10초에 실행 (0분 10초, 1분 10초...)
    @Scheduled(cron = "${scheduler.post-like-cron}")
    public void syncPostLikes() {
        log.info("🔥 [Scheduler] Starting Post Like Count Sync (Redis -> DB)...");

        int synced = 0;
        try {
            synced = syncLikeCounts("like:post:count:*", postStatsRepository::updateLikeCount);
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Redis Scan Failed for Post", e);
        }

        if (synced > 0) {
            log.info("✅ [Scheduler] Post Like Count Sync Finished. (synced from Redis: {})", synced);
            return;
        }

        // Redis 키 없음 또는 Redis 장애 → PostLike 테이블에서 직접 재계산
        try {
            log.warn("⚠️ [Scheduler] Redis count 키 없음 → PostLike 테이블에서 직접 재계산");
            postStatsRepository.reconcileLikeCountsFromDb();
            log.info("✅ [Scheduler] Post Like Count Reconciled from DB.");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Post DB Reconciliation Failed", e);
        }
    }

    // 매분 40초에 실행 (0분 40초, 1분 40초...) -> Post와 30초 간격 벌림
    @Scheduled(cron = "${scheduler.archive-like-cron}")
    public void syncArchiveLikes() {
        log.info("🔥 [Scheduler] Starting Archive Like Count Sync (Redis -> DB)...");

        int synced = 0;
        try {
            synced = syncLikeCounts("like:archive:count:*", archiveStatsRepository::updateLikeCount);
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Redis Scan Failed for Archive", e);
        }

        if (synced > 0) {
            log.info("✅ [Scheduler] Archive Like Count Sync Finished. (synced from Redis: {})", synced);
            return;
        }

        // Redis 키 없음 또는 Redis 장애 → ArchiveLike 테이블에서 직접 재계산
        try {
            log.warn("⚠️ [Scheduler] Redis count 키 없음 → ArchiveLike 테이블에서 직접 재계산");
            archiveStatsRepository.reconcileLikeCountsFromDb();
            log.info("✅ [Scheduler] Archive Like Count Reconciled from DB.");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Archive DB Reconciliation Failed", e);
        }
    }

    private int syncLikeCounts(String pattern, BiConsumer<Long, Long> updater) {
        int count = 0;

        // like:{domain}:count:* 패턴만 스캔
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    // Key Format: like:{domain}:count:{targetId} -> 마지막 ":" 뒤에 있는 것이 targetId 이다.
                    String idStr = key.substring(key.lastIndexOf(":") + 1);
                    Long id = Long.parseLong(idStr);

                    // PostLikeRedisService에서 opsForValue().increment()로 저장했으므로 읽을 때도 opsForValue().get()을 사용
                    Object countObj = redisTemplate.opsForValue().get(key);

                    if (countObj != null) {
                        Long likeCount = Long.parseLong(countObj.toString());
                        updater.accept(id, likeCount);
                        count++;
                    }
                } catch (NumberFormatException e) {
                    log.error("❌ [Scheduler] Error parsing targetId/count from key: {}", key, e);
                } catch (Exception e) {
                    log.error("❌ [Scheduler] Error syncing key: {}", key, e);
                }
            }
        }

        return count;
    }
}
