package com.depth.deokive.common.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.common.enums.ViewLikeDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async("messagingTaskExecutor")
    public void sendToQueue(ViewLikeDomain domain, Long targetId, Long userId, boolean isLiked) {
        LikeMessageDto message = new LikeMessageDto(targetId, userId, isLiked);
        rabbitTemplate.convertAndSend(domain.getExchangeName(), domain.getRoutingKey(), message);
        log.info("🐇 [MQ Send] Domain: {}, TargetId: {}, Action: {}", domain, targetId, isLiked ? "LIKE" : "UNLIKE");
    }
}
