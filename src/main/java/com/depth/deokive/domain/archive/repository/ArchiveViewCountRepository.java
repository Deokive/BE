package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.ArchiveViewCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveViewCountRepository extends JpaRepository<ArchiveViewCount, Long> {
}
