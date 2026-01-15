package com.depth.deokive.common.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveStatsRepository;
import com.depth.deokive.domain.post.repository.PostStatsRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@DisplayName("ViewCount(조회수) 시스템 API 통합 테스트")
class ViewCountApiTest extends ApiTestSupport {

    // 검증(Assertion)을 위한 Repository 조회만 허용
    @Autowired private PostStatsRepository postStatsRepository;
    @Autowired private ArchiveStatsRepository archiveStatsRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    // 각 테스트 메서드마다 고유한 데이터가 생성됨
    private String ownerToken;
    private Long targetPostId;
    private Long targetArchiveId;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 '새로운' 유저와 '새로운' 게시글/아카이브를 만든다.
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Redis 초기화 (테스트 간 데이터 간섭 방지)
        Set<String> keys = redisTemplate.keys("view:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 2. 작성자 생성 (API를 통한 회원가입 및 로그인)
        // NOTE: 닉네임은 2~10자 제한이므로 UUID 8자리만 사용
        Map<String, Object> userInfo = AuthSteps.registerAndLogin(
                "owner-" + uniqueSuffix + "@test.com",
                "O" + uniqueSuffix, // 최대 9자 (O + 8자 UUID)
                "Password123!"
        );
        ownerToken = (String) userInfo.get("accessToken");

        // 3. 게시글 및 아카이브 생성 (API를 통한 E2E 생성)
        targetPostId = PostSteps.create(ownerToken, "Post-" + uniqueSuffix, "IDOL", null);
        targetArchiveId = ArchiveSteps.create(ownerToken, "Archive-" + uniqueSuffix, "PUBLIC");
    }

