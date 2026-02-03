package com.depth.deokive.domain.post.controller;

import com.depth.deokive.system.config.sse.SseEmitterRegistry;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
@Tag(name = "SSE", description = "Server-Sent Events API")
public class RepostSseController {

    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * Repost OG 추출 완료 이벤트 구독
     *
     * [사용 방법 - 프론트엔드]
     * ```javascript
     * const eventSource = new EventSource('/api/v1/sse/repost', {
     *   withCredentials: true
     * });
     *
     * eventSource.addEventListener('repost-completed', (e) => {
     *   const { repostId, status, title, thumbnailUrl } = JSON.parse(e.data);
     *   updateRepostCard(repostId, { status, title, thumbnailUrl });
     * });
     *
     * eventSource.addEventListener('heartbeat', (e) => {
     *   console.log('SSE connected');
     * });
     *
     * eventSource.onerror = () => {
     *   eventSource.close();
     *   setTimeout(() => reconnect(), 3000);
     * };
     * ```
     *
     * [이벤트 타입]
     * - heartbeat: 연결 성공 확인 (연결 직후 전송)
     * - repost-completed: OG 추출 완료 (status: COMPLETED/FAILED)
     *
     * [타임아웃]
     * - 30초 후 자동 연결 종료
     * - 프론트엔드에서 필요시 재연결 처리
     */
    @GetMapping(value = "/repost", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Repost SSE 구독", description = "Repost OG 추출 완료 이벤트를 실시간으로 수신합니다.")
    public SseEmitter subscribeRepostEvents(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long userId = userPrincipal.getUserId();
        log.info("[SSE] Subscribe request from userId={}", userId);

        return sseEmitterRegistry.subscribe(userId);
    }
}
