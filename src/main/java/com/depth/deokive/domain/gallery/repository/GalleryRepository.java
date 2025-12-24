package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.entity.Gallery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {
    @Modifying(clearAutomatically = true) // 1차 캐시 정합성을 위한 clear 옵션 On
    @Query("DELETE FROM Gallery g WHERE g.id IN :ids AND g.archiveId = :archiveId")
    void deleteByIdsAndArchiveId(@Param("ids") List<Long> ids, @Param("archiveId") Long archiveId);

    @Modifying
    @Query("DELETE FROM Gallery g WHERE g.archiveId = :archiveId")
    void deleteByArchiveId(@Param("archiveId") Long archiveId);
}