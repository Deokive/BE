package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveMeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArchiveRepositoryCustom {
    // 내 아카이브 조회(Visibility 무시)
    Page<ArchiveMeResponseDto> searchMyArchive(Pageable pageable, Long userId);
}
