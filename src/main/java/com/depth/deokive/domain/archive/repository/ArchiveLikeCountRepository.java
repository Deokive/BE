package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveLikeCountRepository extends JpaRepository<ArchiveLikeCount, Long> {

    // TODO: 좋아요 기능 구현 시 적용
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveLikeCount alc SET alc.count = alc.count + 1 WHERE alc.archiveId = :id")
    void increaseCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ArchiveLikeCount alc SET alc.count = alc.count - 1 WHERE alc.archiveId = :id AND alc.count > 0")
    void decreaseCount(@Param("id") Long id);
}