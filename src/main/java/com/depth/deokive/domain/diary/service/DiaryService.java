package com.depth.deokive.domain.diary.service;

import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryBookRepository diaryBookRepository;
    private final DiaryFileMapRepository diaryFileMapRepository;
    private final FileRepository fileRepository;

    // TODO: private final FriendRepository friendRepository; // 추후 친구 관계 확인용

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
        connectFiles(diary, request.getFiles());

        return DiaryDto.Response.of(diary, getFileMaps(diary.getId()));
    }

    @Transactional(readOnly = true)
    public DiaryDto.Response getDiary(UserPrincipal userPrincipal, Long diaryId) {
        // SEQ 1. 다이어리 조회
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new RestException(ErrorCode.DIARY_NOT_FOUND));

        // SEQ 2. 다이어리 접근 권한 점검
        validateReadPermission(diary, userPrincipal);

        return DiaryDto.Response.of(diary, getFileMaps(diaryId));
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

        // SEQ 4. 파일 갈아끼우기 (Post와 똑같이 처리)
        diaryFileMapRepository.deleteAllByDiaryId(diaryId);
        connectFiles(diary, request.getFiles());

        return DiaryDto.Response.of(diary, getFileMaps(diaryId));
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

    // --- Helper Methods ---

    private void validateReadPermission(Diary diary, UserPrincipal userPrincipal) {
        Long viewerId = userPrincipal != null ? userPrincipal.getUserId() : null;
        Long writerId = diary.getCreatedBy();

        // SEQ 1. 작성자 본인이면 통과
        if (Objects.equals(viewerId, writerId)) return;

        // SEQ 2. 공개 범위 체크
        switch (diary.getVisibility()) {
            case PRIVATE -> throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 본인 제외 접근 불가
            case RESTRICTED -> {
                // TODO: 친구 관계 확인 로직 추가 필요
                boolean isFriend = checkFriendRelationship(viewerId, writerId);
                if (!isFriend) throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            }
            case PUBLIC -> { /* 모두 허용 */ }
        }
    }

    private boolean checkFriendRelationship(Long viewerId, Long writerId) {
        if (viewerId == null) return false;
        // return friendRepository.existsByFromUserIdAndToUserId(viewerId, writerId);
        return false; // 현재는 친구 로직이 없으므로 false 처리
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

    private void connectFiles(Diary diary, List<DiaryDto.AttachedFileRequest> files) {
        if (files == null || files.isEmpty()) return;

        for (DiaryDto.AttachedFileRequest fileReq : files) {
            File file = fileRepository.findById(fileReq.getFileId())
                    .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));

            DiaryFileMap map = DiaryFileMap.builder()
                    .diary(diary)
                    .file(file)
                    .mediaRole(fileReq.getMediaRole())
                    .sequence(fileReq.getSequence())
                    .build();
            diaryFileMapRepository.save(map);
        }
    }

    private List<DiaryFileMap> getFileMaps(Long diaryId) {
        return diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diaryId);
    }
}