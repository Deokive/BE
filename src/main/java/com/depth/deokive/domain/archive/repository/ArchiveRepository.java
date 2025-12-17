package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.Archive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRepository extends JpaRepository<Archive, Long>, ArchiveRepositoryCustom {
}

