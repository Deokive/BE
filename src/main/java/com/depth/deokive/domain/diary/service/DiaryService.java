package com.depth.deokive.domain.diary.service;

import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryQueryRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryBookRepository diaryBookRepository;
    private final DiaryFileMapRepository diaryFileMapRepository;
    private final FileService fileService;
    private final FriendMapRepository friendMapRepository;
    private final DiaryQueryRepository diaryQueryRepository;

    @Transactional
    public DiaryDto.Response createDiary(UserPrincipal userPrincipal, Long archiveId, DiaryDto.Request request) {
        // SEQ 1. 다이어리 북(아카이브) 조회
        DiaryBook diaryBook = diaryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 확인
        validateBookOwner(diaryBook, userPrincipal.getUserId());

        // SEQ 3. 저장
        Diary diary = request.toEntity(diaryBook);
        diaryRepository.save(diary);

        // SEQ 4. 파일 연결
        List<DiaryFileMap> maps = connectFiles(diary, request.getFiles(), userPrincipal.getUserId());

        // SEQ 5. 썸네일 업데이트
        updateDiaryThumbnail(diary, maps);

        return DiaryDto.Response.of(diary, maps);
    }

    @Transactional(readOnly = true)
    public DiaryDto.Response retrieveDiary(UserPrincipal userPrincipal, Long diaryId) {
        // SEQ 1. 다이어리 조회
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new RestException(ErrorCode.DIARY_NOT_FOUND));

        // SEQ 2. 다이어리 접근 권한 점검
        validateReadPermission(diary, userPrincipal);

        // SEQ 3. 파일 매핑 조회
        List<DiaryFileMap> maps = getFileMaps(diaryId);

        return DiaryDto.Response.of(diary, maps);
    }

    @Transactional
    public DiaryDto.Response updateDiary(UserPrincipal userPrincipal, Long diaryId, DiaryDto.Request request) {
        // SEQ 1. 다이어리 조회
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new RestException(ErrorCode.DIARY_NOT_FOUND));

        // SEQ 2. 소유권 확인
        validateWriter(diary, userPrincipal.getUserId());

        // SEQ 3. 업데이트 (Dirty Checking 기반)
        diary.update(request);

        // SEQ 4. 파일 갈아끼우기 : 전략 -> Full Replacement
        diaryFileMapRepository.deleteAllByDiaryId(diaryId);
        List<DiaryFileMap> maps = connectFiles(diary, request.getFiles(), userPrincipal.getUserId());

        // SEQ 5. 썸네일 업데이트
        updateDiaryThumbnail(diary, maps);

        return DiaryDto.Response.of(diary, maps);
    }

    @Transactional
    public void deleteDiary(UserPrincipal userPrincipal, Long diaryId) {
        // SEQ 1. 다이어리 조회
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new RestException(ErrorCode.DIARY_NOT_FOUND));

        // SEQ 2. 소유권 확인
        validateWriter(diary, userPrincipal.getUserId());

        // SEQ 3. Diary와 관련된 모든 매핑 제거 -> 다이어리 제거 (Cascade가 아닌 명시적 제거)
        diaryFileMapRepository.deleteAllByDiaryId(diaryId);
        diaryRepository.delete(diary);
    }

    @Transactional
    public DiaryDto.UpdateBookTitleResponse updateDiaryBookTitle(UserPrincipal userPrincipal, Long archiveId, DiaryDto.UpdateBookTitleRequest request) {
        // SEQ 1. 다이어리북 조회
        DiaryBook diaryBook = diaryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 확인
        validateBookOwner(diaryBook, userPrincipal.getUserId());

        // SEQ 3. 제목 업데이트 (Dirty Checking)
        diaryBook.updateTitle(request.getTitle());

        // SEQ 4. 응답 반환
        return DiaryDto.UpdateBookTitleResponse.builder()
                .diaryBookId(archiveId)
                .updatedTitle(diaryBook.getTitle())
                .build();
    }

    // Pagination
    @Transactional(readOnly = true)
    public DiaryDto.PageListResponse getDiaries(UserPrincipal userPrincipal, Long archiveId, DiaryDto.DiaryPageRequest request) {
        // SEQ 1. 아카이브(Book) 존재 여부 확인
        DiaryBook diaryBook = diaryBookRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        Long viewerId = (userPrincipal != null) ? userPrincipal.getUserId() : null;
        Long ownerId = diaryBook.getArchive().getUser().getId();
        Visibility archiveVisibility = diaryBook.getArchive().getVisibility();

        // SEQ 2. Archive Level Security (Layer 1) -> 아카이브 접근 권한 확인
        if (archiveVisibility == Visibility.PRIVATE && !Objects.equals(viewerId, ownerId)) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (archiveVisibility == Visibility.RESTRICTED) {
            boolean isFriend = checkFriendRelationship(viewerId, ownerId);
            if (!isFriend && !Objects.equals(viewerId, ownerId)) {
                throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            }
        }

        // SEQ 3. Diary Level Security (Layer 2) - 필터링 조건 계산
        List<Visibility> allowedVisibilities;
        if (Objects.equals(viewerId, ownerId)) {
            // 본인: 모두 조회
            allowedVisibilities = List.of(Visibility.PUBLIC, Visibility.RESTRICTED, Visibility.PRIVATE);
        } else {
            boolean isFriend = checkFriendRelationship(viewerId, ownerId);
            if (isFriend) {
                // 친구: 전체공개 + 친구공개
                allowedVisibilities = List.of(Visibility.PUBLIC, Visibility.RESTRICTED);
            } else {
                // 타인: 전체공개만
                allowedVisibilities = List.of(Visibility.PUBLIC);
            }
        }

        // SEQ 4. QueryDSL Pagination 실행
        Page<DiaryDto.DiaryPageResponse> page = diaryQueryRepository.findDiaries(
                archiveId,
                allowedVisibilities,
                request.toPageable()
        );

        return DiaryDto.PageListResponse.of(diaryBook.getTitle(), page);
    }

    // --- Helper Methods ---

    private void validateReadPermission(Diary diary, UserPrincipal userPrincipal) {
        Long viewerId = userPrincipal != null ? userPrincipal.getUserId() : null;
        Long writerId = diary.getCreatedBy();

        log.info("🟢 Diary Visibility : {}", diary.getVisibility());
        log.info("🟢 Diary WriterId : {}", writerId);
        log.info("🟢 Diary ViewerId : {}", viewerId);

        // SEQ 1. 작성자 본인이면 통과
        if (Objects.equals(viewerId, writerId) && writerId != null) return;

        log.info("🟢 Let's Check Diary Visibility : {}", diary.getVisibility());

        // SEQ 2. 공개 범위 체크
        switch (diary.getVisibility()) {
            case PRIVATE -> throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 본인 제외 접근 불가
            case RESTRICTED -> {
                boolean isFriend = checkFriendRelationship(viewerId, writerId);
                if (!isFriend) { throw new RestException(ErrorCode.AUTH_FORBIDDEN); }
            }
            case PUBLIC -> {/* 모두 허용 */}
        }
    }

    private boolean checkFriendRelationship(Long viewerId, Long writerId) {
        if (viewerId == null) return false;

        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId,
                writerId,
                FriendStatus.ACCEPTED
        );
    }

    private void validateBookOwner(DiaryBook book, Long userId) {
        if (!book.getArchive().getUser().getId().equals(userId)) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateWriter(Diary diary, Long userId) {
        if (!diary.getCreatedBy().equals(userId)) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // Refactor : 파일 목록 한 번에 조회하고, 매핑 엔터티 생성해서 일괄 저장, N+1 문제 방지 및 DB 커넥션 비용 최소화
    private List<DiaryFileMap> connectFiles(
            Diary diary,
            List<DiaryDto.AttachedFileRequest> fileRequests,
            Long userId
    ) {
        // SEQ 1. Validation
        if (fileRequests == null || fileRequests.isEmpty()) {
            return Collections.emptyList();
        }

        // SEQ 2. 파일 ID 목록 추출
        List<Long> fileIds = fileRequests.stream()
                .map(DiaryDto.AttachedFileRequest::getFileId).toList();

        // SEQ 3. File Entity Bulk Fetch
        List<File> files = fileService.validateFileOwners(fileIds, userId);

        // SEQ 4. Validate Files
        if (files.size() != fileIds.stream().distinct().count()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        // SEQ 5. List to Map (ID를 Key로 해서 O(1) 접근 가능하게 할라고 함)
        Map<Long, File> fileMap = files.stream()
                .collect(Collectors.toMap(File::getId, Function.identity()));

        // SEQ 6. Mapping Entities Creation (요청 순서 유지)
        List<DiaryFileMap> newMaps = fileRequests.stream()
                .map(req -> {
                    File file = fileMap.get(req.getFileId());
                    return DiaryFileMap.builder()
                            .diary(diary)
                            .file(file)
                            .mediaRole(req.getMediaRole())
                            .sequence(req.getSequence())
                            .build();
                })
                .collect(Collectors.toList());

        // SEQ 7. Bulk Insert
        return diaryFileMapRepository.saveAll(newMaps);
    }

    private List<DiaryFileMap> getFileMaps(Long diaryId) {
        return diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diaryId);
    }

    private void updateDiaryThumbnail(Diary diary, List<DiaryFileMap> maps) {
        if (maps == null || maps.isEmpty()) {
            diary.updateThumbnail(null);
            return;
        }

        // TODO: 썸네일 선정을 Post와 달리 String으로 해봄 -> 성능 비교해보자 -> 어차피 리팩터링 단계 별도로 있으니까
        String thumbnailUrl = maps.stream()
                .filter(map -> map.getMediaRole() == MediaRole.PREVIEW)
                .findFirst()
                .map(map -> map.getFile().getFilePath())
                .orElseGet(() -> maps.stream()
                        .min(Comparator.comparingInt(DiaryFileMap::getSequence))
                        .map(map -> map.getFile().getFilePath())
                        .orElse(null)
                );

        diary.updateThumbnail(thumbnailUrl);
    }
}