package com.depth.deokive.domain.diary.repository;

import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.dto.QDiaryDto_DiaryPageResponse;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.depth.deokive.domain.diary.entity.QDiary.diary;

@Repository
@RequiredArgsConstructor
public class DiaryQueryRepository {

    private final JPAQueryFactory queryFactory;

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
                            diary.thumbnailUrl, // 역정규화 컬럼 사용 (Zero-Join)
                            diary.recordedAt,
                            diary.visibility
                    ))
                    .from(diary)
                    .where(diary.id.in(ids))
                    .orderBy(diary.recordedAt.desc(), diary.id.desc())
                    .fetch();
        }

        // SEQ 3. Count Query
        JPAQuery<Long> countQuery = queryFactory
                .select(diary.count())
                .from(diary)
                .where(
                        diary.diaryBook.id.eq(bookId),
                        eqVisibility(allowedVisibilities)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
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