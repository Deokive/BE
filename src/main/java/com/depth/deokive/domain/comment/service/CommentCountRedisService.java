package com.depth.deokive.domain.comment.service;

import com.depth.deokive.domain.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentCountRedisService {

    private final RedisTemplate<String, Long> longRedisTemplate;
    private final CommentRepository commentRepository;

    private static final String KEY_PREFIX = "comment:count:";

    /**
     * 댓글 수 조회 (Cache-Aside 패턴)
     * - Cache Hit: Redis 값 반환
     * - Cache Miss: DB에서 COUNT(*) 조회 후 캐시에 저장
     */
    public long getCommentCount(Long postId) {
        String key = KEY_PREFIX + postId;

        try {
            // 1. Redis에서 조회 시도
            Object cached = longRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }

            // 2. Cache Miss -> DB에서 COUNT 조회
            long count = commentRepository.countByPostId(postId);

            // 3. Redis에 저장 (TTL 없음 - Post 삭제 시 명시적 삭제)
            longRedisTemplate.opsForValue().set(key, count);
            log.info("[Comment Count] Cache warmed from DB -> PostId: {}, Count: {}", postId, count);

            return count;
        } catch (Exception e) {
            // Redis 장애 시 DB fallback
            log.warn("[Comment Count] Redis error, falling back to DB -> PostId: {}, Error: {}", postId, e.getMessage());
            return commentRepository.countByPostId(postId);
        }
    }

    /**
     * Post 삭제 시 캐시 정리
     */
    public void deleteCache(Long postId) {
        String key = KEY_PREFIX + postId;
        try {
            longRedisTemplate.delete(key);
            log.info("[Comment Count] Cache deleted -> PostId: {}", postId);
        } catch (Exception e) {
            // Soft Fail
            log.warn("[Comment Count] Cache delete failed -> PostId: {}, Error: {}", postId, e.getMessage());
        }
    }
}
