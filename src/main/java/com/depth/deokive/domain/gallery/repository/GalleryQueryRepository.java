package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.gallery.dto.GalleryDto;
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

import static com.depth.deokive.domain.archive.entity.QArchive.archive;
import static com.depth.deokive.domain.file.entity.QFile.file;
import static com.depth.deokive.domain.gallery.entity.QGallery.gallery;

@Repository
@RequiredArgsConstructor
public class GalleryQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<GalleryDto.Response> searchGalleriesByArchive(Long archiveId, Pageable pageable) {

        // STEP 1. 커버링 인덱스 활용 (ID만 조회)
        // created_at 정렬 인덱스를 타므로 매우 빠름. 데이터 블록 접근 X
        List<Long> ids = queryFactory
                .select(gallery.id)
                .from(gallery)
                .where(gallery.archiveId.eq(archiveId)) // 반정규화된 컬럼 사용
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // STEP 2. 데이터 조회 (WHERE IN)
        // 찾아낸 소수의 ID에 대해서만 File 조인 수행
        List<GalleryDto.Response> content = new ArrayList<>();

        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(Projections.constructor(GalleryDto.Response.class,
                            gallery.id,
                            gallery.originalKey,
                            gallery.createdAt,
                            gallery.lastModifiedAt
                    ))
                    .from(gallery)
                    .where(gallery.id.in(ids))
                    .orderBy(getOrderSpecifiers(pageable)) // IN절 순서 보장을 위해 재정렬
                    .fetch();
        }

        // Count Query (최적화)
        JPAQuery<Long> countQuery = queryFactory
                .select(gallery.count())
                .from(gallery)
                .where(gallery.archiveId.eq(archiveId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, gallery.createdAt)};
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "createdAt" -> new OrderSpecifier<>(direction, gallery.createdAt);
                case "lastModifiedAt" -> new OrderSpecifier<>(direction, gallery.lastModifiedAt);
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, gallery.createdAt));
        }

        boolean hasIdSort = orders.stream().anyMatch(o -> o.getTarget().equals(gallery.id));
        if (!hasIdSort) {
            Order lastDirection = orders.get(orders.size() - 1).getOrder();
            orders.add(new OrderSpecifier<>(lastDirection, gallery.id));
        }

        // Tie-Breaker -> for Integrity
        orders.add(new OrderSpecifier<>(Order.DESC, gallery.id));

        return orders.toArray(new OrderSpecifier[0]);
    }
}