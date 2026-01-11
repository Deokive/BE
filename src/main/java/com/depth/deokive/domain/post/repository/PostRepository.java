package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Post;
import jakarta.transaction.Transactional;
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

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE post_stats ps
        JOIN post_like_count plc ON ps.post_id = plc.post_id
        SET ps.like_count = plc.count
        WHERE ps.like_count != plc.count
    """, nativeQuery = true)
    int syncLikeCountsFromTable();
}