    // DB에서 현재 조회수 가져오기 (검증용)
    private long getCurrentPostViewCount(Long postId) {
        return postStatsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("PostStats not found for postId: " + postId))
                .getViewCount();
    }

    private long getCurrentArchiveViewCount(Long archiveId) {
        return archiveStatsRepository.findById(archiveId)
                .orElseThrow(() -> new RuntimeException("ArchiveStats not found for archiveId: " + archiveId))
                .getViewCount();
    }

    /**
     * SystemSchedulerController API를 호출하여 Redis 데이터를 DB로 동기화
     */
    private void triggerSchedulerSync() {
        given().contentType(ContentType.JSON)
                .post("/api/system/test/scheduler/view-count")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body(equalTo("🟢 View Count Sync Completed! (Redis -> DB)"));
    }

    // ========================================================================================
    // [Category 1]. Basic View Count
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 기본 조회수 로직 검증")
    class BasicView {

        @Test
        @DisplayName("SCENE 1: 게시글 조회 -> 1 증가 확인 (Redis -> DB 동기화)")
        void increasePostView() {
            long initialCount = getCurrentPostViewCount(targetPostId);

            // 1. API 호출 (회원)
            given().cookie("ATK", ownerToken)
                    .get("/api/v1/posts/{id}", targetPostId)
                    .then().statusCode(200);

            // 2. 스케줄러 트리거
            triggerSchedulerSync();

            // 3. 검증 (비동기 반영 대기)
            await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(getCurrentPostViewCount(targetPostId)).isEqualTo(initialCount + 1)
            );
        }

        @Test
        @DisplayName("SCENE 2: 아카이브 조회 -> 1 증가 확인")
        void increaseArchiveView() {
            long initialCount = getCurrentArchiveViewCount(targetArchiveId);

            given().cookie("ATK", ownerToken)
                    .get("/api/v1/archives/{id}", targetArchiveId)
                    .then().statusCode(200);

            triggerSchedulerSync();

            await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(getCurrentArchiveViewCount(targetArchiveId)).isEqualTo(initialCount + 1)
            );
        }

        @Test
        @DisplayName("SCENE 3: 비회원 조회 -> IP 기반 1 증가 확인")
        void anonymousView() {
            long initialCount = getCurrentPostViewCount(targetPostId);

            // 토큰 없이 호출
            given().get("/api/v1/posts/{id}", targetPostId)
                    .then().statusCode(200);

            triggerSchedulerSync();

            await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(getCurrentPostViewCount(targetPostId)).isEqualTo(initialCount + 1)
            );
        }

        @Test
        @DisplayName("SCENE 4: 어뷰징 방지 - 동일 유저 연속 조회 시 1회만 증가")
        void abusePrevention() {
            long initialCount = getCurrentPostViewCount(targetPostId);

            // 연속 5회 호출
            for (int i = 0; i < 5; i++) {
                given().cookie("ATK", ownerToken)
                        .get("/api/v1/posts/{id}", targetPostId)
                        .then().statusCode(200);
            }

            triggerSchedulerSync();

            await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(getCurrentPostViewCount(targetPostId)).isEqualTo(initialCount + 1)
            );
        }
    }

    // ========================================================================================
    // [Category 2]. Concurrency & High Volume
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 동시성 및 대용량 트래픽 검증")
    class Concurrency {

        @Test
        @DisplayName("SCENE 5: [Post] 50명의 서로 다른 유저(IP 조작)가 동시에 조회 -> 정확히 50 증가")
        void concurrent_PostView() {
            long initialCount = getCurrentPostViewCount(targetPostId);
            int threadCount = 50;

            ExecutorService executorService = Executors.newFixedThreadPool(20);

            // 50개의 비동기 요청 (IP를 조작하여 서로 다른 비회원인 것처럼 위장)
            java.util.List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            String fakeIp = "192.168.0." + (i + 1);

                            given().header("X-Forwarded-For", fakeIp)
                                    .get("/api/v1/posts/{id}", targetPostId)
                                    .then().statusCode(200);

                        } catch (Exception e) {
                            log.error("Request failed: {}", e.getMessage());
                        }
                    }, executorService))
                    .toList();

            // 모든 요청 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 동기화
            triggerSchedulerSync();

            // 검증 (비동기 반영 대기)
            await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
                long finalCount = getCurrentPostViewCount(targetPostId);
                log.info("Post View - Initial: {}, Final: {}, Threads: {}", initialCount, finalCount, threadCount);
                assertThat(finalCount).isEqualTo(initialCount + threadCount);
            });
        }

        @Test
        @DisplayName("SCENE 6: [Archive] 30명의 유저가 동시에 조회 -> 정확히 30 증가")
        void concurrent_ArchiveView() {
            long initialCount = getCurrentArchiveViewCount(targetArchiveId);
            int threadCount = 30;

            ExecutorService executorService = Executors.newFixedThreadPool(15);

            java.util.List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        String fakeIp = "10.0.0." + (i + 1);
                        given().header("X-Forwarded-For", fakeIp)
                                .get("/api/v1/archives/{id}", targetArchiveId)
                                .then().statusCode(200);
                    }, executorService))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            triggerSchedulerSync();

            await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
                long finalCount = getCurrentArchiveViewCount(targetArchiveId);
                assertThat(finalCount).isEqualTo(initialCount + threadCount);
            });
        }

        @Test
        @DisplayName("SCENE 7: [Write-Back 정합성] 스케줄러 동기화(DB 반영) 도중에 대량의 조회 요청이 들어와도 누락이 없어야 한다.")
        void concurrent_WriteBack_During_Traffic() {
            // Given
            long initialDbCount = getCurrentPostViewCount(targetPostId);
            long prePopulatedRedisCount = 3000L; // 스케줄러가 가져갈 물량
            int additionalTrafficCount = 500;   // 동기화 도중 치고 들어올 물량

            // 1. Redis에 미리 대량의 조회수 적립 (스케줄러가 처리할 시간을 벌기 위함)
            // NOTE: 테스트 시나리오를 위한 특수한 경우로, API를 통한 조회수 증가로는 충분한 시간을 확보하기 어려움
            // 실제 운영 환경에서는 자연스럽게 쌓인 조회수를 스케줄러가 처리하는 상황을 시뮬레이션
            // RedisViewService는 StringRedisTemplate을 사용하므로 String으로 변환하여 저장
            String key = "view:count:post:" + targetPostId;
            redisTemplate.opsForValue().set(key, String.valueOf(prePopulatedRedisCount));

            ExecutorService executorService = Executors.newFixedThreadPool(16);

            // When
            // Task A: 스케줄러 강제 실행 (Sync) - 별도 스레드에서 실행
            CompletableFuture<Void> syncTask = CompletableFuture.runAsync(ViewCountApiTest.this::triggerSchedulerSync, executorService);

            // Task B: 스케줄러가 도는 동안 500건의 추가 조회 요청 폭격
            java.util.List<CompletableFuture<Void>> trafficTasks = IntStream.range(0, additionalTrafficCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            // 스케줄러가 시작될 틈을 아주 살짝 줌 (현실적인 시나리오)
                            Thread.sleep(5);

                            // 어뷰징 방지 우회 (IP 조작)
                            String fakeIp = "172.10.0." + (i + 1);
                            given().header("X-Forwarded-For", fakeIp)
                                    .get("/api/v1/posts/{id}", targetPostId)
                                    .then().statusCode(200);
                        } catch (Exception e) {
                            log.error("Traffic request failed: {}", e.getMessage());
                        }
                    }, executorService))
                    .toList();

            // 모든 작업(스케줄러 + 추가 요청)이 끝날 때까지 대기
            CompletableFuture.allOf(syncTask).join();
            CompletableFuture.allOf(trafficTasks.toArray(new CompletableFuture[0])).join();

            // Then
            // 검증 공식: (최종 DB 값) + (Redis 잔여 값) == (초기 DB 값) + (미리 넣은 값) + (추가 요청 값)
            await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
                long finalDbCount = getCurrentPostViewCount(targetPostId);
                Object redisVal = redisTemplate.opsForValue().get(key);
                long finalRedisCount = (redisVal != null) ? Long.parseLong(redisVal.toString()) : 0;

                long totalExpected = initialDbCount + prePopulatedRedisCount + additionalTrafficCount;
                long totalActual = finalDbCount + finalRedisCount;

                log.info("=== Write-Back Consistency Check ===");
                log.info("Initial DB: {}", initialDbCount);
                log.info("Pre-populated Redis: {}", prePopulatedRedisCount);
                log.info("Additional Traffic: {}", additionalTrafficCount);
                log.info("Final DB: {}", finalDbCount);
                log.info("Final Redis (Remaining): {}", finalRedisCount);
                log.info("Total Actual (DB+Redis): {}", totalActual);

                assertThat(totalActual).isEqualTo(totalExpected);
            });
        }
    }

    // ========================================================================================
    // Internal Helper Steps (API를 통한 E2E 데이터 생성)
    // ========================================================================================

    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
            try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}

            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            String code = getVerificationCode(email, mailhogUrl);
            given().contentType(ContentType.JSON).body(Map.<String, Object>of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify").then().statusCode(200);

            int userId = given().contentType(ContentType.JSON)
                    .body(Map.<String, Object>of("email", email, "nickname", nickname, "password", password))
                    .post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");

            Response loginRes = given().contentType(ContentType.JSON)
                    .body(Map.<String, Object>of("email", email, "password", password))
                    .post("/api/v1/auth/login");

            return Map.<String, Object>of("accessToken", loginRes.getCookie("ATK"), "userId", userId);
        }

        private static String getVerificationCode(String email, String mailhogUrl) {
            for (int i = 0; i < 20; i++) {
                try {
                    Response res = RestAssured.given().get(mailhogUrl);
                    List<Map<String, Object>> messages = res.jsonPath().getList("items");
                    if (messages != null) {
                        for (Map<String, Object> msg : messages) {
                            if (msg.toString().contains(email)) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> content = (Map<String, Object>) msg.get("Content");
                                Matcher m = Pattern.compile("\\d{6}").matcher(content.get("Body").toString());
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
        static Long create(String token, String title, String category, Long fileId) {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("title", title);
            body.put("content", "Content");
            body.put("category", category);

            if (fileId != null) {
                body.put("files", List.of(Map.<String, Object>of("fileId", fileId, "mediaRole", "PREVIEW", "sequence", 0)));
            } else {
                body.put("files", List.of());
            }

            return given().cookie("ATK", token).contentType(ContentType.JSON).body(body)
                    .post("/api/v1/posts").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class ArchiveSteps {
        static Long create(String token, String title, String visibility) {
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.<String, Object>of("title", title, "visibility", visibility))
                    .post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }
}