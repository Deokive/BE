package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.repository.PostLikeRepository;
import com.depth.deokive.system.config.rabbitmq.RabbitMQConfig;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final PostLikeRepository postLikeRepository;
    private final RedissonClient redissonClient;

    private static final String DUMMY_VALUE = "dummy";

    private String getLikeCountKey(Long postId) { return "like:post:count:" + postId; }
    private String getLikeSetKey(Long postId) { return "like:post:users:" + postId; }
    private String getLockKey(Long postId) { return "lock:like:" + postId; }

    public PostDto.LikeResponse toggleLike(Long postId, Long userId) {
        String setKey = getLikeSetKey(postId);
        String countKey = getLikeCountKey(postId);
        String userIdStr = String.valueOf(userId);

        // 1. 캐시 없으면 Warming (분산 락)
        if (!redisTemplate.hasKey(setKey)) {
            warmingWithLock(postId, setKey, countKey);
        }

        // 2. Redis 연산
        boolean isLiked;
        // 추가됨(1), 이미존재(0)
        Long result = redisTemplate.opsForSet().add(setKey, userIdStr);

        if (result != null && result == 1) {
            // New Like
            isLiked = true;
            redisTemplate.opsForValue().increment(countKey);

            // 진짜 유저가 들어왔으니, 자리 차지하던 'dummy' 제거 -> 있으면 지워지고, 없으면 무시됨
            redisTemplate.opsForSet().remove(setKey, DUMMY_VALUE);

        } else {
            // Already Exists -> Unlike
            isLiked = false;
            redisTemplate.opsForSet().remove(setKey, userIdStr);
            redisTemplate.opsForValue().decrement(countKey);

            // 만약 모든 유저가 다 취소해서 Set이 비어버리면?
            // -> Redis Key가 사라져서 다시 'Cache Miss'가 발생할 수 있음.
            // -> 다시 Cache Miss가 나도 Warming 로직이 "좋아요 0개"로 잘 복구함
        }

        // 3. TTL 연장
        redisTemplate.expire(setKey, 3, TimeUnit.DAYS);
        redisTemplate.expire(countKey, 3, TimeUnit.DAYS);

        // 4. 현재 개수 조회
        Long currentCount = getCount(postId);

        // 5. MQ 전송
        sendToQueue(postId, userId, isLiked);

        return PostDto.LikeResponse.builder()
                .postId(postId)
                .isLiked(isLiked)
                .likeCount(currentCount)
                .build();
    }

    public boolean isLiked(Long postId, Long userId) {
        String setKey = getLikeSetKey(postId);
        if (!redisTemplate.hasKey(setKey)) {
            warmingWithLock(postId, setKey, getLikeCountKey(postId));
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(setKey, String.valueOf(userId)));
    }

    public Long getCount(Long postId) {
        String countKey = getLikeCountKey(postId);
        Object countObj = redisTemplate.opsForValue().get(countKey);

        if (countObj != null) {
            return Long.parseLong(countObj.toString());
        }
        warmingWithLock(postId, getLikeSetKey(postId), countKey);
        Object warmedCount = redisTemplate.opsForValue().get(countKey);
        return warmedCount != null ? Long.parseLong(warmedCount.toString()) : 0L;
    }

    private void warmingWithLock(Long postId, String setKey, String countKey) {
        RLock lock = redissonClient.getLock(getLockKey(postId));

        try {
            boolean available = lock.tryLock(3, 5, TimeUnit.SECONDS);

            if (!available) {
                // 락 획득 실패 시, 잠시 대기 후 리턴 -> retry 해야하지 않을까?
                return;
            }

            if (redisTemplate.hasKey(setKey)) {
                return;
            }

            // 1. DB 전체 로딩 (목적: 이미 좋아요했던 사람이 취소하려고 눌렀는데 등록이 되버리는 상황 방지)
            List<String> userIds = postLikeRepository.findAllUserIdsByPostId(postId)
                    .stream().map(String::valueOf).toList();

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
    public void sendToQueue(Long postId, Long userId, boolean isLiked) {
        LikeMessageDto message = new LikeMessageDto(postId, userId, isLiked);
        rabbitTemplate.convertAndSend(RabbitMQConfig.LIKE_EXCHANGE_NAME, RabbitMQConfig.LIKE_ROUTING_KEY, message);
        log.info("🐇 [MQ Send] PostId: {}, UserId: {}, Action: {}", postId, userId, isLiked ? "LIKE" : "UNLIKE");
    }
}