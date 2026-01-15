package com.depth.deokive.common.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.entity.ArchiveStats;
import com.depth.deokive.domain.archive.repository.ArchiveStatsRepository;
import com.depth.deokive.domain.post.entity.PostStats;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.controller.SystemSchedulerController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Hot Score 계산 로직 검증 (Service/Scheduler Integration)")
class HotScoreCalculationTest extends IntegrationTestSupport {

    @Autowired
    private SystemSchedulerController systemSchedulerController;

    @Autowired
    private PostStatsRepository postStatsRepository;

    @Autowired
    private ArchiveStatsRepository archiveStatsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테스트용 고정 User ID
    private static final Long USER_ID = 1L;

    @BeforeEach
    void initData() {
        // 1. 기존 데이터 클린업 (FK 제약 고려하여 자식 테이블부터 삭제)
        jdbcTemplate.execute("DELETE FROM post_stats");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM archive_stats");
        jdbcTemplate.execute("DELETE FROM archive");
        jdbcTemplate.execute("DELETE FROM users");

        // 2. 더미 유저 생성 (JdbcTemplate 사용)
        String sqlUser = "INSERT INTO users (id, email, username, nickname, password, role, user_type, is_email_verified, created_at, last_modified_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        jdbcTemplate.update(sqlUser, USER_ID, "tester@test.com", "tester", "Tester", "pw", Role.USER.name(), UserType.COMMON.name(), true);
    }

    @Test
    @DisplayName("시나리오 1: [Active Window] 생성된 지 7일 이내 게시글의 핫스코어 계산 검증")
    void verifyActiveHotScoreCalculation() {
        // Given: PDF 샘플 데이터 기반 설정
        // Case A (Row 10): Like 75, View 55000, 96h ago -> Expected ~81.3
        createPostData(10L, 75L, 55000L, 96);
        createArchiveData(10L, 75L, 55000L, 96, Visibility.PUBLIC);

        // Case B (Row 11): Like 50, View 50000, 120h ago -> Expected ~68.74
        createPostData(11L, 50L, 50000L, 120);
        createArchiveData(11L, 50L, 50000L, 120, Visibility.PUBLIC);

        // When: 스케줄러 강제 실행
        systemSchedulerController.triggerHotScore();

        // Then: PostStats 검증
        PostStats postA = postStatsRepository.findById(10L).orElseThrow();
        PostStats postB = postStatsRepository.findById(11L).orElseThrow();

        // 오차범위 0.5 내외 허용 (DB의 NOW()와 Java의 LocalDateTime.now() 간 미세한 차이 고려)
        assertThat(postA.getHotScore()).isCloseTo(81.3, within(0.5));
        assertThat(postB.getHotScore()).isCloseTo(68.74, within(0.5));

        // Then: ArchiveStats 검증 (Post와 동일 로직)
        ArchiveStats archiveA = archiveStatsRepository.findById(10L).orElseThrow();
        ArchiveStats archiveB = archiveStatsRepository.findById(11L).orElseThrow();

        assertThat(archiveA.getHotScore()).isCloseTo(81.3, within(0.5));
        assertThat(archiveB.getHotScore()).isCloseTo(68.74, within(0.5));
    }

    @Test
    @DisplayName("시나리오 2: [Penalty Window] 생성된 지 7일 + 1시간 이내(Gatekeeper Line)일 때 0.5배 패널티 적용 확인")
    void verifyPenaltyHotScoreCalculation() {
        // Given: 168시간(7일)을 갓 넘긴 시점 (168.5시간 전)
        // 정상 점수 예상치:
        // Score = (20 * ln(101) + 3 * ln(10001)) * exp(-0.004 * 168.5)
        //       ≈ (20 * 4.615 + 3 * 9.21) * 0.509
        //       ≈ (92.3 + 27.63) * 0.509
        //       ≈ 119.93 * 0.509 ≈ 61.04
        // Penalty 적용 (x 0.5) ≈ 30.52

        long likeCount = 100;
        long viewCount = 10000;
        int hoursAgo = 168; // 168시간 + a (분 단위 조정을 위해 LocalDateTime 사용 예정이나 여기선 시간단위 근사치)

        // 시간 정밀 조작: 168시간 30분 전
        LocalDateTime penaltyTime = LocalDateTime.now().minusHours(168).minusMinutes(30);

        createPostDataWithTime(20L, likeCount, viewCount, penaltyTime);
        createArchiveDataWithTime(20L, likeCount, viewCount, penaltyTime, Visibility.PUBLIC);

        // When
        systemSchedulerController.triggerHotScore();

        // Then
        PostStats post = postStatsRepository.findById(20L).orElseThrow();
        ArchiveStats archive = archiveStatsRepository.findById(20L).orElseThrow();

        // 패널티가 적용된 값(약 30.5)인지 확인 (패널티 미적용 시 약 61.0)
        assertThat(post.getHotScore()).isCloseTo(30.5, within(1.0));
        assertThat(archive.getHotScore()).isCloseTo(30.5, within(1.0));
    }

    @Test
    @DisplayName("시나리오 3: [Archive Visibility] 비공개 아카이브는 핫스코어 계산에서 제외되어야 한다.")
    void verifyPrivateArchiveExclusion() {
        // Given: 조건은 좋으나 Private인 아카이브
        // Active Window (96h ago), High Stats
        createArchiveData(30L, 1000L, 100000L, 96, Visibility.PRIVATE);

        // When
        systemSchedulerController.triggerHotScore();

        // Then
        ArchiveStats stats = archiveStatsRepository.findById(30L).orElseThrow();

        // 초기값 0.0 유지 확인 (업데이트 쿼리의 WHERE 조건에 걸려야 함)
        assertThat(stats.getHotScore()).isEqualTo(0.0);
    }

