package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveLikeRepository extends JpaRepository<ArchiveLike, Long> {
    boolean existsByArchiveIdAndUserId(Long archiveId, Long userId);
}
