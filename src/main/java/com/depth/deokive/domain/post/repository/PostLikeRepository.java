package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    // Bulk Delete 용 (특정 게시글에 대한 다수 유저의 좋아요 이력)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    // 내 좋아요 삭제 (토글)
    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId AND pl.user.id = :userId")
    void deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query("select pl.user.id from PostLike pl where pl.post.id = :postId")
    List<Long> findAllUserIdsByPostId(@Param("postId") Long postId);
}

