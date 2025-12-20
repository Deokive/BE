package com.depth.deokive.domain.gallery.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryQueryRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GalleryService {

    // private final ArchiveRepository archiveRepository;
    private final GalleryQueryRepository galleryQueryRepository;
    private final GalleryBookRepository galleryBookRepository;

    @ExecutionTime
    @Transactional(readOnly = true)
    public GalleryDto.PageListResponse getGalleries(Long archiveId, Pageable pageable) {

        // Archive archive = archiveRepository.findById(archiveId)
        //         .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND)); // 예외처리는 프로젝트에 맞게
        String galleryBookTitle = galleryBookRepository.findTitleByArchiveId(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        Page<GalleryDto.Response> page = galleryQueryRepository.searchGalleriesByArchive(archiveId, pageable);

        return GalleryDto.PageListResponse.of(galleryBookTitle, page);
    }
}