package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.post.dto.MetadataProvider;
import com.depth.deokive.domain.post.dto.OgMetadata;
import com.depth.deokive.domain.post.dto.RepostCompletedEvent;
import com.depth.deokive.domain.post.dto.RepostOgExtractionMessage;
import com.depth.deokive.domain.post.util.strategy.MetadataProviderFactory;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.config.rabbitmq.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Repost OG 메타데이터 추출 Consumer
 *
 * [성능 최적화 핵심]
 * - OG 추출(네트워크 I/O)과 DB 업데이트 분리
 * - OG 추출 중에는 DB 커넥션 점유하지 않음
 * - DB Pool(6개)과 무관하게 concurrency(60개) 활용 가능
 *
 * [병렬 처리 전략]
 * - concurrency="120": 동시에 120개 메시지 처리 (Virtual Threads 활용)
 * - prefetchCount=120: 한 번에 120개 메시지를 메모리에 로드
 *
 * [성능 예상]
 * - 120개 요청: 120개 동시 처리 * ~1.5초 = 1.5초 내 완료 (1배치)
 * - 300개 요청: 120개 동시 처리 * ~1.5초 = 4초 내 완료 (3배치)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepostOgConsumer {

    // DB 업데이트는 별도 서비스로 분리 (트랜잭션 프록시 적용을 위해)
    private final RepostOgUpdateService repostOgUpdateService;

    // SSE 알림용 Redis Pub/Sub
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Factory 주입
    private final MetadataProviderFactory metadataProviderFactory;

    /**
     * OG 메타데이터 추출 및 DB 업데이트
     *
     * [핵심 변경]
     * - @Transactional 제거: OG 추출(1.5초) 동안 DB 커넥션 점유 방지
     * - DB 작업은 별도 @Transactional 메서드로 분리
     * - 결과: DB Pool(6개)와 무관하게 60개 동시 처리 가능
     *
     * [Before] DB Pool=6, concurrency=10 → 실제 동시 처리 6개 → 120개/20초
     * [After]  DB Pool=6, concurrency=120 → 실제 동시 처리 120개 → 120개/1.5초 (1배치)
     */
    @RabbitListener(
            queues = RabbitMQConfig.REPOST_OG_QUEUE,
            containerFactory = "prefetchContainerFactory",
            concurrency = "120"  // 120개 동시 처리 (OG 추출은 DB 커넥션 불필요)
    )
    @ExecutionTime("OG 추출 및 업데이트")
    // @Transactional 제거 - OG 추출 중 DB 커넥션 점유 방지
    public void extractAndUpdateOgMetadata(RepostOgExtractionMessage message) {
        Long repostId = message.getRepostId();
        Long userId = message.getUserId();
        String url = message.getUrl();

        log.info("[OG Consumer] Repost ID={} 처리 시작", repostId);

        try {
            // 1️⃣ Factory를 통해 적절한 Provider 선택 (Strategy Pattern)
            MetadataProvider provider = metadataProviderFactory.getProvider(url);
            log.info("👉 Selected Strategy: {}", provider.getClass().getSimpleName());

            // 2️⃣ 메타데이터 추출 실행
            OgMetadata metadata = provider.extract(url);

            String title = metadata.getTitle();
            String thumbnailUrl = metadata.getImageUrl();

            // Fallback: 제목 없으면 도메인 이름 사용
            if (title == null || title.isBlank()) {
                title = extractDomainName(url);
            }

            // SEQ 2. DB 업데이트 (별도 서비스 + 트랜잭션, ~50ms)
            // 이 시점에만 DB 커넥션 사용
            repostOgUpdateService.completeMetadata(repostId, title, thumbnailUrl);

            // SEQ 3. SSE 알림 발행 (Redis Pub/Sub)
            publishSseEvent(RepostCompletedEvent.completed(userId, repostId, title, thumbnailUrl));

            log.info("[OG Consumer] Repost ID={} 완료 (title={}, thumbnail={})",
                    repostId, title != null, thumbnailUrl != null);

        } catch (Exception e) {
            log.error("[OG Consumer] 실패: {}", e.getMessage());
            repostOgUpdateService.markAsFailed(repostId, "OG 추출 실패: " + e.getMessage());
            publishSseEvent(RepostCompletedEvent.failed(userId, repostId));
        }
    }

    /**
     * SSE 이벤트를 Redis Pub/Sub으로 발행
     * - 스케일아웃 시 여러 인스턴스에 이벤트 전파
     */
    private void publishSseEvent(RepostCompletedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(RepostSseSubscriber.CHANNEL, json);
            log.debug("[OG Consumer] SSE event published: repostId={}", event.getRepostId());
        } catch (JsonProcessingException e) {
            log.error("[OG Consumer] Failed to serialize SSE event", e);
        }
    }

    /**
     * URL에서 도메인 이름 추출
     */
    private String extractDomainName(String url) {
        try {
            return new URI(url).toURL().getHost();
        } catch (URISyntaxException | MalformedURLException e) {
            return "Unknown";
        }
    }
}
