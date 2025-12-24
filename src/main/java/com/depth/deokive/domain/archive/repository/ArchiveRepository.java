package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {
    // 상세 조회용: Archive + User(작성자) Fetch Join
    @Query("SELECT a FROM Archive a JOIN FETCH a.user WHERE a.id = :id")
    Optional<Archive> findByIdWithUser(@Param("id") Long id);
}