package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeCountRepository extends JpaRepository<PostLikeCount, Long> {

    // [User Action] 1. 좋아요 1 증가
    // PostLikeCount가 존재한다고 가정하고 수행 (Service에서 생성 보장)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostLikeCount plc SET plc.count = plc.count + 1 WHERE plc.postId = :postId")
    void increaseCount(@Param("postId") Long postId);

    // [User Action] 2. 좋아요 1 감소
    // 음수가 되지 않도록 방어 로직 추가 (AND count > 0)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostLikeCount plc SET plc.count = plc.count - 1 WHERE plc.postId = :postId AND plc.count > 0")
    void decreaseCount(@Param("postId") Long postId);
}