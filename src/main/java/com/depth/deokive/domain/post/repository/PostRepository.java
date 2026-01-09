package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE post
        SET hot_score = (
            (like_count * :w1 + LOG10(1 + view_count) * :w2)
            * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
        )
        WHERE created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
    """, nativeQuery = true)
    int updateHotScoreBulk(
            @Param("w1") int w1,
            @Param("w2") int w2,
            @Param("lambda") double lambda
    );

    // 조회수 증가 (Bulk Update)
    @Modifying(clearAutomatically = true) // 영속성 컨텍스트 초기화
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :count WHERE p.id = :postId")
    void incrementViewCount(@Param("postId") Long postId, @Param("count") Long count);
}