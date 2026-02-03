package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.post.dto.RepostOgExtractionMessage;
import com.depth.deokive.system.config.rabbitmq.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Repost OG 메타데이터 추출 요청을 RabbitMQ에 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepostOgProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * OG 추출 메시지를 큐에 발행
     * - 비동기 처리: Consumer가 백그라운드에서 처리
     * - userId: SSE 알림 전송에 사용
     */
    public void requestOgExtraction(Long repostId, Long userId, String url) {
        RepostOgExtractionMessage message = new RepostOgExtractionMessage(repostId, userId, url);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.REPOST_OG_EXCHANGE,
                RabbitMQConfig.REPOST_OG_ROUTING_KEY,
                message
        );

        log.info("[OG Producer] Repost ID={}, userId={}, URL={} 추출 요청 발행", repostId, userId, url);
    }
}
