package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.entity.Gallery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryRepository extends JpaRepository<Gallery, Long>, GalleryRepositoryCustom {
}
