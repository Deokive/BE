package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
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
import java.util.Map;
import java.util.stream.Collectors;

import static com.depth.deokive.domain.archive.entity.QArchive.archive;
import static com.depth.deokive.domain.archive.entity.QArchiveFileMap.archiveFileMap;
import static com.depth.deokive.domain.file.entity.QFile.file;

@Repository
@RequiredArgsConstructor
public class ArchiveQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 아카이브 목록 조회 (내 아카이브 / 친구 아카이브 공용)
     * @param userId 대상 유저 ID
     * @param isMe 본인 여부 (true: 전체 공개, false: 공개/일부공개만)
     */
    public Page<ArchiveDto.Response> searchArchives(Long userId, boolean isMe, Pageable pageable) {

        // STEP 1. ID만 빠르게 조회 (커버링 인덱스 효과)
        List<Long> ids = queryFactory
                .select(archive.id)
                .from(archive)
                .where(
                        archive.user.id.eq(userId),
                        filterVisibility(isMe)
                )
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 데이터가 없으면 빈 페이지 반환
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }

        // STEP 2. 실제 데이터 조회 (엔티티 조회)
        List<Archive> archives = queryFactory
                .selectFrom(archive)
                .where(archive.id.in(ids))
                .orderBy(getOrderSpecifiers(pageable)) // IN절 순서 보장을 위해 정렬 재적용
                .fetch();

        // STEP 3. 썸네일 조회 (Map 메모리 매핑 - N+1 해결)
        Map<Long, String> thumbnailMap = getThumbnailMap(ids);

        // STEP 4. DTO 변환
        List<ArchiveDto.Response> content = archives.stream()
                .map(a -> ArchiveDto.Response.from(a, thumbnailMap.get(a.getId())))
                .toList();

        // Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(archive.count())
                .from(archive)
                .where(
                        archive.user.id.eq(userId),
                        filterVisibility(isMe)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 공개 범위 필터링 조건
    private BooleanExpression filterVisibility(boolean isMe) {
        if (isMe) {
            return null; // 내 거면 모든 상태 조회
        }
        return archive.visibility.in(Visibility.PUBLIC, Visibility.RESTRICTED); // 친구면 비공개 제외
    }

    // 썸네일 조회 로직 (메모리 매핑)
    private Map<Long, String> getThumbnailMap(List<Long> archiveIds) {
        return queryFactory
                .select(archiveFileMap.archive.id, file.filePath)
                .from(archiveFileMap)
                .join(archiveFileMap.file, file)
                .where(
                        archiveFileMap.archive.id.in(archiveIds),
                        file.isThumbnail.isTrue() // 썸네일인 파일만 조회
                )
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(archiveFileMap.archive.id),
                        tuple -> tuple.get(file.filePath),
                        (oldValue, newValue) -> oldValue
                ));
    }

    // 정렬 조건 처리
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
                default -> null;
            };
            if (orderSpecifier != null) orders.add(orderSpecifier);
        }

        return orders.toArray(new OrderSpecifier[0]);
    }
}