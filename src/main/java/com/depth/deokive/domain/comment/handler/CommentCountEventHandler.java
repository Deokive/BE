package com.depth.deokive.domain.comment.handler;

import com.depth.deokive.domain.comment.event.CommentCountEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentCountEventHandler {

    private final RedisTemplate<String, Long> longRedisTemplate;

    private static final String KEY_PREFIX = "comment:count:";

    @Async("messagingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCountEvent(CommentCountEvent event) {
        String key = KEY_PREFIX + event.getPostId();

        try {
            // 캐시 존재 시에만 업데이트 (Cache Miss 시 무시 -> 다음 읽기에서 로딩)
            if (longRedisTemplate.hasKey(key)) {
                longRedisTemplate.opsForValue().increment(key, event.getDelta());
                log.info("[Comment Count] Redis Updated -> PostId: {}, Delta: {}", event.getPostId(), event.getDelta());
            } else {
                log.debug("[Comment Count] Cache not exists, skipping -> PostId: {}", event.getPostId());
            }
        } catch (Exception e) {
            // Soft Fail: Redis 장애 시 로그만 남기고 예외 전파하지 않음
            log.warn("[Comment Count] Redis update failed -> PostId: {}, Error: {}", event.getPostId(), e.getMessage());
        }
    }
}
