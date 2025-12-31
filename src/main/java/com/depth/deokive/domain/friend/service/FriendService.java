package com.depth.deokive.domain.friend.service;

import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.notification.NotificationService;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendMapRepository friendMapRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 친구 요청
     */
    @Transactional
    public void sendFriendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId();

        // SEQ 1. 내 관계 확인
        if(userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        // SEQ 2. 상대 관계 확인
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        User me = userRepository.getReferenceById(userId);

        // SEQ 3. 역방향 체크
        // 상대방이 나한테 보낸 요청이 PENDING인지
        boolean isReversePending = friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(friendId, userId, FriendStatus.PENDING);

        if(isReversePending) {
            throw new RestException(ErrorCode.FRIEND_REQUEST_CONFLICT);
        }

        // SEQ 4. 정방향
        Optional<FriendMap> existingMap = friendMapRepository.findByUserIdAndFriendId(userId, friendId);

        if(existingMap.isPresent()) {
            FriendMap map = existingMap.get();
            FriendStatus status = map.getFriendStatus();

            // 이미 처리가 되어있는지
            if(status == FriendStatus.PENDING) {
                throw new RestException(ErrorCode.FRIEND_ALREADY_REQUESTED);
            }

            if(status == FriendStatus.ACCEPTED) {
                throw new RestException(ErrorCode.FRIEND_ALREADY_EXISTS);
            }

            // 재요청
            map.updateStatus(FriendStatus.PENDING);
            map.updateRequestedBy(me);
        } else {
            // 신규 요청
            FriendMap newMap = FriendMap.builder()
                    .user(me)
                    .friend(friend)
                    .friendStatus(FriendStatus.PENDING)
                    .requestedBy(me)
                    .build();

            friendMapRepository.save(newMap);
        }

        // SEQ 5. 비동기 알림
        notificationService.sendFriendRequestNotification(friendId, userId);

    }
}
