package com.depth.deokive.common.service;

import com.depth.deokive.common.enums.ViewDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisViewService {

    private final StringRedisTemplate redisTemplate;

    @Value("${scheduler.view-cooldown-minutes:10}")
    private long cooldownMinutes;

    private static final String LOG_KEY_FORMAT = "view:log:%s:%s:%s"; // Key 패턴: view:log:{domain}:{id}:{user/ip}
    private static final String COUNT_KEY_FORMAT = "view:count:%s:%s"; // Key 패턴: view:count:{domain}:{id}

    /** 조회수 증가 로직 */
    public void incrementViewCount(ViewDomain domain, Long id, Long userId, String clientIp) {
        String identifier = (userId != null) ? "user:" + userId : "ip:" + clientIp;

        // SEQ 1. Define logKey
        // ex. view:log:post:1:user:100 OR view:log:archive:5:ip:127.0.0.1
        String logKey = String.format(LOG_KEY_FORMAT, domain.getPrefix(), id, identifier);

        // SEQ 2. Prevent Abusing Logic
        Duration ttl = Duration.ofMinutes(cooldownMinutes);
        Boolean isFirstView = redisTemplate.opsForValue().setIfAbsent(logKey, "1", ttl);

        // SEQ 3. View Increment Logic
        if (Boolean.TRUE.equals(isFirstView)) {
            String countKey = String.format(COUNT_KEY_FORMAT, domain.getPrefix(), id);
            redisTemplate.opsForValue().increment(countKey);

            // TODO: 테스트 마치면 제거
            log.debug("🟢 [Redis] Increase View - {}: {}, Identifier: {}", domain, id, identifier);
        }
    }

    /** 특정 도메인의 조회수 데이터를 모두 스캔하여 반환 */
    public Map<Long, Long> getAndFlushViewCounts(ViewDomain domain, int limit) {
        Map<Long, Long> viewCounts = new HashMap<>();
        String pattern = String.format("view:count:%s:*", domain.getPrefix()); // Ex. view:count:archive:*

        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection().scan(options)) {
            while (cursor.hasNext()) {
                if (viewCounts.size() >= limit) {
                    log.info("🟡 [Redis] Batch limit reached ({}), stopping scan for this cycle.", limit);
                    break;
                }

                String key = new String(cursor.next());
                String value = redisTemplate.opsForValue().get(key);

                if (value != null) {
                    Long id = Long.parseLong(key.split(":")[3]); // Key 파싱: view:count:{domain}:{id}
                    viewCounts.put(id, Long.parseLong(value));
                }
            }
        } catch (Exception e) {
            log.error("🔴 Redis Scan Error ({})", domain, e);
        }
        return viewCounts;
    }

    /** DB 반영 후 차감 */
    public void decrementCount(ViewDomain domain, Long id, Long count) {
        String key = String.format(COUNT_KEY_FORMAT, domain.getPrefix(), id);
        redisTemplate.opsForValue().decrement(key, count);

        String value = redisTemplate.opsForValue().get(key);
        if (value != null && Long.parseLong(value) <= 0) {
            redisTemplate.delete(key);
        }
    }
}