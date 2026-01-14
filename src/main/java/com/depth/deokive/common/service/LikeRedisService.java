package com.depth.deokive.common.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.common.enums.ViewDomain;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RedissonClient redissonClient;
    private final DefaultRedisScript<Long> likeScript;

    private static final String DUMMY_VALUE = "dummy";
    private static final String TTL_SECONDS = "259200"; // 3일

    // --- Key Generators
    private String getLikeCountKey(ViewDomain domain, Long id) { return "like:" + domain.getPrefix() + ":count:" + id; }
    private String getLikeSetKey(ViewDomain domain, Long id) { return "like:" + domain.getPrefix() + ":users:" + id; }
    private String getLockKey(ViewDomain domain, Long id) { return "lock:like:" + domain.getPrefix() + ":" + id; }

    public boolean toggleLike(
            ViewDomain domain,
            Long targetId,
            Long userId,
            Supplier<List<Long>> dbLoader
    ) {
        String setKey = getLikeSetKey(domain, targetId);
        String countKey = getLikeCountKey(domain, targetId);
        String lockKey = getLockKey(domain, targetId);

        // 1. 캐시 없으면 Warming (분산 락)
        if (!redisTemplate.hasKey(setKey)) {
            warmingWithLock(setKey, countKey, lockKey, dbLoader);
        }

        // 2. Lua Script 실행: 중복체크 + 카운팅 + TTL을 Redis 내부에서 원자적으로 처리
        // 락 없이도 Redis 싱글 스레드 특성상 완벽한 원자성 보장
        Long result = redisTemplate.execute(
                likeScript,
                List.of(setKey, countKey), // KEYS[1], KEYS[2]
                String.valueOf(userId),    // ARGV[1]
                DUMMY_VALUE,               // ARGV[2]
                TTL_SECONDS                // ARGV[3]
        );

        boolean isLiked = (result != null && result == 1);

        // 3. MQ 전송
        sendToQueue(domain, targetId, userId, isLiked);

        return isLiked;
    }

    public boolean isLiked(ViewDomain domain, Long targetId, Long userId, Supplier<List<Long>> dbLoader) {
        String setKey = getLikeSetKey(domain, targetId);
        if (!redisTemplate.hasKey(setKey)) {
            warmingWithLock(setKey, getLikeCountKey(domain, targetId), getLockKey(domain, targetId), dbLoader);
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(setKey, String.valueOf(userId)));
    }

    public Long getCount(ViewDomain domain, Long targetId, Supplier<List<Long>> dbLoader) {
        String countKey = getLikeCountKey(domain, targetId);
        Object countObj = redisTemplate.opsForValue().get(countKey);
        if (countObj != null) return Long.parseLong(countObj.toString());

        warmingWithLock(getLikeSetKey(domain, targetId), countKey, getLockKey(domain, targetId), dbLoader);
        Object warmedCount = redisTemplate.opsForValue().get(countKey);
        return warmedCount != null ? Long.parseLong(warmedCount.toString()) : 0L;
    }

    private void warmingWithLock(
            String setKey,
            String countKey,
            String lockKey,
            Supplier<List<Long>> dbLoader
    ) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) return;
            if (redisTemplate.hasKey(setKey)) return;

            // 1. DB 전체 로딩 (목적: 이미 좋아요했던 사람이 취소하려고 눌렀는데 등록이 되버리는 상황 방지)
            List<Long> userIds = dbLoader.get();

            // 2. Set 적재
            if (!userIds.isEmpty()) {
                redisTemplate.opsForSet().add(setKey, userIds.toArray());
                redisTemplate.opsForValue().set(countKey, String.valueOf(userIds.size())); // Count는 유저 크기만큼 설정
            } else {
                // 3. 좋아요 0개인 경우 -> Dummy 삽입
                redisTemplate.opsForSet().add(setKey, DUMMY_VALUE);
                redisTemplate.opsForValue().set(countKey, "0"); // Set에는 dummy가 있지만, 보여지는 Count는 0으로 설정
            }

            redisTemplate.expire(setKey, 3, TimeUnit.DAYS);
            redisTemplate.expire(countKey, 3, TimeUnit.DAYS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Async("messagingTaskExecutor")
    public void sendToQueue(ViewDomain domain, Long targetId, Long userId, boolean isLiked) {
        LikeMessageDto message = new LikeMessageDto(targetId, userId, isLiked);
        rabbitTemplate.convertAndSend(domain.getExchangeName(), domain.getRoutingKey(), message);
        log.debug("🐇 [MQ Send] Domain: {}, TargetId: {}, Action: {}", domain, targetId, isLiked ? "LIKE" : "UNLIKE");
    }
}