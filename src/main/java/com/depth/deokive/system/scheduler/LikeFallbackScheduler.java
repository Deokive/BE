package com.depth.deokive.system.scheduler;

import com.depth.deokive.common.enums.ViewLikeDomain;
import com.depth.deokive.common.service.LikeMessagePublisher;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MQ 전송 실패 시 Redis에 기록된 fallback 데이터를 DB에 직접 반영하는 스케줄러.
 *
 * 흐름: LikeMessagePublisher → MQ 실패 → Redis List(like:mq:fallback) → 이 스케줄러 → DB Write
 * MQ 복구를 기다리지 않고, Redis Write-Back 패턴으로 직접 DB에 bulk 처리한다.
 *
 * 단일 트랜잭션으로 처리하여 DB 커넥션 1개만 사용.
 * - UNLIKE: 단일 DELETE 쿼리 (tuple IN clause)
 * - LIKE: 존재 여부 필터링 후 saveAll() → Auditing 정상 동작, FK 위반 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFallbackScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final ArchiveRepository archiveRepository;
    private final ArchiveLikeRepository archiveLikeRepository;

    @Scheduled(cron = "${scheduler.like-fallback-cron}")
    @Transactional
    public void processFailedLikeMessages() {
        List<String> entries = popAll();
        if (entries.isEmpty()) return;

        Map<String, Boolean> deduped = deduplicateEntries(entries);
        log.info("🔄 [Like Fallback] Processing {} entries (deduped from {})", deduped.size(), entries.size());

        // 1. Parse & Group
        List<long[]> postLikes = new ArrayList<>();
        List<long[]> postUnlikes = new ArrayList<>();
        List<long[]> archiveLikes = new ArrayList<>();
        List<long[]> archiveUnlikes = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : deduped.entrySet()) {
            String[] parts = entry.getKey().split(":");
            long targetId = Long.parseLong(parts[1]);
            long userId = Long.parseLong(parts[2]);
            long[] pair = {targetId, userId};

            if (ViewLikeDomain.valueOf(parts[0]) == ViewLikeDomain.POST) {
                (entry.getValue() ? postLikes : postUnlikes).add(pair);
            } else {
                (entry.getValue() ? archiveLikes : archiveUnlikes).add(pair);
            }
        }

        // 2. UNLIKE: 단일 DELETE 쿼리로 bulk 삭제
        bulkDelete("post_like", "post_id", postUnlikes);
        bulkDelete("archive_like", "archive_id", archiveUnlikes);

        // 3. LIKE: 존재 여부 필터링 후 saveAll
        List<PostLike> newPostLikes = new ArrayList<>();
        for (long[] p : postLikes) {
            if (postRepository.existsById(p[0]) && !postLikeRepository.existsByPostIdAndUserId(p[0], p[1])) {
                Post post = postRepository.getReferenceById(p[0]);
                User user = userRepository.getReferenceById(p[1]);
                newPostLikes.add(PostLike.builder().post(post).user(user).build());
            }
        }

        List<ArchiveLike> newArchiveLikes = new ArrayList<>();
        for (long[] p : archiveLikes) {
            if (archiveRepository.existsById(p[0]) && !archiveLikeRepository.existsByArchiveIdAndUserId(p[0], p[1])) {
                Archive archive = archiveRepository.getReferenceById(p[0]);
                User user = userRepository.getReferenceById(p[1]);
                newArchiveLikes.add(ArchiveLike.builder().archive(archive).user(user).build());
            }
        }

        postLikeRepository.saveAll(newPostLikes);
        archiveLikeRepository.saveAll(newArchiveLikes);

        int total = postUnlikes.size() + archiveUnlikes.size() + newPostLikes.size() + newArchiveLikes.size();
        int skipped = deduped.size() - total;
        log.info("✅ [Like Fallback] Completed: {} processed, {} skipped (deleted target)", total, skipped);
    }

    /**
     * 단일 DELETE 쿼리로 bulk 삭제.
     * DELETE FROM {table} WHERE ({targetColumn}, user_id) IN ((?,?), (?,?), ...)
     */
    private void bulkDelete(String table, String targetColumn, List<long[]> pairs) {
        if (pairs.isEmpty()) return;

        String placeholders = pairs.stream()
                .map(p -> "(?,?)")
                .collect(Collectors.joining(","));

        String sql = "DELETE FROM " + table + " WHERE (" + targetColumn + ", user_id) IN (" + placeholders + ")";

        Object[] params = pairs.stream()
                .flatMap(p -> Stream.of(p[0], p[1]))
                .toArray();

        int deleted = jdbcTemplate.update(sql, params);
        log.info("🗑️ [Like Fallback] Bulk deleted {} rows from {}", deleted, table);
    }

    private List<String> popAll() {
        List<String> entries = new ArrayList<>();
        String entry;
        while ((entry = stringRedisTemplate.opsForList().leftPop(LikeMessagePublisher.FALLBACK_KEY)) != null) {
            entries.add(entry);
        }
        return entries;
    }

    /**
     * 동일 (domain:targetId:userId) 키에 대해 마지막 action만 유지.
     * LinkedHashMap으로 순서를 보존하되, 같은 키는 최신 값으로 덮어씀.
     */
    private Map<String, Boolean> deduplicateEntries(List<String> entries) {
        Map<String, Boolean> deduped = new LinkedHashMap<>();
        for (String entry : entries) {
            int lastColon = entry.lastIndexOf(':');
            String key = entry.substring(0, lastColon);
            boolean isLiked = Boolean.parseBoolean(entry.substring(lastColon + 1));
            deduped.put(key, isLiked);
        }
        return deduped;
    }
}
