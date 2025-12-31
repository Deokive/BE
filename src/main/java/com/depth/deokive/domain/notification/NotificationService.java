package com.depth.deokive.domain.notification;

import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;

    @Async("mailTaskExecutor")
    @Transactional
    public void sendFriendRequestNotification(Long receiverId, Long senderId) {
        try {
            // SEQ 1. User 조회
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

            // SEQ 2. 알람 -> 일단은 로그로만
            log.info("To: {}, From: {}, Message: 님이 친구 요청을 보냈습니다.", receiver.getNickname(), sender.getNickname());
        } catch (Exception e) {
            log.error("알람 전송 실패: ", e);
        }
    }
}
