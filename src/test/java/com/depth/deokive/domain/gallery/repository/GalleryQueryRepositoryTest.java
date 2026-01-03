package com.depth.deokive.domain.gallery.repository;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GalleryQueryRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(GalleryQueryRepositoryTest.class);

    @Autowired private GalleryQueryRepository galleryQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private GalleryBookRepository galleryBookRepository;
    @Autowired private FileRepository fileRepository;

    @Autowired private JdbcTemplate jdbcTemplate; // Bulk Insert용
    @Autowired private EntityManager em;

    @Autowired private EntityManagerFactory entityManagerFactory;

    private User user;
    private Archive archive;
    private GalleryBook galleryBook;
    private File file;

    @BeforeEach
    void setUp() {
        // 1. User 생성
        user = userRepository.save(User.builder()
                .email("architect@deokive.com")
                .nickname("SeniorArchitect")
                .username("testUsername")
                .password("securePass123!")
                .role(Role.USER)
                .build());

        // 2. Archive 생성
        archive = archiveRepository.save(Archive.builder()
                .user(user)
                .title("Architect's Portfolio")
                .visibility(Visibility.PUBLIC)
                .build());

        // 3. GalleryBook 생성
        galleryBook = galleryBookRepository.save(GalleryBook.builder()
                .archive(archive)
                .title("Design Patterns")
                .build());

        // 4. File 생성 (테스트용 이미지)
        file = fileRepository.save(File.builder()
                .s3ObjectKey("files/" + UUID.randomUUID())
                .filename("test_image.jpg")
                .filePath("https://cdn.deokive.com/files/test_image.jpg")
                .fileSize(1024L)
                .mediaType(MediaType.IMAGE)
                .build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("✅ N+1 방지 완벽 검증: 쿼리 개수가 정확히 3개인지 확인한다")
    void verifyNPlusOneWithQueryCount() {
        // Given
        int dataSize = 20;
        bulkInsertGalleries(dataSize); // 20개 삽입
        em.clear(); // 1차 캐시 초기화 (DB 조회 강제)

        // Hibernate Statistics 준비
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true); // 통계 수집 활성화
        statistics.clear(); // 기존 통계 초기화 (setUp 과정의 쿼리 카운트 제거)

        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

        // When
        log.info("🚀 쿼리 실행 시작");
        Page<GalleryDto.Response> result = galleryQueryRepository.searchGalleriesByArchive(archive.getId(), pageable);
        log.info("🚀 쿼리 실행 종료");

        // Then 1: 데이터 검증
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent().get(0).getThumbnailUrl()).isNotNull();

        // Then 2: 쿼리 개수 검증 (핵심)
        long queryCount = statistics.getPrepareStatementCount();
        log.info("실행된 SQL 개수: {}", queryCount);

        // 예상되는 쿼리:
        // 1. ID 조회 (Covering Index)
        // 2. Content 조회 (IN 절)
        // 3. Count 조회
        // 총 3개여야 함. (N+1 발생 시 13개 이상)
        assertThat(queryCount).isEqualTo(3); //

        // INFO 10874 --- [    Test worker] c.d.d.d.g.r.GalleryQueryRepositoryTest   : 실행된 SQL 개수: 3
    }

    @Test
    @DisplayName("🚀 대용량 데이터 조회 성능 테스트 (Deep Pagination)")
    void testDeepPaginationPerformance() {
        // Given: 100만 건 데이터 삽입 (환경에 따라 10만건 등으로 조절 가능)
        int totalCount = 100_000;
        log.info("🚀 데이터 {}건 Bulk Insert 시작...", totalCount);

        StopWatch insertSw = new StopWatch();
        insertSw.start();
        bulkInsertGalleries(totalCount);
        insertSw.stop();
        log.info("✅ Bulk Insert 완료: {} ms", insertSw.getTotalTimeMillis());

        em.clear(); // 영속성 컨텍스트 비우기 (캐시 영향 제거)

        // When: 끝부분 페이지 조회 (Deep Pagination)
        // 10만건 중 99,990번째부터 10개 조회
        int pageNumber = (totalCount / 10) - 1;
        Pageable pageable = PageRequest.of(pageNumber, 10, Sort.by("createdAt").descending());

        log.info("🚀 Deep Pagination 조회 시작 (Page: {})", pageNumber);

        StopWatch querySw = new StopWatch();
        querySw.start();
        Page<GalleryDto.Response> result = galleryQueryRepository.searchGalleriesByArchive(archive.getId(), pageable);
        querySw.stop();

        log.info("✅ 조회 완료: {} ms", querySw.getTotalTimeMillis());

        // Then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(totalCount);

        // 성능 검증 (예: 1초 이내) - Two-Step Query 덕분에 매우 빠름
        assertThat(querySw.getTotalTimeMillis()).isLessThan(1000);
    }

    @Test
    @DisplayName("🔍 실행 계획 검증: 진짜 인덱스(idx_gallery_archive_created)를 타는지 확인")
    void checkExecutionPlan() {
        // Given
        int dataSize = 1000;
        bulkInsertGalleries(dataSize);

        // When
        // H2에서 실행 계획을 보는 명령어: EXPLAIN ANALYZE SELECT ...
        String sql = """
        EXPLAIN ANALYZE
        SELECT id 
        FROM gallery 
        WHERE archive_id = %d 
        ORDER BY created_at DESC 
        LIMIT 10 OFFSET 900
        """.formatted(archive.getId());

        List<String> plan = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));

        // Then
        log.info("📋 Execution Plan 결과:");
        plan.forEach(log::info);

        // 검증: 실행 계획 문자열에 인덱스 이름이 포함되어 있어야 함
        // (H2 버전에 따라 출력 포맷이 다르지만 보통 인덱스명이 나옵니다)
        String fullPlan = String.join("\n", plan);
        assertThat(fullPlan).containsIgnoringCase("idx_gallery_archive_created")
                .as("커버링 인덱스(idx_gallery_archive_created)가 실행 계획에 포함되어야 합니다.");
    }

    /**
     * JDBC Batch Insert를 이용한 고속 데이터 삽입
     */
    private void bulkInsertGalleries(int count) {
        String sql = "INSERT INTO gallery (archive_id, gallery_book_id, file_id, created_at, last_modified_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        // 배치 사이즈 설정
        int batchSize = 1000;

        List<Object[]> batchArgs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDateTime now = LocalDateTime.now().minusMinutes(i); // 정렬 테스트를 위해 시간 차등
            batchArgs.add(new Object[]{
                    archive.getId(),
                    galleryBook.getId(),
                    file.getId(),
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            });

            if (batchArgs.size() == batchSize) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
    }
}