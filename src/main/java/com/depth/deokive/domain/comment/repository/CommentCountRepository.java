package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.CommentCount;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentCountRepository extends JpaRepository<CommentCount, Long> {
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO comment_count (post_id, count) VALUES (:postId, 1) " +
            "ON DUPLICATE KEY UPDATE count = count + 1", nativeQuery = true)
    void increaseCount(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE CommentCount c SET c.count = c.count - 1 WHERE c.postId = :postId AND c.count > 0")
    void decreaseCount(@Param("postId") Long postId);

}

