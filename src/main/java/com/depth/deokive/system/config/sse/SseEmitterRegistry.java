package com.depth.deokive.system.config.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE Emitter 관리 레지스트리
 * - userId별 SSE 연결 관리
 * - 스레드 세이프 (ConcurrentHashMap + CopyOnWriteArrayList)
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private static final long DEFAULT_TIMEOUT = 30_000L; // 30초

    // userId → List<SseEmitter> (한 유저가 여러 탭에서 접속 가능)
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결 등록
     */
    public SseEmitter subscribe(Long userId) {
        return subscribe(userId, DEFAULT_TIMEOUT);
    }

    /**
     * SSE 연결 등록 (커스텀 타임아웃)
     */
    public SseEmitter subscribe(Long userId, long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);

        // 유저별 emitter 리스트에 추가
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결 종료 시 정리
        emitter.onCompletion(() -> {
            log.debug("[SSE] Emitter completed for userId={}", userId);
            remove(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("[SSE] Emitter timeout for userId={}", userId);
            remove(userId, emitter);
        });

        emitter.onError(e -> {
            log.debug("[SSE] Emitter error for userId={}: {}", userId, e.getMessage());
            remove(userId, emitter);
        });

        // 연결 직후 heartbeat 전송 (503 방지)
        sendHeartbeat(emitter);

        log.info("[SSE] Subscribed userId={}, active emitters={}", userId, getEmitterCount(userId));
        return emitter;
    }

    /**
     * 특정 유저에게 이벤트 전송
     */
    public void send(Long userId, String eventName, Object data) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("[SSE] No active emitters for userId={}", userId);
            return;
        }

        // 모든 연결에 이벤트 전송 (같은 유저가 여러 탭에서 접속한 경우)
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                log.debug("[SSE] Sent event '{}' to userId={}", eventName, userId);
            } catch (IOException e) {
                log.debug("[SSE] Failed to send event to userId={}: {}", userId, e.getMessage());
                remove(userId, emitter);
            }
        }
    }

    /**
     * Heartbeat 전송 (연결 유지 확인)
     */
    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("connected"));
        } catch (IOException e) {
            log.debug("[SSE] Failed to send heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Emitter 제거
     */
    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    /**
     * 특정 유저의 활성 연결 수
     */
    public int getEmitterCount(Long userId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null ? userEmitters.size() : 0;
    }

    /**
     * 전체 활성 연결 수
     */
    public int getTotalEmitterCount() {
        return emitters.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}
