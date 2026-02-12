package com.depth.deokive.common.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.common.enums.ViewLikeDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeMessagePublisher {

    public static final String FALLBACK_KEY = "like:mq:fallback";

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Async("messagingTaskExecutor")
    public void sendToQueue(ViewLikeDomain domain, Long targetId, Long userId, boolean isLiked) {
        LikeMessageDto message = new LikeMessageDto(targetId, userId, isLiked);
        try {
            rabbitTemplate.convertAndSend(domain.getExchangeName(), domain.getRoutingKey(), message);
            log.info("🐇 [MQ Send] Domain: {}, TargetId: {}, Action: {}", domain, targetId, isLiked ? "LIKE" : "UNLIKE");
        } catch (Exception e) {
            String entry = domain.name() + ":" + targetId + ":" + userId + ":" + isLiked;
            stringRedisTemplate.opsForList().rightPush(FALLBACK_KEY, entry);
            log.warn("⚠️ [MQ Fallback] Saved to Redis: {} (reason: {})", entry, e.getMessage());
        }
    }
}
