package com.depth.deokive.domain.notification.service;

import com.depth.deokive.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // SSE 연결 시간(1시간)
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    /**
     * 클라이언트 로그인 시 연결 요청
     */
    public SseEmitter subscribe(Long userId) {
        // Emitter 생성
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        notificationRepository.save(userId, emitter);

        // SEQ 1. 연결 -> 만료시 삭제
        emitter.onCompletion(() -> notificationRepository.deleteById(userId));
        emitter.onTimeout(() -> notificationRepository.deleteById(userId));
        emitter.onError((e) -> notificationRepository.deleteById(userId));


        // SEQ 2. 연결 직후 더미 데이터 전송
        sendToClient(userId, "connect", "connected! [userId=" + userId + "]");

        return emitter;
    }

    /**
     * 알림 전송
     */
    public void send(Long receiverId, String eventName, Object data) {
        sendToClient(receiverId, eventName, data);
    }

    /**
     * 내부 전송 로직
     */
    public void sendToClient(Long receiverId, String eventName, Object data) {
        SseEmitter emitter = notificationRepository.get(receiverId);

        // 로그인 X -> 전송 X
        if(emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                notificationRepository.deleteById(receiverId);
                log.error("SSE 연결 오류 발생: userId={}", receiverId);
            }
        }
    }

    /**
     * heartbeat -> 30초마다 ping 전송
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        notificationRepository.findAll().forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data(""));
            } catch (IOException e){
                notificationRepository.deleteById(userId);
            }
        });
    }
}