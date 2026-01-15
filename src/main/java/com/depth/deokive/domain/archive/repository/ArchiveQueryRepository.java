package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.dto.QArchiveDto_ArchivePageResponse;
import com.depth.deokive.common.enums.Visibility;
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

import static com.depth.deokive.domain.archive.entity.QArchive.archive;
import static com.depth.deokive.domain.archive.entity.QArchiveStats.archiveStats;

@Repository
@RequiredArgsConstructor
public class ArchiveQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<ArchiveDto.ArchivePageResponse> searchArchiveFeed(
            Long filterUserId,
            List<Visibility> allowedVisibilities,
            Pageable pageable
    ) {
        // STEP 1. 커버링 인덱스 활용 (ID만 조회)
        JPAQuery<Long> idsQuery = queryFactory
                .select(archiveStats.id)
                .from(archiveStats)
                .where(
                        eqUserId(filterUserId),
                        inVisibilities(allowedVisibilities)
                )
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        if (filterUserId != null) {
            idsQuery.join(archiveStats.archive, archive);
        }

        List<Long> ids = idsQuery.fetch();

        // STEP 2. 데이터 조회 (Archive + ArchiveStats + User Fetch Join)
        List<ArchiveDto.ArchivePageResponse> sortedContent = new ArrayList<>();

        if (!ids.isEmpty()) {
            List<ArchiveDto.ArchivePageResponse> content = queryFactory
                    .select(new QArchiveDto_ArchivePageResponse(
                            archive.id,
                            archive.title,
                            archive.thumbnailKey,
                            archiveStats.viewCount,
                            archiveStats.likeCount,
                            archiveStats.hotScore,
                            archiveStats.visibility,
                            archive.createdAt,
                            archive.lastModifiedAt,
                            archive.user.nickname
                    ))
                    .from(archive)
                    .join(archiveStats).on(archive.id.eq(archiveStats.id)) // Shared PK 1:1 Join
                    .join(archive.user) // 작성자 조인
                    .where(archive.id.in(ids))
                    .fetch();

            // ID 순서 보장을 위해 Map으로 변환 후 재정렬
            Map<Long, ArchiveDto.ArchivePageResponse> contentMap = content.stream()
                    .collect(Collectors.toMap(ArchiveDto.ArchivePageResponse::getArchiveId, Function.identity()));

            sortedContent = ids.stream().map(contentMap::get).toList();
        }

        // Count Query (최적화)
        JPAQuery<Long> countQuery = queryFactory
                .select(archiveStats.count())
                .from(archiveStats)
                .where(
                        eqUserId(filterUserId),
                        inVisibilities(allowedVisibilities)
                );

        // 동적 조인: Count 쿼리에서도 동일하게 적용
        if (filterUserId != null) {
            countQuery.join(archiveStats.archive, archive);
        }

        return PageableExecutionUtils.getPage(sortedContent, pageable, countQuery::fetchOne);
    }

    // --- Dynamic Filters ---

    private BooleanExpression eqUserId(Long userId) {
        return userId != null ? archive.user.id.eq(userId) : null;
    }

    private BooleanExpression inVisibilities(List<Visibility> visibilities) {
        return (visibilities != null && !visibilities.isEmpty())
                ? archiveStats.visibility.in(visibilities)
                : null;
    }

    // --- Dynamic Sort Strategy ---

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, archiveStats.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, archiveStats.createdAt);
                case "viewCount" -> new OrderSpecifier<>(direction, archiveStats.viewCount);
                case "likeCount" -> new OrderSpecifier<>(direction, archiveStats.likeCount);
                case "hotScore" -> new OrderSpecifier<>(direction, archiveStats.hotScore);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, archiveStats.createdAt));
        }

        boolean hasIdSort = orders.stream().anyMatch(o -> o.getTarget().equals(archiveStats.id));
        if (!hasIdSort) {
            Order lastDirection = orders.get(orders.size() - 1).getOrder();
            orders.add(new OrderSpecifier<>(lastDirection, archiveStats.id));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}