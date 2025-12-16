package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.dto.GalleryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GalleryRepositoryCustom {
    // 특정 사용자 아카이브의 갤러리 목록 페이징
    Page<GalleryResponseDto> searchGalleries(Long archiveId, Pageable pageable);
}
