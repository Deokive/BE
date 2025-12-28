package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostQueryRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(PostQueryRepositoryTest.class);

    @Autowired private PostQueryRepository postQueryRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate; // Bulk Insert용

    private User user;
    private File file1;

    @BeforeEach
    void setUp() {
        // 1. User 생성
        user = User.builder()
                .email("test@test.com")
                .username("tester")
                .nickname("TestNick")
                .role(Role.USER)
                .build();
        em.persist(user);
        em.flush(); // User ID를 확보하기 위해 flush

        // 2. File 생성 (Thumbnail용)
        file1 = File.builder()
                .s3ObjectKey("files/thumb1.jpg")
                .filename("thumb1.jpg")
                .filePath("https://cdn.example.com/files/thumb1.jpg")
                .fileSize(1024L)
                .mediaType(MediaType.IMAGE) // 필수 설정
                .createdBy(user.getId()) // UserBaseEntity 필드
                .lastModifiedBy(user.getId())
                .build();
        em.persist(file1);
    }

    @Test
    @DisplayName("성능 및 N+1 검증: 리스트 조회 시 User와 Thumbnail을 한 번의 쿼리로(Fetch Join) 가져와야 한다.")
    void searchPostFeed_Performance_Check() {
        // given: 게시글 10개 생성
        for (int i = 1; i <= 10; i++) {
            // @OneToOne 관계로 인해 각 Post는 고유한 File을 가져야 함
            // s3ObjectKey는 unique 제약이 있으므로 고유한 값 사용 (100+i로 구분)
            File thumbnail = null;
            if (i % 2 != 0) { // 홀수는 썸네일 O
                thumbnail = File.builder()
                        .s3ObjectKey("files/thumb" + (100 + i) + ".jpg")
                        .filename("thumb" + i + ".jpg")
                        .filePath("https://cdn.example.com/files/thumb" + i + ".jpg")
                        .fileSize(1024L)
                        .mediaType(MediaType.IMAGE)
                        .createdBy(user.getId())
                        .lastModifiedBy(user.getId())
                        .build();
                em.persist(thumbnail);
            }
            
            Post post = Post.builder()
                    .title("Post " + i)
                    .content("Content " + i)
                    .category(Category.IDOL)
                    .user(user) // [중요] QueryDSL join(post.user)를 위해 연관관계 설정 필수
                    .thumbnailFile(thumbnail) // 홀수는 썸네일 O, 짝수는 X
                    .viewCount(0L)
                    .likeCount(0L)
                    .hotScore(0.0)
                    .createdBy(user.getId()) // UserBaseEntity 필드 - 테스트 환경에서는 수동 설정 필요
                    .lastModifiedBy(user.getId())
                    .build();
            em.persist(post);
        }

        // [핵심] 영속성 컨텍스트 초기화 -> DB에서 쿼리로 직접 가져오도록 강제
        em.flush();
        em.clear();

        // when
        // 정렬: createdAt DESC (최신순)
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.Direction.DESC, "createdAt");
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(10);

        // [데이터 정합성 및 N+1 검증]
        // 최신순이므로 10번째 생성된 "Post 10"이 리스트의 0번째(맨 위)에 위치함

        // 1. 썸네일 없는 게시글 (Post 10 - 짝수)
        PostDto.FeedResponse firstPost = result.getContent().get(0);
        assertThat(firstPost.getTitle()).isEqualTo("Post 10");
        assertThat(firstPost.getThumbnailUrl()).isNull();
        assertThat(firstPost.getWriterNickname()).isEqualTo("TestNick"); // User Join이 잘 되었는지 확인

        // 2. 썸네일 있는 게시글 (Post 9 - 홀수)
        PostDto.FeedResponse secondPost = result.getContent().get(1);
        assertThat(secondPost.getTitle()).isEqualTo("Post 9");
        assertThat(secondPost.getThumbnailUrl()).isNotNull();
        assertThat(secondPost.getThumbnailUrl()).contains("thumb9.jpg");
    }

    @Test
    @DisplayName("필터링: 지정한 카테고리의 게시글만 조회되어야 한다.")
    void searchPostFeed_CategoryFilter() {
        // given
        em.persist(Post.builder().title("Target").category(Category.IDOL).content("C").user(user).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("Other").category(Category.ACTOR).content("C").user(user).createdBy(user.getId()).lastModifiedBy(user.getId()).build());

        em.flush();
        em.clear();

        // when
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, PageRequest.of(0, 10));

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Target");
        assertThat(result.getContent().get(0).getCategory()).isEqualTo(Category.IDOL);
    }

    @Test
    @DisplayName("정렬: HotScore 높은 순으로 정렬되어야 한다.")
    void searchPostFeed_Sort_HotScore() {
        // given
        em.persist(Post.builder().title("Low Score").category(Category.IDOL).content("C").user(user).hotScore(10.0).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("High Score").category(Category.IDOL).content("C").user(user).hotScore(100.0).createdBy(user.getId()).lastModifiedBy(user.getId()).build());

        em.flush();
        em.clear();

        // when (HotScore DESC)
        PageRequest req = PageRequest.of(0, 10, Sort.Direction.DESC, "hotScore");
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, req);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("High Score"); // 100점
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Low Score");  // 10점
    }

    @Test
    @DisplayName("동적 정렬: 썸네일 유무와 관계없이 DTO 변환이 안전하게 수행되어야 한다.")
    void searchPostFeed_Safety_Check() {
        // given
        // 썸네일은 있지만 User가 Lazy Loading 될 때 문제가 없는지 등 복합 확인
        Post post = Post.builder()
                .title("Safe Test")
                .category(Category.IDOL)
                .content("Content")
                .user(user)
                .thumbnailFile(file1)
                .createdBy(user.getId())
                .lastModifiedBy(user.getId())
                .build();
        em.persist(post);

        em.flush();
        em.clear();

        // when
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(null, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getThumbnailUrl()).contains("thumb1.jpg");
    }

    @Test
    @DisplayName("✅ N+1 방지 완벽 검증: 쿼리 개수가 정확히 3개인지 확인한다")
    void verifyNPlusOneWithQueryCount() {
        // Given
        int dataSize = 20;
        bulkInsertPosts(dataSize, Category.IDOL); // 20개 삽입
        em.clear(); // 1차 캐시 초기화 (DB 조회 강제)

        // Hibernate Statistics 준비
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true); // 통계 수집 활성화
        statistics.clear(); // 기존 통계 초기화 (setUp 과정의 쿼리 카운트 제거)

        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

        // When
        log.info("🚀 쿼리 실행 시작");
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, pageable);
        log.info("🚀 쿼리 실행 종료");

        // Then 1: 데이터 검증
        assertThat(result.getContent()).hasSize(10);

        // Then 2: 쿼리 개수 검증 (핵심)
        long queryCount = statistics.getPrepareStatementCount();
        log.info("실행된 SQL 개수: {}", queryCount);

        // 예상되는 쿼리:
        // 1. ID 조회 (Covering Index)
        // 2. Content 조회 (IN 절 + Fetch Join)
        // 3. Count 조회
        // 총 3개여야 함. (N+1 발생 시 23개 이상)
        assertThat(queryCount).isEqualTo(3)
                .as("쿼리 개수가 정확히 3개여야 합니다. (ID 조회, Content 조회, Count 조회)");
    }

    @Test
    @DisplayName("🚀 대용량 데이터 조회 성능 테스트 (Deep Pagination)")
    void testDeepPaginationPerformance() {
        // Given: 10,000건 데이터 삽입
        int totalCount = 10_000;
        log.info("🚀 데이터 {}건 Bulk Insert 시작...", totalCount);

        StopWatch insertSw = new StopWatch();
        insertSw.start();
        bulkInsertPosts(totalCount, Category.IDOL);
        insertSw.stop();
        log.info("✅ Bulk Insert 완료: {} ms", insertSw.getTotalTimeMillis());

        em.clear(); // 영속성 컨텍스트 비우기 (캐시 영향 제거)

        // When: 끝부분 페이지 조회 (Deep Pagination)
        // 10,000건 중 9,990번째부터 10개 조회
        int pageNumber = (totalCount / 10) - 1;
        Pageable pageable = PageRequest.of(pageNumber, 10, Sort.by("createdAt").descending());

        log.info("🚀 Deep Pagination 조회 시작 (Page: {})", pageNumber);

        StopWatch querySw = new StopWatch();
        querySw.start();
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, pageable);
        querySw.stop();

        log.info("✅ 조회 완료: {} ms", querySw.getTotalTimeMillis());

        // Then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(totalCount);

        // 성능 검증 (예: 1초 이내) - Two-Step Query 덕분에 매우 빠름
        assertThat(querySw.getTotalTimeMillis()).isLessThan(1000)
                .as("Deep Pagination 조회가 1초 이내에 완료되어야 합니다.");
    }

    @Test
    @DisplayName("다양한 정렬 조합: 여러 정렬 필드 조합 테스트")
    void searchPostFeed_MultipleSortFields() {
        // given
        em.persist(Post.builder().title("Post A").category(Category.IDOL).content("C").user(user)
                .viewCount(100L).likeCount(50L).hotScore(50.0).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("Post B").category(Category.IDOL).content("C").user(user)
                .viewCount(100L).likeCount(50L).hotScore(100.0).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("Post C").category(Category.IDOL).content("C").user(user)
                .viewCount(200L).likeCount(50L).hotScore(50.0).createdBy(user.getId()).lastModifiedBy(user.getId()).build());

        em.flush();
        em.clear();

        // when: viewCount DESC, hotScore DESC 조합
        Sort sort = Sort.by(Sort.Direction.DESC, "viewCount")
                .and(Sort.by(Sort.Direction.DESC, "hotScore"));
        PageRequest pageRequest = PageRequest.of(0, 10, sort);
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(3);
        // viewCount가 높은 Post C가 첫 번째
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Post C");
        // viewCount가 같으면 hotScore가 높은 Post B가 두 번째
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Post B");
        assertThat(result.getContent().get(2).getTitle()).isEqualTo("Post A");
    }

    @Test
    @DisplayName("기본 정렬: 정렬 필드가 없는 경우 createdAt DESC로 정렬되어야 한다.")
    void searchPostFeed_DefaultSort() {
        // given: Post 엔티티는 UserBaseEntity를 상속하므로 createdAt이 자동 설정됨
        // ID가 큰 것이 나중에 생성된 것이므로 ID 역순으로 정렬되는지 확인
        
        Post post1 = Post.builder().title("Post 1").category(Category.IDOL).content("C").user(user)
                .createdBy(user.getId()).lastModifiedBy(user.getId()).build();
        Post post2 = Post.builder().title("Post 2").category(Category.IDOL).content("C").user(user)
                .createdBy(user.getId()).lastModifiedBy(user.getId()).build();
        
        em.persist(post1);
        em.flush(); // post1의 ID 확보
        em.persist(post2);
        em.flush(); // post2의 ID 확보 (post1보다 큰 ID)
        
        em.clear();

        // when: 정렬 필드 없음 (빈 Sort)
        PageRequest pageRequest = PageRequest.of(0, 10); // Sort 없음
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, pageRequest);

        // then: 기본 정렬(createdAt DESC)이 적용되어야 함
        assertThat(result.getContent()).hasSize(2);
        // 나중에 생성된 Post 2가 첫 번째 (최신순)
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Post 2");
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Post 1");
    }

    @Test
    @DisplayName("정렬: likeCount 높은 순으로 정렬되어야 한다.")
    void searchPostFeed_Sort_LikeCount() {
        // given
        em.persist(Post.builder().title("Low Likes").category(Category.IDOL).content("C").user(user)
                .likeCount(10L).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("High Likes").category(Category.IDOL).content("C").user(user)
                .likeCount(100L).createdBy(user.getId()).lastModifiedBy(user.getId()).build());

        em.flush();
        em.clear();

        // when (likeCount DESC)
        PageRequest req = PageRequest.of(0, 10, Sort.Direction.DESC, "likeCount");
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, req);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("High Likes"); // 100개
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Low Likes");  // 10개
    }

    @Test
    @DisplayName("정렬: viewCount 높은 순으로 정렬되어야 한다.")
    void searchPostFeed_Sort_ViewCount() {
        // given
        em.persist(Post.builder().title("Low Views").category(Category.IDOL).content("C").user(user)
                .viewCount(50L).createdBy(user.getId()).lastModifiedBy(user.getId()).build());
        em.persist(Post.builder().title("High Views").category(Category.IDOL).content("C").user(user)
                .viewCount(500L).createdBy(user.getId()).lastModifiedBy(user.getId()).build());

        em.flush();
        em.clear();

        // when (viewCount DESC)
        PageRequest req = PageRequest.of(0, 10, Sort.Direction.DESC, "viewCount");
        Page<PostDto.FeedResponse> result = postQueryRepository.searchPostFeed(Category.IDOL, req);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("High Views"); // 500개
        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Low Views");  // 50개
    }

    /**
     * JDBC Batch Insert를 이용한 고속 데이터 삽입
     */
    private void bulkInsertPosts(int count, Category category) {
        String sql = "INSERT INTO post (title, content, category, user_id, view_count, like_count, hot_score, created_at, last_modified_at, created_by, last_modified_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 배치 사이즈 설정
        int batchSize = 1000;

        List<Object[]> batchArgs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDateTime now = LocalDateTime.now().minusMinutes(i); // 정렬 테스트를 위해 시간 차등
            batchArgs.add(new Object[]{
                    "Post " + i,
                    "Content " + i,
                    category.name(),
                    user.getId(),
                    0L,
                    0L,
                    0.0,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    user.getId(),
                    user.getId()
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