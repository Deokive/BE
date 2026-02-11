package com.depth.deokive.domain.post.repository;


import com.depth.deokive.common.service.PaginationCountCacheService;
import com.depth.deokive.common.service.PaginationIdCacheService;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
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
    private final PaginationCountCacheService paginationCountCacheService;
    private final PaginationIdCacheService paginationIdCacheService;

    public Page<RepostDto.RepostElementResponse> findByTabId(Long tabId, Pageable pageable) {

        // SEQ 1. ID로 조회 - Redis 캐싱 (정렬 고정)
        String idsCacheKey = "repost:" + tabId
                + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();

        List<Long> ids = paginationIdCacheService.getIds(idsCacheKey, () ->
                queryFactory
                        .select(repost.id)
                        .from(repost)
                        .where(repost.repostTab.id.eq(tabId))
                        .orderBy(repost.createdAt.desc(), repost.id.desc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .fetch()
        );

        List<RepostDto.RepostElementResponse> content = new ArrayList<>();

        // SEQ 2. 데이터 조회
        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(Projections.constructor(RepostDto.RepostElementResponse.class,
                            repost.id,
                            repost.url,        // Changed from repost.postId
                            repost.title,
                            repost.thumbnailUrl, // Changed from repost.thumbnailKey
                            repost.repostTab.id,
                            repost.createdAt,
                            repost.status.stringValue()  // Enum → String
                    ))
                    .from(repost)
                    .where(repost.id.in(ids))
                    .orderBy(repost.createdAt.desc(), repost.id.desc())
                    .fetch();
        }

        // SEQ 4. Count Query - Redis 캐싱 (탭별 캐시)
        String countCacheKey = "repost:" + tabId;

        return PageableExecutionUtils.getPage(content, pageable,
                () -> paginationCountCacheService.getCount(countCacheKey, () ->
                        queryFactory.select(repost.count())
                                .from(repost)
                                .where(repost.repostTab.id.eq(tabId))
                                .fetchOne()
                ));
    }
}