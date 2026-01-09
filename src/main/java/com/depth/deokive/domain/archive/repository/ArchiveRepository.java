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

    @Modifying(clearAutomatically = true) // 벌크 연산 후 1차 캐시 초기화 필수
    @Query(value = """
        UPDATE archive 
        SET hot_score = (
            (like_count * :w1 + LOG10(1 + view_count) * :w2) 
            * EXP(-:lambda * TIMESTAMPDIFF(HOUR, created_at, NOW()))
        )
        WHERE visibility = 'PUBLIC'
          AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) 
    """, nativeQuery = true)
    int updateHotScoreBulk(
            @Param("w1") int w1,
            @Param("w2") int w2,
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
    @Query("UPDATE Archive a SET a.viewCount = a.viewCount + :count WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id, @Param("count") Long count);
}