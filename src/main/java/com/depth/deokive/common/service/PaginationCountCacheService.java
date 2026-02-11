package com.depth.deokive.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 페이지네이션 COUNT 쿼리 캐싱 서비스 (Redis)
 *
 * <p>Deep Pagination에서 매 요청마다 실행되는 COUNT 쿼리를 Redis에 캐싱하여
 * 동시 요청 시 DB 부하를 제거한다.</p>
 *
 * <p>Redis 기반이므로 수평 확장(서버 증설) 시에도 모든 인스턴스가
 * 동일한 캐시를 공유한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaginationCountCacheService {

    private final RedisTemplate<String, Long> longRedisTemplate;

    private static final String KEY_PREFIX = "pagination:count:";
    private static final Duration TTL = Duration.ofSeconds(60);

    /**
     * 캐시된 COUNT 값을 반환한다. (Cache-Aside 패턴)
     * - Cache Hit: Redis 값 반환
     * - Cache Miss: countSupplier 실행 후 Redis에 저장
     *
     * @param cacheKey      캐시 키 (예: "post:feed:ALL", "archive:global:PUBLIC")
     * @param countSupplier COUNT 쿼리 실행 Supplier (Cache Miss 시에만 호출)
     */
    public long getCount(String cacheKey, Supplier<Long> countSupplier) {
        String key = KEY_PREFIX + cacheKey;

        try {
            Object cached = longRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }

            long count = countSupplier.get();
            longRedisTemplate.opsForValue().set(key, count, TTL);
            return count;
        } catch (Exception e) {
            log.warn("[Pagination Count] Redis error, falling back to direct query. key={}, error={}",
                    key, e.getMessage());
            return countSupplier.get();
        }
    }

    /**
     * 정확한 캐시 키를 삭제한다.
     * 필터 조건이 고정된 도메인(Gallery, Repost, Ticket)에서 사용한다.
     *
     * @param cacheKey 캐시 키 (예: "gallery:456", "repost:789")
     */
    public void evict(String cacheKey) {
        String key = KEY_PREFIX + cacheKey;
        try {
            longRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[Pagination Count] Cache eviction failed. key={}, error={}",
                    key, e.getMessage());
        }
    }

    /**
     * 접두사 기반으로 관련 캐시를 일괄 삭제한다.
     * 필터 조건 조합이 다양한 도메인(Post, Archive, Diary)에서 사용한다.
     *
     * @param prefix 캐시 키 접두사 (예: "post", "archive", "diary:123")
     */
    public void evictByPrefix(String prefix) {
        String pattern = KEY_PREFIX + prefix + ":*";
        try {
            Set<String> keys = longRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                longRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[Pagination Count] Cache eviction failed. pattern={}, error={}",
                    pattern, e.getMessage());
        }
    }
}
