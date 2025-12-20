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

    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getMyArchives(Long userId, Pageable pageable) {
        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(userId, true, pageable);
        return ArchiveDto.PageListResponse.of(page);
    }

    @ExecutionTime
    @Transactional(readOnly = true)
    public ArchiveDto.PageListResponse getFriendArchives(Long myUserId, Long friendId, Pageable pageable) {

        validateFriendRelationship(myUserId, friendId);

        Page<ArchiveDto.Response> page = archiveQueryRepository.searchArchives(friendId, false, pageable);

        return ArchiveDto.PageListResponse.of(page);
    }

    // 친구 관계 검증 로직 분리 (Clean Code)
    private void validateFriendRelationship(Long myUserId, Long friendId) {
        if (!userRepository.existsById(friendId)) {
            throw new RestException(ErrorCode.USER_NOT_FOUND);
        }

        boolean isFriend = friendMapRepository.existsFriendship(myUserId, friendId, FriendStatus.ACCEPTED);

        if (!isFriend) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN); // 친구가 아님
        }
    }
}