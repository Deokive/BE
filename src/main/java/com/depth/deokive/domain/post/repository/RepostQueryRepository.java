package com.depth.deokive.domain.post.repository;


import com.depth.deokive.domain.post.dto.RepostDto;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.depth.deokive.domain.post.entity.QRepost.repost;

@Repository
@RequiredArgsConstructor
public class RepostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<RepostDto.RepostElementResponse> findByTabId(Long tabId, Pageable pageable) {

        // SEQ 1. ID로 조회
        List<Long> ids = queryFactory
                .select(repost.id)
                .from(repost)
                .where(repost.repostTab.id.eq(tabId))
                .orderBy(repost.createdAt.desc(), repost.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<RepostDto.RepostElementResponse> content = new ArrayList<>();

        // SEQ 2. 데이터 조회
        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(Projections.constructor(RepostDto.RepostElementResponse.class,
                            repost.id,
                            repost.postId,
                            repost.title,
                            repost.thumbnailKey,
                            repost.repostTab.id,
                            repost.createdAt
                    ))
                    .from(repost)
                    .where(repost.id.in(ids))
                    .orderBy(repost.createdAt.desc(), repost.id.desc())
                    .fetch();
        }

        // SEQ 4. Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(repost.count())
                .from(repost)
                .where(repost.repostTab.id.eq(tabId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}