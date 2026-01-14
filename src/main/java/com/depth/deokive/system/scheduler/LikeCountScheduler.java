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
import org.springframework.transaction.annotation.Transactional;

import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostStatsRepository postStatsRepository;
    private final ArchiveStatsRepository archiveStatsRepository;

    // 매분 10초에 실행 (0분 10초, 1분 10초...)
    @Scheduled(cron = "10 * * * * *")
    @Transactional
    public void syncPostLikes() {
        log.info("🔥 [Scheduler] Starting Post Like Count Sync (Redis -> DB)...");
        try {
            syncLikeCounts("like:post:count:*", postStatsRepository::updateLikeCount);
            log.info("✅ [Scheduler] Post Like Count Sync Finished.");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Post Sync Failed", e);
        }
    }

    // 매분 40초에 실행 (0분 40초, 1분 40초...) -> Post와 30초 간격 벌림
    @Scheduled(cron = "40 * * * * *")
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void syncArchiveLikes() {
        log.info("🔥 [Scheduler] Starting Archive Like Count Sync (Redis -> DB)...");
        try {
            syncLikeCounts("like:archive:count:*", archiveStatsRepository::updateLikeCount);
            log.info("✅ [Scheduler] Archive Like Count Sync Finished.");
        } catch (Exception e) {
            log.error("🔴 [Scheduler] Archive Sync Failed", e);
        }
    }

    private void syncLikeCounts(String pattern, BiConsumer<Long, Long> updater) {
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
                        Long count = Long.parseLong(countObj.toString());
                        updater.accept(id, count);
                    }
                } catch (NumberFormatException e) {
                    log.error("❌ [Scheduler] Error parsing targetId/count from key: {}", key, e);
                } catch (Exception e) {
                    log.error("❌ [Scheduler] Error syncing key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("❌ [Scheduler] Redis Scan Failed", e);
            throw new RuntimeException(e);
        }
    }
}