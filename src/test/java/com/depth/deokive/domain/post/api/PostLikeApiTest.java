package com.depth.deokive.domain.post.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostLikeRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import com.depth.deokive.system.scheduler.LikeCountScheduler;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@DisplayName("Post 좋아요 동시성 및 기능 통합 테스트 (Redis+MQ)")
class PostLikeApiTest extends ApiTestSupport {

    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostStatsRepository postStatsRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private LikeCountScheduler likeCountScheduler; // 스케줄러 수동 실행용

    private static String tokenOwner;
    private static Long postId;

    @BeforeEach
    void setUp() {
        // [Global Setup] 최초 1회 실행: 작성자 및 게시글 생성
        if (tokenOwner == null) {
            Map<String, Object> owner = AuthSteps.registerAndLogin("owner.like@test.com", "LikeOwner", "Password123!");
            tokenOwner = (String) owner.get("accessToken");

            // 게시글 생성 (파일 없이 간단 생성)
            postId = PostSteps.createPost(tokenOwner, "Like Target Post");
        }

        // 매 테스트마다 좋아요 데이터 초기화 (Redis & DB)
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        postLikeRepository.deleteAll();
        // 주의: postStats는 초기화하지 않음 (게시글 자체는 유지)
    }

    @Nested
    @DisplayName("[Category 1] 좋아요 기능 검증")
    class FunctionalTest {

        @Test
        @DisplayName("SCENE 1. 좋아요 토글 (ON -> OFF)")
        void toggleLike() {
            // 1. 유저 A 생성 & 로그인
            Map<String, Object> userA = AuthSteps.registerAndLogin("liker.a@test.com", "LikerA", "Password123!");
            String tokenA = (String) userA.get("accessToken");
            Long userAId = ((Number) userA.get("userId")).longValue();

            // 2. 좋아요 요청 (ON)
            given().cookie("ATK", tokenA)
                    .post("/api/v1/posts/{postId}/like", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("isLiked", equalTo(true))
                    .body("likeCount", equalTo(1));

            // 3. Redis & DB 검증 (비동기 반영 대기)
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(postLikeRepository.existsByPostIdAndUserId(postId, userAId)).isTrue();
            });

