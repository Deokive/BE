package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.dto.QArchiveDto_ArchivePageResponse;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
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

import static com.depth.deokive.domain.archive.entity.QArchive.archive;
import static com.depth.deokive.domain.file.entity.QFile.file;

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
        // 인덱스만 태워서 정렬된 ID 리스트를 빠르게 가져옵니다. (데이터 블록 접근 X)
        List<Long> ids = queryFactory
                .select(archive.id)
                .from(archive)
                .where(
                        eqUserId(filterUserId),
                        inVisibilities(allowedVisibilities)
                )
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // STEP 2. 데이터 조회 (WHERE IN)
        // 찾아낸 소수의 ID에 대해서만 Banner File 및 User 조인을 수행합니다.
        List<ArchiveDto.ArchivePageResponse> content = new ArrayList<>();

        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(new QArchiveDto_ArchivePageResponse(
                            archive.id,
                            archive.title,
                            archive.bannerFile.filePath, // 1:1 Banner Join
                            archive.viewCount,
                            archive.likeCount,
                            archive.hotScore,
                            archive.visibility,
                            archive.createdAt,
                            archive.lastModifiedAt,
                            archive.user.nickname
                    ))
                    .from(archive)
                    .join(archive.user) // 작성자 조인
                    .where(archive.id.in(ids))
                    .orderBy(getOrderSpecifiers(pageable)) // ID IN 순서 보장을 위해 재정렬
                    .fetch();
        }

        // Count Query (최적화)
        JPAQuery<Long> countQuery = queryFactory
                .select(archive.count())
                .from(archive)
                .where(
                        eqUserId(filterUserId),
                        inVisibilities(allowedVisibilities)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // --- Dynamic Filters ---

    private BooleanExpression eqUserId(Long userId) {
        return userId != null ? archive.user.id.eq(userId) : null;
    }

    private BooleanExpression inVisibilities(List<Visibility> visibilities) {
        return (visibilities != null && !visibilities.isEmpty())
                ? archive.visibility.in(visibilities)
                : null;
    }

    // --- Dynamic Sort Strategy ---

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, archive.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, archive.createdAt);
                case "lastModifiedAt" -> new OrderSpecifier<>(direction, archive.lastModifiedAt);
                case "viewCount" -> new OrderSpecifier<>(direction, archive.viewCount);
                case "likeCount" -> new OrderSpecifier<>(direction, archive.likeCount);
                case "hotScore" -> new OrderSpecifier<>(direction, archive.hotScore);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, archive.createdAt));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}