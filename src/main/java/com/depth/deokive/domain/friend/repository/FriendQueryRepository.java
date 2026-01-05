package com.depth.deokive.domain.friend.repository;

import com.depth.deokive.domain.friend.dto.FriendDto;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

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
    public Slice<FriendDto.Response> findMyFriends(Long userId, Pageable pageable) {

        List<FriendDto.Response> content = queryFactory
                .select(Projections.constructor(FriendDto.Response.class,
                        friendMap.friend.id,
                        friendMap.friend.nickname
                ))
                .from(friendMap)
                .join(friendMap.friend, user)
                .where(
                        friendMap.user.id.eq(userId),
                        friendMap.friendStatus.eq(FriendStatus.ACCEPTED)
                )
                .orderBy(friendMap.acceptedAt.desc()) // 최신순으로 -> 추후 수정
                .offset(pageable.getOffset())
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
}
