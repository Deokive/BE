package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.dto.FriendDto;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


import static com.depth.deokive.domain.friend.entity.QFriendMap.friendMap;
import static com.depth.deokive.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class FriendQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 친구 목록 조회
     */
    public Slice<FriendDto.Response> findMyFriends(Long userId, Long lastFriendId, LocalDateTime lastAcceptedAt, Pageable pageable) {

        List<FriendDto.Response> content = queryFactory
                .select(Projections.constructor(FriendDto.Response.class,
                        friendMap.friend.id,
                        friendMap.friend.nickname,
                        friendMap.acceptedAt
                ))
                .from(friendMap)
                .join(friendMap.friend, user)
                .where(
                        friendMap.user.id.eq(userId),
                        friendMap.friendStatus.eq(FriendStatus.ACCEPTED),
                        cursorCondition(lastFriendId, lastAcceptedAt)
                )
                .orderBy(friendMap.acceptedAt.desc(), friendMap.friendId.desc()) // 최신순으로 -> 추후 수정
                .limit(pageable.getPageSize() + 1)
                .fetch();

        return checkLastPage(pageable, content);
    }

    // --- Helper Methods ---
    private <T> Slice<T> checkLastPage(Pageable pageable, List<T> results) {
        boolean hasNext = false;
        if (results.size() > pageable.getPageSize()) {
            hasNext = true;
            results.remove(pageable.getPageSize());
        }
        return new SliceImpl<>(results, pageable, hasNext);
    }

    // 커서 조건
    private BooleanExpression cursorCondition(Long lastFriendId, LocalDateTime lastAcceptedAt) {
        if (lastFriendId == null || lastAcceptedAt == null) {
            return null;
        }

        // 수락 시간
        return friendMap.acceptedAt.lt(lastAcceptedAt)
                //시간이 같으면 id가 더 작은 값으로
                .or(friendMap.acceptedAt.eq(lastAcceptedAt)
                        .and(friendMap.friendId.lt(lastFriendId)));
    }
}
