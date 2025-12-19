package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.entity.GalleryBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryBookRepository extends JpaRepository<GalleryBook, Long> {
}
