package com.depth.deokive.domain.gallery.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.util.PageUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
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
    private final ArchiveRepository archiveRepository;
    private final GalleryRepository galleryRepository;
    private final FileService fileService;
    private final FriendMapRepository friendMapRepository;

    @ExecutionTime
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<GalleryDto.Response> getGalleries(UserPrincipal userPrincipal, Long archiveId, Pageable pageable) {

        GalleryBook galleryBook = galleryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateReadPermission(galleryBook.getArchive(), userPrincipal);

        Page<GalleryDto.Response> page = galleryQueryRepository.searchGalleriesByArchive(archiveId, pageable);

        PageUtils.validatePageRange(page);

        return PageDto.PageListResponse.of(galleryBook.getTitle(), page);
    }

    @Transactional
    public GalleryDto.CreateResponse createGalleries(
            UserPrincipal userPrincipal,
            Long archiveId,
            GalleryDto.CreateRequest request) {

        GalleryBook galleryBook = galleryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        validateOwner(galleryBook.getArchive().getUser().getId(), userPrincipal);

        List<File> files = fileService.validateFileOwners(request.getFileIds(), userPrincipal.getUserId());

        if (files.size() != request.getFileIds().size()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        List<Gallery> galleries = files.stream()
                .map(file -> Gallery.builder()
                        .archiveId(archiveId)
                        .galleryBook(galleryBook)
                        .file(file)
                        .originalKey(file.getS3ObjectKey())
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

    private void validateReadPermission(Archive archive, UserPrincipal userPrincipal) {
        Long ownerId = archive.getUser().getId();
        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;

        if (ownerId.equals(viewerId)) return; // 주인은 프리패스

        switch (archive.getVisibility()) {
            case PRIVATE -> throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            case RESTRICTED -> {
                if (!checkFriendRelationship(viewerId, ownerId)) {
                    throw new RestException(ErrorCode.AUTH_FORBIDDEN);
                }
            }
            case PUBLIC -> { /* Pass */ }
        }
    }

    private boolean checkFriendRelationship(Long viewerId, Long ownerId) {
        if (viewerId == null) return false;

        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId,
                ownerId,
                FriendStatus.ACCEPTED
        );
    }
}