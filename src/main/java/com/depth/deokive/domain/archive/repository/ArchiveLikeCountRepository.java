package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveLikeCountRepository extends JpaRepository<ArchiveLikeCount, Long> {
}

