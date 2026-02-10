package com.depth.deokive.domain.diary.repository;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.service.PaginationCountCacheService;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.dto.QDiaryDto_DiaryPageResponse;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.depth.deokive.domain.diary.entity.QDiary.diary;

@Repository
@RequiredArgsConstructor
public class DiaryQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final PaginationCountCacheService paginationCountCacheService;

    public Page<DiaryDto.DiaryPageResponse> findDiaries(
            Long bookId,
            List<Visibility> allowedVisibilities,
            Pageable pageable
    ) {
        // SEQ 1. Covering Index
        List<Long> ids = queryFactory
                .select(diary.id)
                .from(diary)
                .where(
                        diary.diaryBook.id.eq(bookId),
                        eqVisibility(allowedVisibilities) // 친구 사이여도 Private는 안옴
                )
                .orderBy(diary.recordedAt.desc(), diary.id.desc()) // Tie-Breaker 추가
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<DiaryDto.DiaryPageResponse> content = new ArrayList<>();

        // SEQ 2. Data Fetch Using IDs
        if (!ids.isEmpty()) {
            content = queryFactory
                    .select(new QDiaryDto_DiaryPageResponse(
                            diary.id,
                            diary.title,
                            diary.thumbnailKey,
                            diary.recordedAt,
                            diary.visibility
                    ))
                    .from(diary)
                    .where(diary.id.in(ids))
                    .orderBy(diary.recordedAt.desc(), diary.id.desc())
                    .fetch();
        }

        // SEQ 3. Count Query - Redis 캐싱
        String visKey = allowedVisibilities.stream()
                .map(Visibility::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String countCacheKey = "diary:" + bookId + ":" + visKey;

        return PageableExecutionUtils.getPage(content, pageable,
                () -> paginationCountCacheService.getCount(countCacheKey, () ->
                        queryFactory.select(diary.count())
                                .from(diary)
                                .where(
                                        diary.diaryBook.id.eq(bookId),
                                        eqVisibility(allowedVisibilities)
                                )
                                .fetchOne()
                ));
    }

    private BooleanExpression eqVisibility(List<Visibility> allowedVisibilities) {
        if (allowedVisibilities == null || allowedVisibilities.isEmpty()) {
            return diary.visibility.in(allowedVisibilities);
        }

        // 전체 조회 권한(Owner)인 경우 필터링 생략 -> Index Pure Scan 유도
        if (allowedVisibilities.size() == Visibility.values().length) {
            return null;
        }

        return diary.visibility.in(allowedVisibilities);
    }
}