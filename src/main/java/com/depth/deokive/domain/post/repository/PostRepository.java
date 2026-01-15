package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PostRepository extends JpaRepository<Post, Long> {
    // 1. 일반 핫스코어 업데이트 (최근 7일 이내)
    // Formula: (w1 * ln(1+l) + w2 * ln(1+v)) * e^(-lambda * a) [cite: 215]
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE post_stats
        SET hot_score = (
            ( :w1 * LN(1 + like_count) + :w2 * LN(1 + view_count) )
            * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
        )
        WHERE created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
    """, nativeQuery = true)
    int updateHotScoreStandard(
            @Param("w1") double w1,
            @Param("w2") double w2,
            @Param("lambda") double lambda
    );

    // 2. 게이트키퍼 패널티 적용 (7일 ~ 7일+1시간)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE post_stats
        SET hot_score = (
            (
                ( :w1 * LN(1 + like_count) + :w2 * LN(1 + view_count) )
                * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
            ) * 0.5
        )
        WHERE created_at BETWEEN DATE_SUB(NOW(), INTERVAL 169 HOUR) 
                             AND DATE_SUB(NOW(), INTERVAL 168 HOUR)
    """, nativeQuery = true)
    int applyHotScorePenalty(
            @Param("w1") double w1,
            @Param("w2") double w2,
            @Param("lambda") double lambda
    );

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE post_stats ps
        JOIN post_like_count plc ON ps.post_id = plc.post_id
        SET ps.like_count = plc.count
        WHERE ps.like_count != plc.count
    """, nativeQuery = true)
    int syncLikeCountsStatFromCountTable();
}