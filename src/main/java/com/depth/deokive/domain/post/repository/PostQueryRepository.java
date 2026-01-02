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

import static com.depth.deokive.domain.post.entity.QPost.post;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<PostDto.PostPageResponse> searchPostFeed(Category category, Pageable pageable) {

        // STEP 1. 커버링 인덱스 활용 (ID 조회)
        List<Long> ids = queryFactory
                .select(post.id)
                .from(post)
                .where(eqCategory(category)) // category 인덱스를 태워 ID만 빠르게 추출
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // STEP 2. 데이터 조회 (User, Thumbnail Fetch Join)
        List<PostDto.PostPageResponse> content = new ArrayList<>();

        if (!ids.isEmpty()) {
            content = queryFactory
                .select(new QPostDto_PostPageResponse(
                        post.id,
                        post.title,
                        post.category,
                        post.thumbnailUrl,
                        post.user.nickname,
                        post.likeCount,
                        post.viewCount,
                        post.hotScore,
                        post.createdAt,
                        post.lastModifiedAt
                ))
                .from(post)
                .join(post.user) // 작성자 Fetch Join
                .where(post.id.in(ids))
                .orderBy(getOrderSpecifiers(pageable)) // ID 순서 보장을 위해 재정렬
                .fetch();
        }

        // Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(eqCategory(category));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression eqCategory(Category category) {
        return category != null ? post.category.eq(category) : null;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, post.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, post.createdAt);
                case "viewCount" -> new OrderSpecifier<>(direction, post.viewCount);
                case "likeCount" -> new OrderSpecifier<>(direction, post.likeCount);
                case "hotScore" -> new OrderSpecifier<>(direction, post.hotScore);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        // 정렬 기준이 없거나 값이 같을 경우를 대비해 ID 역순 추가 (Pagination 안정성)
        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, post.id));
        }

        orders.add(new OrderSpecifier<>(Order.DESC, post.id));

        return orders.toArray(new OrderSpecifier[0]);
    }
}