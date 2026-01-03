package com.depth.deokive.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class NotificationRepository {
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SEQ 1. Emitter 저장
    public SseEmitter save(Long userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
        return emitter;
    }

    // SEQ 2. Emitter 삭제
    public void deleteById(Long userId) {
        emitters.remove(userId);
    }

    // SEQ 3. Emitter 조회
    public SseEmitter get(Long userId) {
        return emitters.get(userId);
    }
}
