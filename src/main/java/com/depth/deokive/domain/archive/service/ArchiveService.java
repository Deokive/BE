package com.depth.deokive.domain.archive.service;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.repository.ArchiveQueryRepository;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.repository.UserRepository;
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
public class ArchiveService {

    private final ArchiveQueryRepository archiveQueryRepository;
    private final FriendMapRepository friendMapRepository;
    private final UserRepository userRepository;

    // 내 아카이브 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getMyArchives(Long userId, Pageable pageable) {
        // 1. 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(userId, true, pageable);

        // 2. 인덱스 범위 검증
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 친구 아카이브 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getFriendArchives(Long myUserId, Long friendId, Pageable pageable) {
        // 1. 친구 관계 검증
        validateFriendRelationship(myUserId, friendId);

        // 2. 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(friendId, false, pageable);

        // 3. 인덱스 범위 검증
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 핫피드 목록 조회
    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getHotArchives(Pageable pageable) {

        // 1. 핫피드 조회
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchHotArchives(pageable);

        // 2. 페이지 범위 검증(데이터 없는데 페이지 요청 시 -> 404에러)
        validateIndexBounds(pageable, page);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 친구 관계 검증 로직 분리 (Clean Code)
    private void validateFriendRelationship(Long myUserId, Long friendId) {
        // 친구 존재 여부 확인
        if (!userRepository.existsById(friendId)) {
            throw new RestException(ErrorCode.USER_NOT_FOUND); // 존재하지 X -> 404에러
        }

        // 친구 관계 확인(ACCEPTED인지)
        boolean isFriend = friendMapRepository.existsFriendship(myUserId, friendId, FriendStatus.ACCEPTED);

        if (!isFriend) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 친구 X -> 403 에러
        }
    }

    // 공통 검증 로직(원래 있으면 재사용, 없으면 추가)
    public void validateIndexBounds(Pageable pageable, Page<?> pageData) {
        // 요청한 페이지 -> 전체 페이지 수, 데이터가 아예 없는게 아니면 404 예외처리
        if(pageable.getPageNumber() > 0 && pageData.getTotalPages() <= pageable.getPageNumber()) {
            throw new RestException(ErrorCode.DB_DATA_NOT_FOUND);
        }
    }
}