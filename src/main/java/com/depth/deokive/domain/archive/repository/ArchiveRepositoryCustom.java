package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArchiveRepositoryCustom {
    Page<ArchiveResponseDto> searchArchiveList(Pageable pageable, Long userId);
}
