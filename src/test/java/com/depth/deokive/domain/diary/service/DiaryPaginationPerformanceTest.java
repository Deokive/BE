package com.depth.deokive.domain.diary.service;

import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.repository.DiaryQueryRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DiaryPaginationPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(DiaryPaginationPerformanceTest.class);

    @Autowired
    private DiaryService diaryService;

    @Autowired
    private DiaryQueryRepository diaryQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate; // 고속 데이터 삽입용

    private static final long USER_ID = 1L;
    private static final long ARCHIVE_ID = 100L;
    private static final int TOTAL_RECORDS = 100_000; // 10만 건 데이터
    private static final int PAGE_SIZE = 12;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO users (id, nickname, email, role, created_at, last_modified_at, is_email_verified, username, user_type) VALUES (?, ?, ?, 'USER', NOW(), NOW(), ?, ?, ?)", USER_ID, "tester", "test@test.com", true, "tester_username", "COMMON");
        jdbcTemplate.update("INSERT INTO archive (id, user_id, title, visibility, badge, view_count, like_count, hot_score, created_at, last_modified_at) VALUES (?, ?, ?, 'PUBLIC', 'NEWBIE', 0, 0, 0, NOW(), NOW())", ARCHIVE_ID, USER_ID, "Perf Archive");
        jdbcTemplate.update("INSERT INTO diary_book (archive_id, title, created_at, last_modified_at) VALUES (?, ?, NOW(), NOW())", ARCHIVE_ID, "Perf Book");

        String sql = "INSERT INTO diary (title, content, recorded_at, color, visibility, diary_book_id, created_by, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        List<Object[]> batchArgs = IntStream.range(0, TOTAL_RECORDS)
                .mapToObj(i -> new Object[]{
                        "Title " + i,
                        "Content " + i,
                        Date.valueOf(LocalDate.now().minusDays(i % 1000)), // 날짜 분산
                        "#FFFFFF",
                        "PUBLIC",
                        ARCHIVE_ID,
                        USER_ID
                })
                .toList();

        StopWatch setupWatch = new StopWatch();
        setupWatch.start();
        jdbcTemplate.batchUpdate(sql, batchArgs);
        setupWatch.stop();
        log.info("✅ Bulk Insert Completed: {} records in {} ms", TOTAL_RECORDS, setupWatch.getTotalTimeMillis());
    }

    @Test
    @DisplayName("Deep Pagination 성능 측정: 커버링 인덱스 적용 시 10만 건 중 9,000페이지 조회 성능 검증")
    void testDeepPaginationPerformance() {
        // given
        UserPrincipal userPrincipal = new UserPrincipal(USER_ID, "tester", null, null);

        int deepPageNumber = (TOTAL_RECORDS / PAGE_SIZE) - 100; // 끝에서 100번째 페이지
        DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
        request.setPage(deepPageNumber);
        request.setSize(PAGE_SIZE);

        log.info("🚀 Requesting Page: {} (Offset: ~{})", deepPageNumber, deepPageNumber * PAGE_SIZE);

        // when
        StopWatch queryWatch = new StopWatch();
        queryWatch.start();

        DiaryDto.PageListResponse response = diaryService.getDiaries(userPrincipal, ARCHIVE_ID, request);

        queryWatch.stop();
        long executionTime = queryWatch.getTotalTimeMillis();

        // then
        log.info("⏱️ Deep Pagination Execution Time: {} ms", executionTime);
        log.info("📄 Result Content Size: {}", response.getContent().size());

        assertThat(response.getContent()).hasSize(PAGE_SIZE);

        // 2. 성능 임계값 검증 (H2 메모리 DB 기준, 로컬 환경에 따라 다름)
        // 커버링 인덱스가 적용되었다면 10만 건 정도는 순식간에 가져와야 함 (보통 50~100ms 이내)
        assertThat(executionTime).isLessThan(1000L);
    }
}