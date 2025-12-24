package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.entity.GalleryBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GalleryBookRepository extends JpaRepository<GalleryBook, Long> {
    @Query("SELECT gb.title FROM GalleryBook gb WHERE gb.archive.id = :archiveId")
    Optional<String> findTitleByArchiveId(@Param("archiveId") Long archiveId);
}
