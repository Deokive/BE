package com.depth.deokive.domain.archive.repository;

import com.depth.deokive.domain.archive.dto.ArchiveMeResponseDto;
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
    public Page<ArchiveMeResponseDto> searchMyArchive(Pageable pageable, Long userId) {
        // 1. 컨텐츠 조회 (Projections를 사용해서 DTO로 조회)
        List<ArchiveMeResponseDto> content = queryFactory
                .select(Projections.fields(ArchiveMeResponseDto.class,
                        archive.id,
                        archive.title,
                        file.filePath.as("thumbnail"), // File 엔티티 URL 매핑
                        archive.createdAt,
                        archive.lastModifiedAt
                ))
                .from(archive)
                // 썸네일 Join (N+1 해결 -> 필요한 필드만 select해서)
                .leftJoin(archiveFileMap).on(
                        archiveFileMap.archive.eq(archive)
                                .and(archiveFileMap.sequence.eq(1)) // 썸네일만
                )
                .leftJoin(archiveFileMap.file, file)
                .where(
                        archive.user.id.eq(userId) // 내 아카이브만
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifier(pageable))
                .fetch();

        // 2. 카운트 쿼리
        JPAQuery<Long> countQuery = queryFactory
                .select(archive.count())
                .from(archive)
                .where(archive.user.id.eq(userId));

        return new PageImpl<>(content, pageable, countQuery.fetchOne());
    }

    // 정렬 조건
    private OrderSpecifier<?> getOrderSpecifier(Pageable pageable) {
        if(!pageable.getSort().isEmpty()) {
            for(Sort.Order order : pageable.getSort()) {
                Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;

                switch(order.getProperty()) {
                    /*
                    case "popular": // 인기순
                        return new OrderSpecifier<>(direction, archiveLikeCount.likeCount);
                    case "view": // 조회순
                        return new OrderSpecifier<>(direction, archiveViewCount.viewCount);
                     */
                    case "createdAt": // 최신순
                        return new OrderSpecifier<>(direction, archive.createdAt);
                }
            }
        }
        // 기본 -> 최신순
        return new OrderSpecifier<>(Order.DESC, archive.createdAt);
    }
}
