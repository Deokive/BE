package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostStats;
import com.depth.deokive.domain.post.entity.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface PostStatsRepository extends JpaRepository<PostStats, Long> {
    // [Scheduler] 1. 조회수 증가 (Atomic Update)
    // 현재 값에 delta를 더함. X-Lock 점유 시간 극소화.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.viewCount = ps.viewCount + :count WHERE ps.id = :postId")
    void incrementViewCountForWriteBack(@Param("postId") Long postId, @Param("count") Long count);

    // [User Action] 3. 카테고리 동기화
    // Post 수정 시 카테고리가 바뀌면 여기도 바꿔야 정렬 인덱스가 안 깨짐.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.category = :category WHERE ps.id = :postId")
    void syncUpdateCategory(@Param("postId") Long postId, @Param("category") Category category);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostStats ps SET ps.likeCount = :count WHERE ps.id = :postId")
    void updateLikeCount(@Param("postId") Long postId, @Param("count") Long count);
}
