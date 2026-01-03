package com.depth.deokive.common.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArchiveGuard {
    private final FriendMapRepository friendMapRepository;

    // 1. 아카이브 읽기 권한 체크 (주인, 친구, 공개범위 판단)
    public void checkArchiveReadPermission(Archive archive, UserPrincipal user) {
        checkVisibility(archive.getUser().getId(), user, archive.getVisibility());
    }

    // 2. 범용 공개범위 체크 (Diary 등 개별 Visibility가 있는 경우 사용)
    public void checkVisibility(Long ownerId, UserPrincipal user, Visibility visibility) {
        Long viewerId = (user != null) ? user.getUserId() : null;
        if (ownerId.equals(viewerId)) return; // 주인은 통과

        switch (visibility) {
            case PRIVATE -> throw new RestException(ErrorCode.AUTH_FORBIDDEN);
            case RESTRICTED -> {
                if (!isFriend(viewerId, ownerId)) {
                    throw new RestException(ErrorCode.AUTH_FORBIDDEN);
                }
            }
            case PUBLIC -> { /* 통과 */ }
        }
    }

    // 3. 소유자(수정/삭제 권한) 체크
    public void checkOwner(Long ownerId, UserPrincipal user) {
        if (!ownerId.equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // 4. 친구 관계 확인 (Helper)
    public boolean isFriend(Long viewerId, Long ownerId) {
        if (viewerId == null) return false;
        return friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                viewerId, ownerId, FriendStatus.ACCEPTED
        );
    }
}