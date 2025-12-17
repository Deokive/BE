package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveResponseDto;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static com.depth.deokive.domain.archive.entity.QArchive.archive;
import static com.depth.deokive.domain.archive.entity.QArchiveFileMap.archiveFileMap;
import static com.depth.deokive.domain.archive.entity.QArchiveLike.archiveLike;
import static com.depth.deokive.domain.archive.entity.QArchiveLikeCount.archiveLikeCount;
import static com.depth.deokive.domain.archive.entity.QArchiveViewCount.archiveViewCount;
import static com.depth.deokive.domain.file.entity.QFile.file;
import static com.depth.deokive.domain.user.entity.QUser.user;

@RequiredArgsConstructor
public class ArchiveRepositoryImpl implements ArchiveRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ArchiveResponseDto> searchArchiveList(Pageable pageable, Long userId) {
        // 1. 데이터 조회
        List<ArchiveResponseDto> content = queryFactory
                .select(Projections.constructor(ArchiveResponseDto.class,
                        archive.id,
                        archive.title,
                        user.nickname,
                        file.filePath,
                        archiveLikeCount.likeCount.coalesce(0L),
                        archiveViewCount.viewCount.coalesce(0L),
                        userId != null ? archiveLike.id.isNotNull() : com.querydsl.core.types.dsl.Expressions.asBoolean(false),
                        archive.createdAt
                        ))
                .from(archive)
                .join(archive.user, user)
                // 1-1. 좋아요 개수 Join
                .leftJoin(archiveLikeCount).on(archiveLikeCount.archiveId.eq(archive.id))
                // 1-2. 조회수 Join
                .leftJoin(archiveViewCount).on(archiveViewCount.archiveId.eq(archive.id))
                // 1-3. 썸네일 Join
                .leftJoin(archiveFileMap).on(
                        archiveFileMap.archive.eq(archive)
                                .and(archiveFileMap.sequence.eq(1))
                )
                // 1-4. 좋아요 여부 Join
                .leftJoin(archiveLike).on(
                        archiveLike.archive.eq(archive)
                                .and(userId != null ? archiveLike.user.id.eq(userId) : archiveLike.user.id.isNull())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifier(pageable))
                .fetch();

        // 2. 카운트 쿼리
        JPAQuery<Long> countQuery = queryFactory
                .select(archive.count())
                .from(archive);

        return new PageImpl<>(content, pageable, countQuery.fetchOne());
    }

    // 정렬 조건(최신순, 인기순, 조회순)
    private OrderSpecifier<?> getOrderSpecifier(Pageable pageable) {
        if(!pageable.getSort().isEmpty()) {
            for(Sort.Order order : pageable.getSort()) {
                Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;

                switch(order.getProperty()) {
                    case "popular": // 인기순
                        return new OrderSpecifier<>(direction, archiveLikeCount.likeCount);
                    case "view": // 조회순
                        return new OrderSpecifier<>(direction, archiveViewCount.viewCount);
                    case "latest": // 최신순
                        return new OrderSpecifier<>(direction, archive.createdAt);
                }
            }
        }
        // 기본 -> 최신순
        return new OrderSpecifier<>(Order.DESC, archive.createdAt);
    }
}
