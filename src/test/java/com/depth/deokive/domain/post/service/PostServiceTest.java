package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.config.aop.ExecutionTimeAspect;
import com.depth.deokive.system.security.model.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PostService.class, ExecutionTimeAspect.class}) // 서비스 빈 등록
@EnableAspectJAutoProxy
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true", // 쿼리 카운팅을 위해 통계 활성화
        "logging.level.org.hibernate.SQL=DEBUG" // 실행되는 쿼리 로그 확인
})
class PostServiceTest {

    private static final Logger log = LoggerFactory.getLogger(PostServiceTest.class);

    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private PostFileMapRepository postFileMapRepository;
    @Autowired private EntityManager entityManager;

    private User testUser;
    private UserPrincipal userPrincipal;
    private List<File> testFiles;

    @BeforeEach
    void setUp() {
        // 1. 테스트 유저 생성
        testUser = userRepository.save(User.builder()
                .email("test@test.com")
                .nickname("Tester")
                .username("testUser")
                .password("password")
                .role(Role.USER)
                .build());

        userPrincipal = UserPrincipal.from(testUser);

        // 2. 테스트용 파일 3개 생성
        testFiles = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            File file = fileRepository.save(File.builder()
                    .s3ObjectKey("test/key/" + i)
                    .filename("image" + i + ".jpg")
                    .filePath("http://cdn.com/image" + i + ".jpg")
                    .fileSize(1024L)
                    .mediaType(MediaType.IMAGE)
                    .build());
            testFiles.add(file);
        }

        // 쿼리 카운트 초기화를 위해 영속성 컨텍스트 비우기
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("게시글 생성 및 파일 연결 테스트")
    void createPost_Success() {
        // given
        List<PostDto.AttachedFileRequest> attachedFiles = testFiles.stream()
                .map(f -> new PostDto.AttachedFileRequest(f.getId(), MediaRole.CONTENT, 1))
                .collect(Collectors.toList());

        PostDto.Request request = PostDto.Request.builder()
                .title("Test Title")
                .content("Test Content")
                .category(Category.IDOL)
                .files(attachedFiles)
                .build();

        // when
        PostDto.Response response = postService.createPost(userPrincipal, request);

        // then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Test Title");
        assertThat(response.getFiles()).hasSize(3); // 파일 3개가 잘 연결되었는지

        // DB 확인
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(response.getId());
        assertThat(maps).hasSize(3);
        assertThat(maps.get(0).getFile().getId()).isEqualTo(testFiles.getFirst().getId());
    }

    @Test
    @DisplayName("게시글 수정: 기존 파일 삭제 후 재생성 (Bulk Delete 검증)")
    void updatePost_Success() {
        // given (기존 게시글 생성)
        Post post = postRepository.save(Post.builder()
                .title("Old Title")
                .content("Old Content")
                .category(Category.IDOL)
                .user(testUser)
                .build());

        // 기존에 파일 1개 연결해둠
        postFileMapRepository.save(
            PostFileMap.builder()
                .post(post)
                .file(testFiles.getFirst())
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build()
        );

        entityManager.flush();
        entityManager.clear();

        // 수정 요청: 파일 구성을 변경 (1개 -> 3개)
        List<PostDto.AttachedFileRequest> newFiles = testFiles.stream()
                .map(f -> new PostDto.AttachedFileRequest(f.getId(), MediaRole.CONTENT, 1))
                .collect(Collectors.toList());

        PostDto.Request updateRequest = PostDto.Request.builder()
                .title("New Title")
                .content("New Content")
                .category(Category.ACTOR)
                .files(newFiles)
                .build();

        // when
        postService.updatePost(userPrincipal, post.getId(), updateRequest);

        // then
        Post updatedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updatedPost.getTitle()).isEqualTo("New Title");

        // 파일 매핑이 3개로 늘어났는지 확인
        List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
        assertThat(maps).hasSize(3);
    }

    @Test
    @DisplayName("성능 테스트: 파일 100개가 연결된 게시글 삭제 시 쿼리 횟수 검증")
    void deletePost_BulkQuery_Performance() {
        // given: 파일 100개 생성 및 연결
        Post post = postRepository.save(Post.builder()
                .title("Bulk Delete Test")
                .content("Content")
                .category(Category.IDOL)
                .user(testUser)
                .build());

        List<File> bulkFiles = new ArrayList<>();
        // 100개의 더미 파일 생성 (Batch Insert가 아니므로 여기서 시간이 좀 걸림)
        for (int i = 0; i < 100; i++) {
            bulkFiles.add(File.builder()
                    .s3ObjectKey("bulk/" + i)
                    .filename("file" + i)
                    .filePath("url")
                    .fileSize(100L)
                    .mediaType(MediaType.IMAGE)
                    .build());
        }
        fileRepository.saveAll(bulkFiles);

        List<PostFileMap> maps = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            maps.add(PostFileMap.builder()
                    .post(post)
                    .file(bulkFiles.get(i))
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(i)
                    .build());
        }
        postFileMapRepository.saveAll(maps);

        entityManager.flush();
        entityManager.clear(); // 1차 캐시 비우기 (순수 쿼리 성능 측정용)

        // 쿼리 통계 준비
        Session session = entityManager.unwrap(Session.class);
        Statistics statistics = session.getSessionFactory().getStatistics();
        statistics.clear(); // 통계 초기화

        // when: 시간 측정 시작
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        postService.deletePost(userPrincipal, post.getId());
        entityManager.flush(); // 쓰기 지연된 DELETE 쿼리를 강제로 DB에 보냄

        stopWatch.stop();

        // then
        log.info("🔥 삭제 소요 시간: {} ms", stopWatch.getTotalTimeMillis());
        log.info("🔥 실행된 쿼리 수 (Delete): {}", statistics.getEntityDeleteCount());
        // 주의: statistics.getEntityDeleteCount()는 JPA를 통한 삭제만 카운트될 수 있음.
        // 정확한 쿼리 문자열 실행 횟수는 prepareStatementCount를 봅니다.
        long queryCount = statistics.getPrepareStatementCount();
        log.info("🔥 실행된 SQL 문 개수: {}", queryCount);

        // 검증
        // 1. SELECT (게시글 조회)
        // 2. DELETE (파일 맵 벌크 삭제)
        // 3. DELETE (게시글 삭제 - flush로 인해 실행됨)
        // 총 3개의 쿼리가 나가야 정상입니다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

        // 검증 1: 게시글이 삭제되었는가?
        assertThat(postRepository.findById(post.getId())).isEmpty();
        assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId())).isEmpty();

        // 검증 2: N+1 문제가 발생하지 않았는가?
        // 예상 쿼리:
        // 1. Post 조회 (SELECT)
        // 2. User 조회 (Lazy Loading으로 인한 SELECT, validateOwner 시점)
        // 3. PostFileMap Bulk Delete (DELETE FROM map WHERE post_id=?) -> 1방
        // 4. Post Delete (DELETE FROM post WHERE id=?) -> 1방
        // 총 DELETE 쿼리는 2방이어야 함. (만약 Cascade였다면 101방)

        // 쿼리 카운트는 환경에 따라 SELECT 횟수가 다를 수 있으므로 DELETE 쿼리만 논리적으로 검증하거나
        // 시간이 매우 짧게(수 ms) 걸리는 것으로 간접 검증합니다.
        assertThat(stopWatch.getTotalTimeMillis()).isLessThan(500); // 100개 삭제인데 0.5초 미만이면 Bulk 성공
    }
}