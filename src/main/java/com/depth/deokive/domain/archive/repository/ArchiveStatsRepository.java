package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.entity.ArchiveStats;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ArchiveStatsRepository extends JpaRepository<ArchiveStats, Long> {

    // 1. 조회수 증가 (Atomic Update)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveStats s SET s.viewCount = s.viewCount + :count WHERE s.id = :id")
    void incrementViewCount(@Param("id") Long id, @Param("count") Long count);

    // 2. 반정규화 필드 동기화 (Visibility)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveStats s SET s.visibility = :visibility WHERE s.id = :id")
    void syncVisibility(@Param("id") Long id, @Param("visibility") Visibility visibility);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveStats s SET s.badge = :targetBadge " +
            "WHERE s.createdAt <= :cutOffDate " +
            "AND s.badge IN :targetBadges")
    int updateBadgesBulkInStats(
            @Param("targetBadge") Badge targetBadge,
            @Param("cutOffDate") LocalDateTime cutOffDate,
            @Param("targetBadges") List<Badge> targetBadges
    );

    // Scheduler Sync -> 좋아요 수 업데이트
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveStats s SET s.likeCount = :count WHERE s.id = :id")
    void updateLikeCount(@Param("id") Long id, @Param("count") Long count);
}