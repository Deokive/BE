package com.depth.deokive.domain.post.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostLikeRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.scheduler.LikeCountScheduler;
import com.depth.deokive.system.security.jwt.dto.JwtDto;
import com.depth.deokive.system.security.jwt.service.TokenService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@DisplayName("Post 좋아요 순수 E2E 테스트 (No TearDown)")
class PostLikeApiTest extends ApiTestSupport {

    // 검증(Assertion)을 위한 Repository 조회만 허용
    @Autowired private PostLikeRepository postLikeRepository;

    // 테스트 데이터 셋업을 위한 서비스 (API 호출로 대체 가능하지만 속도를 위해 허용)
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private TokenService tokenService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    // 각 테스트 메서드마다 고유한 데이터가 생성됨
    private User owner;
    private String ownerToken;
    private Long targetPostId;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 '새로운' 유저와 '새로운' 게시글을 만든다.
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. 작성자 생성
        owner = createDirectUser("owner-" + uniqueSuffix + "@test.com", "Owner-" + uniqueSuffix);
        ownerToken = generateToken(owner);

        // 2. 게시글 생성
        targetPostId = createDirectPost(owner.getId(), "Post-" + uniqueSuffix).getId();
    }

    @Nested
    @DisplayName("기능 시나리오")
    class FunctionalTest {

        @Test
        @DisplayName("SCENE 1. 좋아요 등록 및 취소 (Toggle)")
        void likeToggle() {
            // Given: 새로운 유저 생성
            User userA = createDirectUser("userA-" + UUID.randomUUID() + "@test.com", "UserA");
            String tokenA = generateToken(userA);

            // [Action 1] 좋아요 등록
            given().cookie("ATK", tokenA)
                    .post("/api/v1/posts/{postId}/like", targetPostId)
                    .then()
                    .statusCode(200)
                    .body("isLiked", equalTo(true))
                    .body("likeCount", equalTo(1));

            // [Verify 1] DB 최종 적재 확인
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(postLikeRepository.existsByPostIdAndUserId(targetPostId, userA.getId())).isTrue()
            );

            // [Action 2] 좋아요 취소
            given().cookie("ATK", tokenA)
                    .post("/api/v1/posts/{postId}/like", targetPostId)
                    .then()
                    .statusCode(200)
                    .body("isLiked", equalTo(false))
                    .body("likeCount", equalTo(0));

            // [Verify 2] DB 삭제 확인
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(postLikeRepository.existsByPostIdAndUserId(targetPostId, userA.getId())).isFalse()
            );
        }
    }

    @Nested
    @DisplayName("동시성 시나리오")
    class ConcurrencyTest {

        @Test
        @DisplayName("SCENE 2. 100명 동시 좋아요 (Race Condition)")
        void concurrentLikes() throws InterruptedException {
            int threadCount = 100;
            ExecutorService executorService = Executors.newFixedThreadPool(32);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // 1. 100명의 유니크한 유저 생성
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String suffix = UUID.randomUUID().toString().substring(0, 5);
                User u = createDirectUser("con-" + suffix + "@t.com", "Nick" + suffix);
                tokens.add(generateToken(u));
            }

            // 2. 동시 요청
            for (String token : tokens) {
                executorService.submit(() -> {
                    try {
                        given().cookie("ATK", token)
                                .post("/api/v1/posts/{postId}/like", targetPostId)
                                .then().statusCode(200);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            // 3. 검증 (Redis API 조회)
            given().cookie("ATK", ownerToken)
                    .get("/api/v1/posts/{postId}", targetPostId)
                    .then()
                    .body("likeCount", equalTo(threadCount));

            // 4. 검증 (DB 비동기 반영)
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                long count = postLikeRepository.countByPostId(targetPostId);
                assertThat(count).isEqualTo(threadCount);
            });
        }

        @Test
        @DisplayName("SCENE 3. 따닥 요청 (중복 방지)")
        void doubleClick() throws InterruptedException {
            int clickCount = 5;
            ExecutorService executorService = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(clickCount);

            User user = createDirectUser("clicker-" + UUID.randomUUID() + "@t.com", "Clicker");
            String token = generateToken(user);

            for (int i = 0; i < clickCount; i++) {
                executorService.submit(() -> {
                    try {
                        given().cookie("ATK", token)
                                .post("/api/v1/posts/{postId}/like", targetPostId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            // 검증: 홀수 번 요청 -> 최종 상태 ON (1)
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean exists = postLikeRepository.existsByPostIdAndUserId(targetPostId, user.getId());
                assertThat(exists).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("예외 시나리오")
    class EdgeCaseTest {
        @Test
        @DisplayName("SCENE 4. 존재하지 않는 게시글 (404)")
        void notFound() {
            given().cookie("ATK", ownerToken)
                    .post("/api/v1/posts/{postId}/like", 999999L)
                    .then()
                    .statusCode(404)
                    .body("error", equalTo(ErrorCode.POST_NOT_FOUND.name()));
        }

        @Test
        @DisplayName("SCENE 5. 로그인 안 함 (401)")
        void unauthorized() {
            given().post("/api/v1/posts/{postId}/like", targetPostId)
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("SCENE 6. [Delete Cleanup] 게시글 삭제 시 Redis의 좋아요 데이터도 즉시 삭제되어야 한다.")
        void deletePost_Should_Evict_Redis() {
            // Given: 좋아요 1개 생성 (Redis에 데이터 적재)
            given().cookie("ATK", ownerToken)
                    .post("/api/v1/posts/{postId}/like", targetPostId)
                    .then()
                    .statusCode(200)
                    .body("isLiked", equalTo(true))
                    .body("likeCount", equalTo(1));

            // RabbitMQ 메시지 처리가 완료될 때까지 대기
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(postLikeRepository.existsByPostIdAndUserId(targetPostId, owner.getId())).isTrue()
            );

            // When: 게시글 삭제
            given().cookie("ATK", ownerToken)
                    .delete("/api/v1/posts/{postId}", targetPostId)
                    .then()
                    .statusCode(204);

            // Then: Redis Key가 사라져야 함 (Ghost Key 방지)
            String countKey = "like:post:count:" + targetPostId;
            String setKey = "like:post:users:" + targetPostId;

            assertThat(redisTemplate.hasKey(countKey)).isFalse();
            assertThat(redisTemplate.hasKey(setKey)).isFalse();
        }
    }

    // --- Helpers (DB Direct Setup only for speed) ---
    private User createDirectUser(String email, String nickname) {
        User user = User.builder()
                .email(email)
                .nickname(nickname)
                .username("usr_" + UUID.randomUUID().toString().substring(0,8))
                .password("pw")
                .role(Role.USER)
                .userType(UserType.COMMON)
                .isEmailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private com.depth.deokive.domain.post.entity.Post createDirectPost(Long userId, String title) {
        User user = userRepository.findById(userId).orElseThrow();
        com.depth.deokive.domain.post.entity.Post post = com.depth.deokive.domain.post.entity.Post.builder()
                .user(user)
                .title(title)
                .content("Content")
                .category(Category.IDOL)
                .build();
        return postRepository.save(post);
    }

    private String generateToken(User user) {
        JwtDto.TokenOptionWrapper option = JwtDto.TokenOptionWrapper.of(UserPrincipal.from(user), false);
        return tokenService.issueTokens(option).getAccessToken();
    }
}