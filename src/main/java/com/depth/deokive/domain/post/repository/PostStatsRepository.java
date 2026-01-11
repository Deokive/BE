package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostStats;
import com.depth.deokive.domain.post.entity.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface PostStatsRepository extends JpaRepository<PostStats, Long> {
    // [Scheduler] 1. 조회수 증가 (Atomic Update)
    // 현재 값에 delta를 더함. X-Lock 점유 시간 극소화.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.viewCount = ps.viewCount + :count WHERE ps.id = :postId")
    void incrementViewCount(@Param("postId") Long postId, @Param("count") Long count);

    // [Scheduler] 2. 핫스코어 업데이트 (Atomic Update)
    // 계산된 점수를 바로 반영.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.hotScore = :score WHERE ps.id = :postId")
    void updateHotScore(@Param("postId") Long postId, @Param("score") Double score);

    // [User Action] 3. 카테고리 동기화
    // Post 수정 시 카테고리가 바뀌면 여기도 바꿔야 정렬 인덱스가 안 깨짐.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.category = :category WHERE ps.id = :postId")
    void updateCategory(@Param("postId") Long postId, @Param("category") Category category);

    // [Scheduler] 4. 좋아요 수 동기화 (PostLikeCount -> PostStats)
    // 특정 게시글의 좋아요 수를 강제로 맞춤 (10초 주기 Sync용)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.likeCount = :count WHERE ps.id = :postId")
    void syncLikeCount(@Param("postId") Long postId, @Param("count") Long count);

    // [Scheduler] 5. HotScore 계산 대상 조회 (커버링 인덱스 활용)
    // 최근 7일(cutoffDate) 이내에 생성된 게시글의 ID만 빠르게 가져옴
    @Query("SELECT ps.id FROM PostStats ps WHERE ps.createdAt >= :cutoffDate")
    List<Long> findAllIdsByCreatedAtAfter(@Param("cutoffDate") LocalDateTime cutoffDate);
}
