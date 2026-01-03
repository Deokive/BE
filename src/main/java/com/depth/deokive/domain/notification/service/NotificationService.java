package com.depth.deokive.domain.notification.service;

import com.depth.deokive.domain.notification.repository.NotificationRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    // SSE 연결 시간(1시간)
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    /**
     * 클라이언트 로그인 시 연결 요청
     */
    public SseEmitter subscribe(Long userId) {
        // Emitter 생성
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // SEQ 1. 연결 -> 만료시 삭제
        emitter.onCompletion(() -> notificationRepository.deleteById(userId));
        emitter.onTimeout(() -> notificationRepository.deleteById(userId));

        // SEQ 2. 저장
        notificationRepository.save(userId, emitter);

        // SEQ 3. 연결 직후 더미 데이터 전송
        sendToClient(userId, "System", "connected");

        return emitter;
    }

    /**
     * 친구 요청 알림
     */
    public void sendFriendRequestNotification(Long receiverId, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        String message = sender.getNickname() + "님이 친구 요청을 보냈습니다.";

        // 실제 전송
        sendToClient(receiverId, "FriendRequest", message);
    }

    /**
     * 친구 수락 알림
     */
    public void sendFriendAcceptNotification(Long receiverId, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        String message = sender.getNickname() + "님이 친구 요청을 수락했습니다.";
        sendToClient(receiverId, "FriendAccept", message);
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
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                notificationRepository.deleteById(receiverId);
                log.error("SSE 연결 오류 발생: userId={}", receiverId);
            }
        }
    }
}
