package com.depth.deokive.common.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostLike;
import com.depth.deokive.domain.post.repository.PostLikeRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.rabbitmq.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventListener {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository; // getReferenceById 사용
    private final UserRepository userRepository; // getReferenceById 사용

    // OOM 방지를 위해 containerFactory 설정 적용
    @RabbitListener(queues = RabbitMQConfig.LIKE_QUEUE_NAME, containerFactory = "prefetchContainerFactory")
    @Transactional
    public void handleLikeEvent(LikeMessageDto message) {
        try {
            Long postId = message.getId();
            Long userId = message.getUserId();

            if (message.isLiked()) {
                // INSERT (중복 발생 시 무시하거나 Exception 처리)
                // Proxy 객체 사용으로 SELECT 쿼리 방지
                if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
                    Post post = postRepository.getReferenceById(postId);
                    User user = userRepository.getReferenceById(userId);
                    postLikeRepository.save(PostLike.builder().post(post).user(user).build());
                }
            } else {
                // DELETE
                postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            }
            // 주의: 여기서 PostLikeCount(카운트 테이블)을 업데이트하지 않습니다.
            // 락 경쟁을 피하기 위해 카운트는 스케줄러가 Redis -> DB로 일괄 동기화합니다.

        } catch (Exception e) {
            log.error("🔴 [MQ Consume Fail] {}", e.getMessage(), e);
            // 필요 시 Dead Letter Queue 처리 로직 추가
        }
    }
}