package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.post.dto.PostDto;


import com.depth.deokive.domain.post.dto.QPostDto_PostPageResponse;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.depth.deokive.domain.post.entity.QPost.post;
import static com.depth.deokive.domain.post.entity.QPostStats.postStats;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<PostDto.PostPageResponse> searchPostFeed(Category category, Pageable pageable) {

        // STEP 1. 커버링 인덱스 활용 (ID 조회)
        List<Long> ids = queryFactory
                .select(postStats.id)
                .from(postStats)
                .where(eqCategory(category)) // postStats.category 사용
                .orderBy(getOrderSpecifiers(pageable)) // postStats 기준 정렬
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // STEP 2. 데이터 조회 (Post + PostStats + User Fetch Join)
        List<PostDto.PostPageResponse> sortedContent = new ArrayList<>();

        if (!ids.isEmpty()) {
            List<PostDto.PostPageResponse> content = queryFactory
                .select(new QPostDto_PostPageResponse(
                        post.id,
                        post.title,
                        post.category,
                        post.content,
                        post.thumbnailKey,
                        post.user.nickname,
                        postStats.likeCount,
                        postStats.viewCount,
                        postStats.hotScore,
                        post.createdAt,
                        post.lastModifiedAt
                ))
                .from(post)
                .join(postStats).on(post.id.eq(postStats.id)) // Shared PK 기반 명시적 조인 (1:1)
                .join(post.user) // 작성자 Fetch Join
                .where(post.id.in(ids))
                .fetch();

            Map<Long, PostDto.PostPageResponse> contentMap = content.stream()
                    .collect(Collectors.toMap(PostDto.PostPageResponse::getPostId, Function.identity()));

            sortedContent = ids.stream().map(contentMap::get).toList();
        }

        // Count Query (PostStats 기준)
        JPAQuery<Long> countQuery = queryFactory
                .select(postStats.count())
                .from(postStats)
                .where(eqCategory(category));

        return PageableExecutionUtils.getPage(sortedContent, pageable, countQuery::fetchOne);
    }

    private BooleanExpression eqCategory(Category category) {
        return category != null ? postStats.category.eq(category) : null;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, postStats.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, postStats.createdAt);
                case "viewCount" -> new OrderSpecifier<>(direction, postStats.viewCount);
                case "likeCount" -> new OrderSpecifier<>(direction, postStats.likeCount);
                case "hotScore" -> new OrderSpecifier<>(direction, postStats.hotScore);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        // 정렬 기준이 없거나 값이 같을 경우를 대비해 ID 역순 추가 (Pagination 안정성)
        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, postStats.createdAt));
        }

        boolean hasIdSort = orders.stream().anyMatch(o -> o.getTarget().equals(postStats.id));

        if (!hasIdSort) {
            Order lastDirection = orders.isEmpty() ? Order.DESC : orders.get(orders.size() - 1).getOrder();
            orders.add(new OrderSpecifier<>(lastDirection, postStats.id));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}