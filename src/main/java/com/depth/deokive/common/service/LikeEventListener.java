package com.depth.deokive.common.service;

import com.depth.deokive.common.dto.LikeMessageDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.ArchiveLike;
import com.depth.deokive.domain.archive.repository.ArchiveLikeRepository;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
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

    private final UserRepository userRepository;

    // Post Domain
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    // Archive Domain
    private final ArchiveRepository archiveRepository;
    private final ArchiveLikeRepository archiveLikeRepository;

    // OOM 방지를 위해 containerFactory 설정 적용
    @RabbitListener(queues = "#{postLikeQueue.name}", containerFactory = "prefetchContainerFactory")
    @Transactional
    public void handlePostLikeEvent(LikeMessageDto message) {
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
            // 주의: 여기서 PostLikeCount(카운트 테이블)을 업데이트 X
            // 락 경쟁을 피하기 위해 카운트는 스케줄러가 Redis -> DB로 일괄 동기화

        } catch (Exception e) {
            log.error("🔴 [Post: MQ Consume Fail] {}", e.getMessage(), e);
            // 필요 시 Dead Letter Queue 처리 로직 추가
        }
    }

    @RabbitListener(queues = "#{archiveLikeQueue.name}", containerFactory = "prefetchContainerFactory")
    @Transactional
    public void handleArchiveLike(LikeMessageDto message) {
        try {
            Long archiveId = message.getId();
            Long userId = message.getUserId();

            if (message.isLiked()) {
                if (!archiveLikeRepository.existsByArchiveIdAndUserId(archiveId, userId)) {
                    Archive archive = archiveRepository.getReferenceById(archiveId);
                    User user = userRepository.getReferenceById(userId);
                    archiveLikeRepository.save(ArchiveLike.builder().archive(archive).user(user).build());
                }
            } else {
                // ArchiveLikeRepository에 deleteByArchiveIdAndUserId 메서드 필요
                archiveLikeRepository.deleteByArchiveIdAndUserId(archiveId, userId);
            }
        } catch (Exception e) {
            log.error("🔴 [Archive: MQ Consume Fail] {}", e.getMessage(), e);
        }
    }
}