            // 4. 좋아요 취소 요청 (OFF)
            given().cookie("ATK", tokenA)
                    .post("/api/v1/posts/{postId}/like", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("isLiked", equalTo(false))
                    .body("likeCount", equalTo(0));

            // 5. DB 삭제 검증
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(postLikeRepository.existsByPostIdAndUserId(postId, userAId)).isFalse();
            });
        }

        @Test
        @DisplayName("SCENE 2. 게시글 상세 조회 시 isLiked 반영 확인")
        void getPost_WithLikeStatus() {
            // 1. 유저 B 생성 & 로그인
            Map<String, Object> userB = AuthSteps.registerAndLogin("liker.b@test.com", "LikerB", "Password123!");
            String tokenB = (String) userB.get("accessToken");

            // 2. 좋아요 누름
            given().cookie("ATK", tokenB).post("/api/v1/posts/{postId}/like", postId);

            // 3. 상세 조회
            given().cookie("ATK", tokenB)
                    .get("/api/v1/posts/{postId}", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("isLiked", equalTo(true))
                    .body("likeCount", equalTo(1));

            // 4. 다른 유저(Owner)가 조회하면 false 여야 함
            given().cookie("ATK", tokenOwner)
                    .get("/api/v1/posts/{postId}", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("isLiked", equalTo(false))
                    .body("likeCount", equalTo(1)); // 카운트는 1
        }
    }

    @Nested
    @DisplayName("[Category 2] 동시성 및 대용량 트래픽 검증")
    class ConcurrencyTest {

        @Test
        @DisplayName("SCENE 3. 300명 동시 좋아요 -> Redis 즉시 처리 & DB 최종 일관성")
        void concurrentLikes() throws InterruptedException {
            int userCount = 300;
            ExecutorService executorService = Executors.newFixedThreadPool(32);
            CountDownLatch latch = new CountDownLatch(userCount);

            // 1. 300명의 유저 토큰 미리 발급 (로그인 부하 제외)
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                Map<String, Object> user = AuthSteps.registerAndLogin("bulk." + i + "@test.com", "Bulk" + i, "Password123!");
                tokens.add((String) user.get("accessToken"));
            }

            System.out.println("🔥 [Test] 300 Users Ready. Starting Concurrent Requests...");

            // 2. 동시 요청 시작
            long startTime = System.currentTimeMillis();

            for (String token : tokens) {
                executorService.submit(() -> {
                    try {
                        given().cookie("ATK", token)
                                .post("/api/v1/posts/{postId}/like", postId)
                                .then()
                                .statusCode(200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            long endTime = System.currentTimeMillis();
            log.info("⚡ [Test] 300 Requests Finished In : {} ms", endTime - startTime);

            // 3. 검증 1: API 응답 속도 (전체 300개가 2초 내에 처리되어야 함 - 로컬 환경 감안)
            assertThat(endTime - startTime).isLessThan(5000);

            // 4. 검증 2: Redis 카운트 (즉시 반영)
            // PostLikeRedisService의 getCount 로직 검증 (API로 조회)
            given().cookie("ATK", tokenOwner)
                    .get("/api/v1/posts/{postId}", postId)
                    .then()
                    .body("likeCount", equalTo(userCount));

            // 5. 검증 3: DB 비동기 반영 (RabbitMQ) - 최대 10초 대기
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                long dbCount = postLikeRepository.count(); // 해당 테스트 DB는 매번 초기화되므로 전체 count = 해당 post 좋아요 수
                assertThat(dbCount).isEqualTo(userCount);
            });

            // 6. 검증 4: 스케줄러 실행 후 PostStats 반영
            likeCountScheduler.syncPostLikes(); // 수동 트리거

            // PostStats 조회
            long statsCount = postStatsRepository.findById(postId).orElseThrow().getLikeCount();
            assertThat(statsCount).isEqualTo(userCount);
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================

    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
            // MailHog 청소 (선택)
            try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}

            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);

            // 메일 도착 대기 (약간의 지연 필요)
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            String code = getVerificationCode(email, mailhogUrl);

            given().contentType(ContentType.JSON).body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify").then().statusCode(200);

            int userId = given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "nickname", nickname, "password", password))
                    .post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");

            Response loginRes = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                    .post("/api/v1/auth/login");

            return Map.of("accessToken", loginRes.getCookie("ATK"), "userId", userId);
        }

        private static String getVerificationCode(String email, String mailhogUrl) {
            for (int i = 0; i < 20; i++) {
                try {
                    Response res = RestAssured.given().get(mailhogUrl);
                    List<Map<String, Object>> messages = res.jsonPath().getList("items");
                    if (messages != null) {
                        for (Map<String, Object> msg : messages) {
                            if (msg.toString().contains(email)) {
                                Matcher m = Pattern.compile("\\d{6}").matcher(((Map) msg.get("Content")).get("Body").toString());
                                if (m.find()) return m.group();
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("MailHog Fail: " + email);
        }
    }

    static class PostSteps {
        static Long createPost(String token, String title) {
            // 파일 없이 생성하는 API가 있다면 사용, 아니면 파일 업로드 로직 추가 필요
            // 현재 PostController.createPost는 files 리스트를 받음. 빈 리스트 허용 여부에 따라 다름.
            // 여기서는 files: [] (빈 리스트)로 전송 가정
            Map<String, Object> body = Map.of(
                    "title", title,
                    "content", "Test Content",
                    "category", Category.IDOL,
                    "files", List.of()
            );

            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(body)
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(201)
                    .extract().jsonPath().getLong("id");
        }
    }
}