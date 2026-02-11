package com.depth.deokive.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 페이지네이션 ID 리스트 캐싱 서비스 (Redis)
 *
 * <p>Deep Pagination에서 매 요청마다 실행되는 offset scan(커버링 인덱스 쿼리)의
 * 결과(ID 리스트)를 Redis에 캐싱하여, 같은 필터+정렬+페이지 조합의
 * 동시 요청에서 DB 부하를 제거한다.</p>
 *
 * <p>직렬화: {@code List<Long>} → 쉼표 구분 문자열 ({@code "10231,10230,10229"})</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaginationIdCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "pagination:ids:";
    private static final Duration TTL = Duration.ofSeconds(60);

    /**
     * 캐시된 ID 리스트를 반환한다. (Cache-Aside 패턴)
     * - Cache Hit: Redis 값 역직렬화 후 반환
     * - Cache Miss: idsSupplier 실행 후 Redis에 저장
     * - 빈 리스트는 캐싱하지 않음 (새 데이터 추가 시 즉시 반영)
     *
     * @param cacheKey    캐시 키 (예: "post:feed:ALL:createdAt_DESC,id_DESC:0:20")
     * @param idsSupplier ID 조회 쿼리 실행 Supplier (Cache Miss 시에만 호출)
     */
    public List<Long> getIds(String cacheKey, Supplier<List<Long>> idsSupplier) {
        String key = KEY_PREFIX + cacheKey;

        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return deserialize(cached);
            }

            List<Long> ids = idsSupplier.get();

            if (!ids.isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, serialize(ids), TTL);
            }

            return ids;
        } catch (Exception e) {
            log.warn("[Pagination IDs] Redis error, falling back to direct query. key={}, error={}",
                    key, e.getMessage());
            return idsSupplier.get();
        }
    }

    /**
     * 정확한 캐시 키를 삭제한다.
     */
    public void evict(String cacheKey) {
        String key = KEY_PREFIX + cacheKey;
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[Pagination IDs] Cache eviction failed. key={}, error={}",
                    key, e.getMessage());
        }
    }

    /**
     * 접두사 기반으로 관련 캐시를 일괄 삭제한다.
     *
     * @param prefix 캐시 키 접두사 (예: "post", "archive", "diary:123")
     */
    public void evictByPrefix(String prefix) {
        String pattern = KEY_PREFIX + prefix + ":*";
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[Pagination IDs] Cache eviction failed. pattern={}, error={}",
                    pattern, e.getMessage());
        }
    }

    private String serialize(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> deserialize(String value) {
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(Long::parseLong)
                .toList();
    }
}
