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

    /**
     * 특정 탭의 리포스트 목록 페이지네이션
     */
    public Page<RepostDto.Response> findByTabId(Long tabId, Pageable pageable) {

        // SEQ 1. ID로 조회
        List<Long> ids = queryFactory
                .select(repost.id)
                .from(repost)
                .where(repost.repostTab.id.eq(tabId))
                .orderBy(getOrderSpecifier(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // SEQ 2. ID 없으면 빈 페이지
        if(ids.isEmpty()) {
            return Page.empty(pageable);
        }

        // SEQ 3. 데이터 조회
        List<RepostDto.Response> content = queryFactory
                .select(Projections.constructor(RepostDto.Response.class,
                        repost.id,
                        repost.postId,
                        repost.title,
                        repost.thumbnailUrl,
                        repost.repostTab.id
                ))
                .from(repost)
                .where(repost.id.in(ids))
                .orderBy(getOrderSpecifier(pageable))
                .fetch();

        // SEQ 4. Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(repost.count())
                .from(repost)
                .where(repost.repostTab.id.eq(tabId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // Helper Method
    private OrderSpecifier[] getOrderSpecifier(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, repost.createdAt)};
        }
        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "title" -> new OrderSpecifier<>(direction, repost.title);
                case "createdAt" -> new OrderSpecifier<>(direction, repost.createdAt);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, repost.createdAt));
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
