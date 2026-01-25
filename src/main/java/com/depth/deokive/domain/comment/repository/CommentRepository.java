package com.depth.deokive.domain.comment.repository;

import com.depth.deokive.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    long countByPostId(Long postId);

    /** 대댓글 먼저 삭제 (parent_id IS NOT NULL) */
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId AND c.parent IS NOT NULL")
    void deleteRepliesByPostId(@Param("postId") Long postId);

    /** 부모 댓글 삭제 (parent_id IS NULL) */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL")
    void deleteParentsByPostId(@Param("postId") Long postId);
}

