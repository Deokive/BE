package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArchiveLikeRepository extends JpaRepository<ArchiveLike, Long> {
    boolean existsByArchiveIdAndUserId(Long archiveId, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ArchiveLike al WHERE al.archive.id = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);

    // 좋아요 토글용: 특정 유저의 좋아요 삭제
    @Modifying
    @Query("DELETE FROM ArchiveLike al WHERE al.archive.id = :archiveId AND al.user.id = :userId")
    void deleteByArchiveIdAndUserId(@Param("archiveId") Long archiveId, @Param("userId") Long userId);

    // Redis Warming용: 해당 아카이브에 좋아요를 누른 모든 유저 ID 조회
    @Query("SELECT al.user.id FROM ArchiveLike al WHERE al.archive.id = :archiveId")
    List<Long> findAllUserIdsByArchiveId(@Param("archiveId") Long archiveId);
}
