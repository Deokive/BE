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

        // STEP 1. 생성일(createdAt) 또는 수정일(lastModifiedAt) 정렬인 경우 최적화 경로 사용
        boolean isOptimizedPath = filterUserId != null && isOptimizableSort(pageable);

        List<Long> ids;
        JPAQuery<Long> countQuery;

        // STEP 2. 커버링 인덱스 활용 (ID만 조회) && 정렬 조건 분기 : My Archives vs Global Archives
        if (isOptimizedPath) {
            // Case 1. My Archives(Me or Friends) -> Archive Table Scan
            ids = queryFactory
                    .select(archive.id)
                    .from(archive)
                    .where(
                            archive.user.id.eq(filterUserId),
                            inVisibilitiesForArchive(allowedVisibilities) // Archive 엔티티 조건 사용
                    )
                    .orderBy(getArchiveOrderSpecifiers(pageable)) // Archive 컬럼 기준 정렬
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            countQuery = queryFactory
                    .select(archive.count())
                    .from(archive)
                    .where(
                            archive.user.id.eq(filterUserId),
                            inVisibilitiesForArchive(allowedVisibilities)
                    );
        } else {
            // Case 2. Global Archives -> ArchiveStats 기반 조회
            JPAQuery<Long> idsQuery = queryFactory
                    .select(archiveStats.id)
                    .from(archiveStats)
                    .where(
                            eqUserIdForStats(filterUserId),
                            inVisibilitiesForStats(allowedVisibilities)
                    )
                    .orderBy(getStatsOrderSpecifiers(pageable))
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize());

            // 필터 유저가 있으면 조인 필요
            if (filterUserId != null) {
                idsQuery.join(archiveStats.archive, archive);
            }

            ids = idsQuery.fetch();

            // Count Query (ArchiveStats 테이블 대상)
            countQuery = queryFactory
                    .select(archiveStats.count())
                    .from(archiveStats)
                    .where(
                            eqUserIdForStats(filterUserId),
                            inVisibilitiesForStats(allowedVisibilities)
                    );

            // TODO: 테스트 하고 다시 돌아올 것
            if (filterUserId != null) {
                countQuery.join(archiveStats.archive, archive);
            }
        }

        // STEP 3. Data Fetching
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
                    .join(archiveStats).on(archive.id.eq(archiveStats.id)) // 1:1 Shared PK Join
                    .join(archive.user) // 작성자 Fetch Join
                    .where(archive.id.in(ids))
                    .fetch();

            // ID 순서 보장을 위해 Map으로 변환 후 재정렬
            Map<Long, ArchiveDto.ArchivePageResponse> contentMap = content.stream()
                    .collect(Collectors.toMap(ArchiveDto.ArchivePageResponse::getArchiveId, Function.identity()));

            sortedContent = ids.stream()
                    .map(contentMap::get)
                    .collect(Collectors.toList());
        }

        return PageableExecutionUtils.getPage(sortedContent, pageable, countQuery::fetchOne);
    }

    // --- Dynamic Filters ---
    // ---- For Archive Entity -----
    private boolean isOptimizableSort(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order ->
                        "createdAt".equals(order.getProperty()) ||
                        "lastModifiedAt".equals(order.getProperty())
                );
    }

    private BooleanExpression inVisibilitiesForArchive(List<Visibility> visibilities) {
        return (visibilities != null && !visibilities.isEmpty())
                ? archive.visibility.in(visibilities)
                : null;
    }

    // Archive 테이블용 정렬 Specifier
    private OrderSpecifier<?>[] getArchiveOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            switch (order.getProperty()) {
                case "createdAt" -> orders.add(new OrderSpecifier<>(direction, archive.createdAt));
                case "lastModifiedAt" -> orders.add(new OrderSpecifier<>(direction, archive.lastModifiedAt));
                // 'id'나 다른 컬럼은 현재 인덱스 최적화 대상이 아니므로 제외하거나 필요 시 추가
            }
        }

        // 유효한 정렬 기준이 없으면 기본값(createdAt DESC)
        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier<>(Order.DESC, archive.createdAt));
        }

        // Tie-Breaker -> ID 정렬이 없으면 추가 (Pagination 일관성)
        boolean hasIdSort = orders.stream()
                .anyMatch(o -> o.getTarget().equals(archive.id));

        if (!hasIdSort) {
            Order lastDirection = orders.isEmpty() ? Order.DESC : orders.get(orders.size() - 1).getOrder();
            orders.add(new OrderSpecifier<>(lastDirection, archive.id));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }

    // ----- For ArchiveStats --------

    private BooleanExpression eqUserIdForStats(Long userId) {
        return userId != null ? archive.user.id.eq(userId) : null;
    }

    private BooleanExpression inVisibilitiesForStats(List<Visibility> visibilities) {
        return (visibilities != null && !visibilities.isEmpty())
                ? archiveStats.visibility.in(visibilities)
                : null;
    }

    // ArchiveStats 테이블용 정렬 Specifier
    private OrderSpecifier<?>[] getStatsOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

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

        // Tie-Breaker
        boolean hasIdSort = orders.stream().anyMatch(o -> o.getTarget().equals(archiveStats.id));
        if (!hasIdSort) {
            Order lastDirection = orders.isEmpty() ? Order.DESC : orders.get(orders.size() - 1).getOrder();
            orders.add(new OrderSpecifier<>(lastDirection, archiveStats.id));
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}