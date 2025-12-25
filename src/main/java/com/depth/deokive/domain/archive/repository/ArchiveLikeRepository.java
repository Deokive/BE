package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveLikeRepository extends JpaRepository<ArchiveLike, Long> {
    boolean existsByArchiveIdAndUserId(Long archiveId, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ArchiveLike al WHERE al.archive.id = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);
}
