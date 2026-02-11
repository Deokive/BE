package com.depth.deokive.common.service;

import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeRedisService {

    private final RedisTemplate<String, Long> longRedisTemplate;
    private final RedissonClient redissonClient;
    private final DefaultRedisScript<Long> likeScript;
    private final LikeMessagePublisher likeMessagePublisher;

    private static final String DUMMY_VALUE = "dummy";
    private static final String TTL_SECONDS = "259200"; // 3일

    // --- Key Generators
    private String getLikeCountKey(ViewLikeDomain domain, Long id) { return "like:" + domain.getPrefix() + ":count:" + id; }
    private String getLikeSetKey(ViewLikeDomain domain, Long id) { return "like:" + domain.getPrefix() + ":users:" + id; }
    private String getLockKey(ViewLikeDomain domain, Long id) { return "lock:like:" + domain.getPrefix() + ":" + id; }

    public boolean toggleLike(
            ViewLikeDomain domain,
            Long targetId,
            Long userId,
            Supplier<List<Long>> dbLoader,
            Runnable existenceValidator
    ) {
        String setKey = getLikeSetKey(domain, targetId);
        String countKey = getLikeCountKey(domain, targetId);
        String lockKey = getLockKey(domain, targetId);

        // 1. 캐시 없으면 Warming (분산 락)
        if (!longRedisTemplate.hasKey(setKey)) {
            warmingWithLock(setKey, countKey, lockKey, dbLoader, existenceValidator);
        }

        // 2. Lua Script 실행: 중복체크 + 카운팅 + TTL을 Redis 내부에서 원자적으로 처리
        // 락 없이도 Redis 싱글 스레드 특성상 완벽한 원자성 보장
        Long result = longRedisTemplate.execute(
                likeScript,
                List.of(setKey, countKey), // KEYS[1], KEYS[2]
                String.valueOf(userId),    // ARGV[1]
                DUMMY_VALUE,               // ARGV[2]
                TTL_SECONDS                // ARGV[3]
        );

        boolean isLiked = (result != null && result == 1);

        // 3. MQ 비동기 전송 (별도 Bean 호출로 @Async 프록시 정상 동작)
        likeMessagePublisher.sendToQueue(domain, targetId, userId, isLiked);

        return isLiked;
    }

    public boolean isLiked(ViewLikeDomain domain, Long targetId, Long userId, Supplier<List<Long>> dbLoader, Runnable existenceValidator) {
        String setKey = getLikeSetKey(domain, targetId);
        if (!longRedisTemplate.hasKey(setKey)) {
            warmingWithLock(setKey, getLikeCountKey(domain, targetId), getLockKey(domain, targetId), dbLoader, existenceValidator);
        }
        return Boolean.TRUE.equals(longRedisTemplate.opsForSet().isMember(setKey, String.valueOf(userId)));
    }

    public Long getCount(ViewLikeDomain domain, Long targetId, Supplier<List<Long>> dbLoader, Runnable existenceValidator) {
        String countKey = getLikeCountKey(domain, targetId);
        Object countObj = longRedisTemplate.opsForValue().get(countKey);
        if (countObj != null) return Long.parseLong(countObj.toString());

        warmingWithLock(getLikeSetKey(domain, targetId), countKey, getLockKey(domain, targetId), dbLoader, existenceValidator);
        Object warmedCount = longRedisTemplate.opsForValue().get(countKey);
        return warmedCount != null ? Long.parseLong(warmedCount.toString()) : 0L;
    }

    private void warmingWithLock(
            String setKey,
            String countKey,
            String lockKey,
            Supplier<List<Long>> dbLoader,
            Runnable existenceValidator
    ) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) return;
            if (longRedisTemplate.hasKey(setKey)) return;

            // DB 웜업 직전에 존재 여부 검증 -> 여기서 예외가 터지면 Redis Key가 생성되지 않고 404가 나감
            if (existenceValidator != null) { existenceValidator.run(); }

            // 1. DB 전체 로딩 (목적: 이미 좋아요했던 사람이 취소하려고 눌렀는데 등록이 되버리는 상황 방지)
            List<Long> userIds = dbLoader.get();

            // 2. Set 적재
            if (!userIds.isEmpty()) {
                longRedisTemplate.opsForSet().add(setKey, userIds.toArray(new Long[0]));
                longRedisTemplate.opsForValue().set(countKey, (long) userIds.size()); // Count는 유저 크기만큼 설정
            } else {
                // 3. 좋아요 0개인 경우 -> Dummy 삽입
                longRedisTemplate.opsForSet().add(setKey, -1L);
                longRedisTemplate.opsForValue().set(countKey, 0L); // Set에는 dummy가 있지만, 보여지는 Count는 0으로 설정
            }

            longRedisTemplate.expire(setKey, 3, TimeUnit.DAYS);
            longRedisTemplate.expire(countKey, 3, TimeUnit.DAYS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void deleteLikeData(ViewLikeDomain domain, Long targetId) {
        String setKey = getLikeSetKey(domain, targetId);
        String countKey = getLikeCountKey(domain, targetId);

        // Lock Key는 TTL이 짧고 자동 만료되므로 굳이 삭제 안 해도 됨

        longRedisTemplate.delete(List.of(setKey, countKey));
        log.info("[Redis] Deleted Like Data for {} ID: {}", domain, targetId);
    }
}