    @ParameterizedTest(name = "Rank {0} (Archive{1}): Like={2}, View={3}, Age={4}h -> Expect Score={5}")
    @CsvSource({
            // Rank, ArchiveId, Like, View, Age(h), ExpectedScore, Status
            "1,   2,  275, 95000,   0, 146.79, Active",
            "2,   1,  300, 10000,   0, 141.77, Active",
            "3,   3,  250, 90000,  24, 131.48, Active",
            "4,   4,  225, 85000,  24, 129.42, Active",
            "5,   5,  200, 80000,  48, 115.49, Active",
            "6,   6,  175, 75000,  48, 113.14, Active",
            "7,   7,  150, 70000,  72, 100.33, Active",
            "8,   8,  125, 65000,  72,  97.45, Active",
            "9,   9,  100, 60000,  96,  85.35, Active",
            "10, 10,   75, 55000,  96,  81.30, Active",
            "11, 11,   50, 50000, 120,  68.74, Active",
            "12, 12,   25, 45000, 120,  60.21, Active",
            "13, 16,   75, 25000, 168,  59.75, Active", // 168h 꽉 찬 시점 (아직 Active)
            "14, 15,   50, 30000, 168,  55.95, Active",
            "15, 14,   25, 35000, 144,  54.28, Active",

            // Penalized Group: 실제로는 216h 등이 지났지만, 점수는 168h 시점에 0.5배 되어 박제된 값임.
            // 따라서 테스트에서는 이들이 '방금 막 7일이 지나 패널티를 받는 상황(168.5h)'으로 가정하고 로직을 검증함.
            "16, 20,  175,  5000, 216,  32.93, Penalized",
            "17, 19,  150, 10000, 216,  32.68, Penalized",
            "18, 18,  125, 15000, 192,  32.06, Penalized",
            "19, 17,  100, 20000, 192,  31.16, Penalized",
            "20, 13,    0, 40000, 144,  17.87, Active"
    })
    void verifyHotScoreFromPdfData(int rank, Long archiveId, Long likeCount, Long viewCount,
                                   int ageHours, double expectedScore, String status) {
        // Given
        LocalDateTime createdAt;
        if ("Active".equals(status)) {
            // Active는 현재 시간 기준 경과 시간 적용
            createdAt = LocalDateTime.now().minusHours(ageHours);
        } else {
            // Penalized는 PDF 상의 Age(예: 216h)가 아니라,
            // "패널티 로직이 실행되는 시점(Gatekeeper Line)"인 168시간 + 30분 전으로 설정하여
            // 스케줄러가 패널티 공식(*0.5)을 수행하도록 유도
            createdAt = LocalDateTime.now().minusHours(168).minusMinutes(30);
        }

        // 데이터 주입 (Post & Archive 동일하게 설정)
        createPostDataWithTime(archiveId, likeCount, viewCount, createdAt);
        createArchiveDataWithTime(archiveId, likeCount, viewCount, createdAt, Visibility.PUBLIC);

        // When: 스케줄러 실행
        systemSchedulerController.triggerHotScore();

        // Then: 점수 검증
        ArchiveStats stats = archiveStatsRepository.findById(archiveId).orElseThrow();

        // 허용 오차 0.5 (DB 시간과 Java 시간의 미세 차이, 부동소수점 연산 오차 고려)
        assertThat(stats.getHotScore())
                .as("Rank %d (ID %d) - %s Expect: %.2f, Actual: %.2f",
                        rank, archiveId, status, expectedScore, stats.getHotScore())
                .isCloseTo(expectedScore, within(0.5));
    }

    // --- Helper Methods using JdbcTemplate for High Performance Setup ---

    private void createPostData(Long id, Long like, Long view, int hoursAgo) {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(hoursAgo);
        createPostDataWithTime(id, like, view, createdAt);
    }

    private void createPostDataWithTime(Long id, Long like, Long view, LocalDateTime createdAt) {
        // 1. Post 생성
        String sqlPost = "INSERT INTO post (id, title, content, category, user_id, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sqlPost, id, "Test Post " + id, "Content", Category.IDOL.name(), USER_ID, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));

        // 2. PostStats 생성 (초기 HotScore 0)
        String sqlStats = "INSERT INTO post_stats (post_id, view_count, like_count, hot_score, category, created_at) VALUES (?, ?, ?, 0.0, ?, ?)";
        jdbcTemplate.update(sqlStats, id, view, like, Category.IDOL.name(), Timestamp.valueOf(createdAt));
    }

    private void createArchiveData(Long id, Long like, Long view, int hoursAgo, Visibility visibility) {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(hoursAgo);
        createArchiveDataWithTime(id, like, view, createdAt, visibility);
    }

    private void createArchiveDataWithTime(Long id, Long like, Long view, LocalDateTime createdAt, Visibility visibility) {
        // 1. Archive 생성
        String sqlArchive = "INSERT INTO archive (id, title, user_id, visibility, badge, created_at, last_modified_at) VALUES (?, ?, ?, ?, 'NEWBIE', ?, ?)";
        jdbcTemplate.update(sqlArchive, id, "Test Archive " + id, USER_ID, visibility.name(), Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));

        // 2. ArchiveStats 생성
        String sqlStats = "INSERT INTO archive_stats (archive_id, view_count, like_count, hot_score, visibility, badge, created_at) VALUES (?, ?, ?, 0.0, ?, 'NEWBIE', ?)";
        jdbcTemplate.update(sqlStats, id, view, like, visibility.name(), Timestamp.valueOf(createdAt));

        // 3. 필수 하위 테이블 생성 (Delete 시 FK 제약 때문에 필요할 수 있으나 Insert엔 필수 아님, 생략)
    }
}