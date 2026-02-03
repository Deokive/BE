package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.post.dto.RepostCompletedEvent;
import com.depth.deokive.system.config.sse.SseEmitterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Repost 완료 이벤트 Redis Subscriber
 * - Redis Pub/Sub 메시지 수신
 * - SSE로 클라이언트에게 전파
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepostSseSubscriber implements MessageListener {

    public static final String CHANNEL = "repost:completed";

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            RepostCompletedEvent event = objectMapper.readValue(body, RepostCompletedEvent.class);

            log.info("[SSE Subscriber] Received event: userId={}, repostId={}, status={}",
                    event.getUserId(), event.getRepostId(), event.getStatus());

            // SSE로 클라이언트에게 전송
            sseEmitterRegistry.send(event.getUserId(), "repost-completed", event);

        } catch (Exception e) {
            log.error("[SSE Subscriber] Failed to process message: {}", e.getMessage(), e);
        }
    }
}
