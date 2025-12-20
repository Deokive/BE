package com.depth.deokive.domain.gallery.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.entity.Gallery;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryQueryRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.system.config.aop.ExecutionTime;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final GalleryQueryRepository galleryQueryRepository;
    private final GalleryBookRepository galleryBookRepository;
    private final FileRepository fileRepository;
    private final ArchiveRepository archiveRepository;
    private final GalleryRepository galleryRepository;

    @ExecutionTime
    @Transactional(readOnly = true)
    public GalleryDto.PageListResponse getGalleries(Long archiveId, Pageable pageable) {

        String galleryBookTitle = galleryBookRepository.findTitleByArchiveId(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        Page<GalleryDto.Response> page = galleryQueryRepository.searchGalleriesByArchive(archiveId, pageable);

        return GalleryDto.PageListResponse.of(galleryBookTitle, page);
    }

    @Transactional
    public GalleryDto.CreateResponse createGalleries(
            UserPrincipal userPrincipal,
            Long archiveId,
            GalleryDto.CreateRequest request) {

        GalleryBook galleryBook = galleryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(galleryBook.getArchive().getUser().getId(), userPrincipal);

        List<File> files = fileRepository.findAllById(request.getFileIds());

        if (files.size() != request.getFileIds().size()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        List<Gallery> galleries = files.stream()
                .map(file -> Gallery.builder()
                        .archiveId(archiveId)
                        .galleryBook(galleryBook)
                        .file(file)
                        .build())
                .collect(Collectors.toList());

        galleryRepository.saveAll(galleries);

        return GalleryDto.CreateResponse.builder()
                .createdCount(galleries.size())
                .archiveId(archiveId)
                .build();
    }

    @Transactional
    public GalleryDto.UpdateTitleResponse updateGalleryBookTitle(
            UserPrincipal userPrincipal,
            Long archiveId,
            GalleryDto.UpdateTitleRequest request) {

        GalleryBook galleryBook = galleryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(galleryBook.getArchive().getUser().getId(), userPrincipal);

        galleryBook.updateTitle(request.getTitle());

        return GalleryDto.UpdateTitleResponse.builder()
                .galleryBookId(archiveId)
                .updatedTitle(galleryBook.getTitle())
                .build();
    }

    @Transactional
    public void deleteGalleries(UserPrincipal userPrincipal, Long archiveId, GalleryDto.DeleteRequest request) {
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(archive.getUser().getId(), userPrincipal);
        galleryRepository.deleteByIdsAndArchiveId(request.getGalleryIds(), archiveId);
    }

    // Helper Methods
    private void validateOwner(Long ownerId, UserPrincipal userPrincipal) {
        if (!ownerId.equals(userPrincipal.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }
}