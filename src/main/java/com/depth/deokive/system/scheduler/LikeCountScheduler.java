package com.depth.deokive.system.scheduler;

import com.depth.deokive.domain.post.repository.PostStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostStatsRepository postStatsRepository;

    // 1분마다 실행 (서비스 규모에 따라 조절)
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void syncLikeCountsFromRedis() {
        log.info("🔥 [Scheduler] Starting Like Count Sync (Redis -> DB)...");

        // 1. like:post:count:* 패턴만 스캔합니다. (User Set은 스캔하지 않음)
        ScanOptions options = ScanOptions.scanOptions().match("like:post:count:*").count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    // Key Format: like:post:count:{postId}
                    // 마지막 ":" 뒤에 있는 것이 postId입니다.
                    String postIdStr = key.substring(key.lastIndexOf(":") + 1);
                    Long postId = Long.parseLong(postIdStr);

                    // [핵심 수정]
                    // PostLikeRedisService에서 opsForValue().increment()로 저장했으므로
                    // 읽을 때도 opsForValue().get()을 사용해야 합니다.
                    Object countObj = redisTemplate.opsForValue().get(key);

                    if (countObj != null) {
                        Long count = Long.parseLong(countObj.toString());

                        // DB PostStats 업데이트
                        // (단일 업데이트 혹은 Bulk Update 쿼리 사용 권장)
                        postStatsRepository.updateLikeCount(postId, count);
                    }
                } catch (NumberFormatException e) {
                    log.error("❌ [Scheduler] Error parsing postId/count from key: {}", key, e);
                } catch (Exception e) {
                    log.error("❌ [Scheduler] Error syncing key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("❌ [Scheduler] Redis Scan Failed", e);
        }

        log.info("✅ [Scheduler] Like Count Sync Finished.");
    }
}