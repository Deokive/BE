package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {
    // 상세 조회용: Archive + User(작성자) Fetch Join
    @Query("SELECT a FROM Archive a JOIN FETCH a.user WHERE a.id = :id")
    Optional<Archive> findByIdWithUser(@Param("id") Long id);

    // 1. 일반 핫스코어 업데이트 (최근 7일 이내)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE archive_stats
        SET hot_score = (
            ( :w1 * LN(1 + like_count) + :w2 * LN(1 + view_count) )
            * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
        )
        WHERE visibility = 'PUBLIC'
          AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
    """, nativeQuery = true)
    int updateHotScoreStandard(
            @Param("w1") double w1,
            @Param("w2") double w2,
            @Param("lambda") double lambda
    );

    // 2. 게이트키퍼 패널티 적용 (7일 ~ 7일+1시간)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE archive_stats
        SET hot_score = (
            (
                ( :w1 * LN(1 + like_count) + :w2 * LN(1 + view_count) )
                * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
            ) * 0.5
        )
        WHERE visibility = 'PUBLIC'
          AND created_at BETWEEN DATE_SUB(NOW(), INTERVAL 169 HOUR) 
                             AND DATE_SUB(NOW(), INTERVAL 168 HOUR)
    """, nativeQuery = true)
    int applyHotScorePenalty(
            @Param("w1") double w1,
            @Param("w2") double w2,
            @Param("lambda") double lambda
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Archive a SET a.badge = :targetBadge " +
            "WHERE a.createdAt <= :cutOffDate " +
            "AND a.badge IN :targetBadges"
    ) // 타겟보다 낮은 등급들만 골라서 업데이트
    int updateBadgesBulk(
        @Param("targetBadge") Badge targetBadge,
        @Param("cutOffDate") LocalDateTime cutOffDate,
        @Param("targetBadges") List<Badge> targetBadges
    );

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE archive_stats s
        JOIN archive_like_count lc ON s.archive_id = lc.archive_id
        SET s.like_count = lc.count
        WHERE s.like_count != lc.count
    """, nativeQuery = true)
    int syncLikeCountsStatFromCountTable();
}