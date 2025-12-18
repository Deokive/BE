package com.depth.deokive.domain.archive.service;

import com.depth.deokive.domain.archive.dto.ArchiveMeResponseDto;
import com.depth.deokive.domain.archive.dto.CustomPageResponse;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveRepository archiveRepository;

    public CustomPageResponse<ArchiveMeResponseDto> getMyArchiveList(Pageable pageable, Long userId) {
        // 1. querydsl (userId 넘겨서 조회)
        Page<ArchiveMeResponseDto> page = archiveRepository.searchMyArchive(pageable, userId);

        // 2. 인덱스 범위 체크
        if(page.getTotalElements() > 0 && pageable.getPageNumber() >= page.getTotalPages()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 페이지는 존재하지 않습니다.");
        }

        // 3. 커스텀 응답 포맷
        return CustomPageResponse.of(page);
    }
}
