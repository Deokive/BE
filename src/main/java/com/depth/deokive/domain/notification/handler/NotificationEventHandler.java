package com.depth.deokive.domain.notification.handler;

import com.depth.deokive.domain.notification.dto.event.NotificationEvent;
import com.depth.deokive.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("[Notification] 발송 요청 감지 -> Type: {}, Target: {}", event.getType(), event.getReceiverId());

        notificationService.send(
                event.getReceiverId(),
                event.getType().name(),
                event
        );
    }
}
