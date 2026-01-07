package com.depth.deokive.domain.friend.service;

import com.depth.deokive.domain.friend.dto.FriendDto;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendRequestType;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.friend.repository.FriendQueryRepository;
import com.depth.deokive.domain.notification.dto.event.NotificationEvent;
import com.depth.deokive.domain.notification.entity.enums.NotificationType;
import com.depth.deokive.domain.notification.service.NotificationService;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendMapRepository friendMapRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FriendQueryRepository friendQueryRepository;

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

        // SEQ 5. 알림
        String message = me.getNickname() + "님이 친구 요청을 보냈습니다.";
        String relatedUrl = "/users/" + userId;

        eventPublisher.publishEvent(NotificationEvent.of(
                friendId,
                userId,
                NotificationType.FRIEND_REQUEST,
                message,
                relatedUrl
        ));
    }

    /**
     * 친구 요청 수락
     */
    @Transactional
    public void acceptFriendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId();

        // SEQ 1. 자기 자신에게 친구 요청을 수락한 경우
        if(userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        boolean isAlreadyFriend = friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                userId, friendId, FriendStatus.ACCEPTED);

        // SEQ 2. 이미 친구인 경우
        if(isAlreadyFriend) {
            throw new RestException(ErrorCode.FRIEND_ALREADY_EXISTS);
        }

        // SEQ 3. 요청 데이터 확인 -> 데이터가 없거나, PENDING이 아니면 -> 에러
        FriendMap requestMap = friendMapRepository.findByUserIdAndFriendId(friendId, userId)
                .filter(map -> map.getFriendStatus() == FriendStatus.PENDING)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        // SEQ 4. 상대방 관계 ACCEPTED로 최신화(상대방 -> 나(ACCEPTED))
        requestMap.updateStatus(FriendStatus.ACCEPTED);

        // SEQ 5. 내 관계 생성/업데이트(나 -> 상대방(ACCEPTED))
        User me = userRepository.getReferenceById(userId);
        User friend = userRepository.getReferenceById(friendId);

        FriendMap myMap = friendMapRepository.findByUserIdAndFriendId(userId, friendId)
                .orElse(FriendMap.builder()
                        .user(me)
                        .friend(friend)
                        .requestedBy(me)
                        .build());

        myMap.updateStatus(FriendStatus.ACCEPTED);
        friendMapRepository.save(myMap);

        // SEQ 6. 알림
        String message = me.getNickname() + "님이 친구 요청을 수락했습니다.";
        String relatedUrl = "/users/" + userId;

        eventPublisher.publishEvent(NotificationEvent.of(
                friendId,
                userId,
                NotificationType.FRIEND_ACCEPT,
                message,
                relatedUrl
        ));
    }

    /**
     * 친구 요청 거절
     */
    @Transactional
    public void rejectFriendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId();

        // SEQ 1. 자기 자신일 때
        if(userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        // SEQ 2. 이미 친구일 때
        boolean isAlreadyFriend = friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                userId, friendId, FriendStatus.ACCEPTED);

        if(isAlreadyFriend) {
            throw new RestException(ErrorCode.FRIEND_ALREADY_EXISTS);
        }

        // SEQ 3. 요청 데이터 확인 -> 데이터가 없거나, PENDING이 아니면 -> 에러
        FriendMap requestMap = friendMapRepository.findByUserIdAndFriendId(friendId, userId)
                .filter(map -> map.getFriendStatus() == FriendStatus.PENDING)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        // SEQ 4. 상태 변경
        requestMap.updateStatus(FriendStatus.REJECTED);
    }

    /**
     * 친구 요청 취소
     */
    @Transactional
    public void friendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId(); // 나

        // 자기 자신일 때
        if(userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        // SEQ 1. 내가 보낸 요청 데이터 확인 (나 -> 상대방)
        FriendMap sentMap = friendMapRepository.findByUserIdAndFriendId(userId, friendId)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_SEND_REQUEST_NOT_FOUND));

        // SEQ 3. 상태 검증
        if(sentMap.getFriendStatus() != FriendStatus.PENDING) {
            throw new RestException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        // SEQ 4. 상태 변경
        sentMap.updateStatus(FriendStatus.CANCELED);
    }

    /**
     * 친구 끊기
     */
    @Transactional
    public void cancelFriendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId(); // 나

        // 자기 자신일 때
        if(userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        // SEQ 1. 관계 조회(나 -> 친구)
        FriendMap myMap = friendMapRepository.findByUserIdAndFriendId(userId, friendId)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_NOT_FOUND));

        // SEQ 2. 친구 관계 조회(친구 -> 나)
        FriendMap friendMap = friendMapRepository.findByUserIdAndFriendId(friendId, userId)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_NOT_FOUND));

        // SEQ 3. 상태 검증(나, 친구 둘 다 ACCEPTED가 아니라면 에러)
        if(myMap.getFriendStatus() != FriendStatus.ACCEPTED || friendMap.getFriendStatus() != FriendStatus.ACCEPTED) {
            throw new RestException(ErrorCode.FRIEND_NOT_FOUND);
        }

        // SEQ 4. 상태 변경
        myMap.updateStatus(FriendStatus.CANCELED);
        friendMap.updateStatus(FriendStatus.CANCELED);
    }

    /**
     * 친구 끊기 취소(recover)
     */
    @Transactional
    public void recoverFriendRequest(UserPrincipal userPrincipal, Long friendId) {
        Long userId = userPrincipal.getUserId(); // 나

        // 자기 자신일 때
        if (userId.equals(friendId)) {
            throw new RestException(ErrorCode.FRIEND_SELF_BAD_REQUEST);
        }

        // SEQ 1. 관계 조회(나 -> 친구)
        FriendMap myMap = friendMapRepository.findByUserIdAndFriendId(userId, friendId)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_CANCELED_NOT_FOUND));

        FriendMap friendMap = friendMapRepository.findByUserIdAndFriendId(friendId, userId)
                .orElseThrow(() -> new RestException(ErrorCode.FRIEND_CANCELED_NOT_FOUND));

        // SEQ 2. 상태 검증(CANCELED일 떄만 가능)
        if(myMap.getFriendStatus() != FriendStatus.CANCELED || friendMap.getFriendStatus() != FriendStatus.CANCELED) {
            throw new RestException(ErrorCode.FRIEND_RECOVER_BAD_REQUEST);
        }

        // SEQ 3. 상태 변경
        myMap.updateStatus(FriendStatus.ACCEPTED);
        friendMap.updateStatus(FriendStatus.ACCEPTED);
    }

    /**
     * 친구 목록 조회
     */
    @Transactional
    public FriendDto.FriendListResponse<FriendDto.Response> getMyFriends(UserPrincipal userPrincipal,
                                                     Long lastFriendId,
                                                     LocalDateTime lastAcceptedAt,
                                                     Pageable pageable) {
        Slice<FriendDto.Response> slice = friendQueryRepository.findMyFriends(
                userPrincipal.getUserId(),
                lastFriendId,
                lastAcceptedAt,
                pageable
        );
        return FriendDto.FriendListResponse.of(slice);
    }

    /**
     * 보낸/받은 친구 요청 목록 조회
     */
    @Transactional(readOnly = true)
    public FriendDto.FriendListResponse<FriendDto.RequestResponse> getFriendRequests(
            UserPrincipal userPrincipal,
            String type,
            Long lastId,
            LocalDateTime lastCreatedAt,
            Pageable pageable
    ) {
        // SEQ 1. ENUM 검증
        FriendRequestType requestType;
        try {
            requestType = FriendRequestType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RestException(ErrorCode.FRIEND_INVALID_TYPE);
        }

        // SEQ 2. Repository 조회
        Slice<FriendDto.RequestResponse> slice = friendQueryRepository.findFriendRequests(
                userPrincipal.getUserId(),
                requestType,
                lastId,
                lastCreatedAt,
                pageable
        );

        // SEQ 3. 응답 변환
        return FriendDto.FriendListResponse.of(slice);
    }
}
