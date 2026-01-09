package com.depth.deokive.domain.sticker.service;

import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.sticker.dto.StickerDto;
import com.depth.deokive.domain.sticker.entity.Sticker;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StickerService {

    private final StickerRepository stickerRepository;
    private final ArchiveRepository archiveRepository;
    private final ArchiveGuard archiveGuard;

    @Transactional
    public StickerDto.Response createSticker(UserPrincipal user, Long archiveId, StickerDto.CreateRequest request) {
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 확인
        archiveGuard.checkOwner(archive.getUser().getId(), user);

        // SEQ 3. 중복 날짜 확인
        if (stickerRepository.existsByArchiveIdAndDate(archiveId, request.getDate())) {
            throw new RestException(ErrorCode.STICKER_ALREADY_EXISTS);
        }

        // SEQ 4. 저장
        Sticker sticker = request.toEntity(archive);
        stickerRepository.save(sticker);

        return StickerDto.Response.from(sticker);
    }

    @Transactional
    public StickerDto.Response updateSticker(UserPrincipal user, Long stickerId, StickerDto.UpdateRequest request) {
        // SEQ 1. 조회
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new RestException(ErrorCode.STICKER_NOT_FOUND));

        // SEQ 2. 소유권 확인
        archiveGuard.checkOwner(sticker.getArchive().getUser().getId(), user);

        // SEQ 3. 날짜 변경 시 중복 체크
        if (request.getDate() != null && !request.getDate().equals(sticker.getDate())) {
            if (stickerRepository.existsByArchiveIdAndDate(sticker.getArchive().getId(), request.getDate())) {
                throw new RestException(ErrorCode.STICKER_ALREADY_EXISTS);
            }
        }

        // SEQ 4. 업데이트
        sticker.update(request.getStickerType(), request.getDate());

        return StickerDto.Response.from(sticker);
    }

    @Transactional
    public void deleteSticker(UserPrincipal user, Long stickerId) {
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new RestException(ErrorCode.STICKER_NOT_FOUND));

        archiveGuard.checkOwner(sticker.getArchive().getUser().getId(), user);

        stickerRepository.delete(sticker);
    }

    @Transactional(readOnly = true)
    public List<StickerDto.Response> getMonthlyStickers(UserPrincipal user, Long archiveId, int year, int month) {
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 조회 권한 확인 (친구/공개 등)
        archiveGuard.checkArchiveReadPermission(archive, user);

        // SEQ 3. 날짜 계산
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        // SEQ 4. 조회
        return stickerRepository.findAllByArchiveIdAndDateBetweenOrderByDateAsc(archiveId, startDate, endDate)
                .stream()
                .map(StickerDto.Response::from)
                .collect(Collectors.toList());
    }
}