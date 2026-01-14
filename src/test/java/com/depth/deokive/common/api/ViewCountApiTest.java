// package com.depth.deokive.common.api;
//
// import com.depth.deokive.common.test.ApiTestSupport;
// import com.depth.deokive.domain.archive.repository.ArchiveRepository;
// import com.depth.deokive.domain.post.repository.PostRepository;
// import io.restassured.RestAssured;
// import io.restassured.http.ContentType;
// import io.restassured.response.Response;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Nested;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.http.HttpStatus;
//
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.atomic.AtomicInteger;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
// import java.util.stream.IntStream;
//
// import static io.restassured.RestAssured.given;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.hamcrest.Matchers.equalTo;
//
// @DisplayName("ViewCount(조회수) 시스템 API 통합 테스트")
// class ViewCountApiTest extends ApiTestSupport {
//
//     @Autowired private PostRepository postRepository;
//     @Autowired private ArchiveRepository archiveRepository;
//     @Autowired private StringRedisTemplate redisTemplate;
//
//     // 테스트용 데이터
//     private static String tokenUserA;
//     private static Long postAId;
//     private static Long archiveAId;
//
//     @BeforeEach
//     void setUp() {
//         RestAssured.port = port;
//
//         // 1. Redis 초기화 (테스트 간 데이터 간섭 방지)
//         Set<String> keys = redisTemplate.keys("view:*");
//         if (keys != null && !keys.isEmpty()) {
//             redisTemplate.delete(keys);
//         }
//
//         // 2. 데이터 초기화 (최초 1회만 수행)
//         if (tokenUserA == null) {
//             // 내부 Steps 활용하여 유저 및 콘텐츠 생성
//             Map<String, Object> userA = AuthSteps.registerAndLogin("view@test.com", "ViewTester", "Password123!");
//             tokenUserA = (String) userA.get("accessToken");
//
//             postAId = PostSteps.create(tokenUserA, "View Post", "IDOL", null);
//             archiveAId = ArchiveSteps.create(tokenUserA, "View Archive", "PUBLIC");
//         }
//     }
//
//     // DB에서 현재 조회수 가져오기 (검증용)
//     private long getCurrentPostViewCount(Long postId) {
//         return postRepository.findById(postId).orElseThrow().getViewCount();
//     }
//
//     private long getCurrentArchiveViewCount(Long archiveId) {
//         return archiveRepository.findById(archiveId).orElseThrow().getViewCount();
//     }
//
//     /**
//      * SystemSchedulerController API를 호출하여 Redis 데이터를 DB로 동기화
//      */
//     private void triggerSchedulerSync() {
//         given().contentType(ContentType.JSON)
//                 .post("/api/system/test/scheduler/view-count")
//                 .then()
//                 .statusCode(HttpStatus.OK.value())
//                 .body(equalTo("🟢 View Count Sync Completed! (Redis -> DB)"));
//
//         // DB 반영 딜레이(JPA Flush 등) 안전 장치
//         try { Thread.sleep(200); } catch (InterruptedException ignored) {}
//     }
//
//     // ========================================================================================
//     // [Category 1]. Basic View Count
//     // ========================================================================================
//     @Nested
//     @DisplayName("[Category 1] 기본 조회수 로직 검증")
//     class BasicView {
//
//         @Test
//         @DisplayName("SCENE 1: 게시글 조회 -> 1 증가 확인 (Redis -> DB 동기화)")
//         void increasePostView() {
//             long initialCount = getCurrentPostViewCount(postAId);
//
//             // 1. API 호출 (회원)
//             given().cookie("ATK", tokenUserA)
//                     .get("/api/v1/posts/{id}", postAId)
//                     .then().statusCode(200);
//
//             // 2. 스케줄러 트리거
//             triggerSchedulerSync();
//
//             // 3. 검증
//             assertThat(getCurrentPostViewCount(postAId)).isEqualTo(initialCount + 1);
//         }
//
//         @Test
//         @DisplayName("SCENE 2: 아카이브 조회 -> 1 증가 확인")
//         void increaseArchiveView() {
//             long initialCount = getCurrentArchiveViewCount(archiveAId);
//
//             given().cookie("ATK", tokenUserA)
//                     .get("/api/v1/archives/{id}", archiveAId)
//                     .then().statusCode(200);
//
//             triggerSchedulerSync();
//
//             assertThat(getCurrentArchiveViewCount(archiveAId)).isEqualTo(initialCount + 1);
//         }
//
//         @Test
//         @DisplayName("SCENE 3: 비회원 조회 -> IP 기반 1 증가 확인")
//         void anonymousView() {
//             long initialCount = getCurrentPostViewCount(postAId);
//
//             // 토큰 없이 호출
//             given().get("/api/v1/posts/{id}", postAId)
//                     .then().statusCode(200);
//
//             triggerSchedulerSync();
//
//             assertThat(getCurrentPostViewCount(postAId)).isEqualTo(initialCount + 1);
//         }
//
//         @Test
//         @DisplayName("SCENE 4: 어뷰징 방지 - 동일 유저 연속 조회 시 1회만 증가")
//         void abusePrevention() {
//             long initialCount = getCurrentPostViewCount(postAId);
//
//             // 연속 5회 호출
//             for (int i = 0; i < 5; i++) {
//                 given().cookie("ATK", tokenUserA)
//                         .get("/api/v1/posts/{id}", postAId)
//                         .then().statusCode(200);
//             }
//
//             triggerSchedulerSync();
//
//             assertThat(getCurrentPostViewCount(postAId)).isEqualTo(initialCount + 1);
//         }
//     }
//
//     // ========================================================================================
//     // [Category 2]. Concurrency & High Volume
//     // ========================================================================================
//     @Nested
//     @DisplayName("[Category 2] 동시성 및 대용량 트래픽 검증")
//     class Concurrency {
//
//         @Test
//         @DisplayName("SCENE 5: [Post] 50명의 서로 다른 유저(IP 조작)가 동시에 조회 -> 정확히 50 증가")
//         void concurrent_PostView() {
//             long initialCount = getCurrentPostViewCount(postAId);
//             int threadCount = 50;
//
//             ExecutorService executorService = Executors.newFixedThreadPool(20);
//
//             // 50개의 비동기 요청 (IP를 조작하여 서로 다른 비회원인 것처럼 위장)
//             List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
//                     .mapToObj(i -> CompletableFuture.runAsync(() -> {
//                         try {
//                             String fakeIp = "192.168.0." + (i + 1);
//
//                             given().header("X-Forwarded-For", fakeIp)
//                                     .get("/api/v1/posts/{id}", postAId)
//                                     .then().statusCode(200);
//
//                         } catch (Exception e) {
//                             System.err.println("Request failed: " + e.getMessage());
//                         }
//                     }, executorService))
//                     .toList();
//
//             // 모든 요청 대기
//             CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//             // 동기화
//             triggerSchedulerSync();
//
//             long finalCount = getCurrentPostViewCount(postAId);
//
//             // 검증
//             System.out.println("Post View - Initial: " + initialCount + ", Final: " + finalCount + ", Threads: " + threadCount);
//             assertThat(finalCount).isEqualTo(initialCount + threadCount);
//         }
//
//         @Test
//         @DisplayName("SCENE 6: [Archive] 30명의 유저가 동시에 조회 -> 정확히 30 증가")
//         void concurrent_ArchiveView() {
//             long initialCount = getCurrentArchiveViewCount(archiveAId);
//             int threadCount = 30;
//
//             ExecutorService executorService = Executors.newFixedThreadPool(15);
//
//             List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
//                     .mapToObj(i -> CompletableFuture.runAsync(() -> {
//                         String fakeIp = "10.0.0." + (i + 1);
//                         given().header("X-Forwarded-For", fakeIp)
//                                 .get("/api/v1/archives/{id}", archiveAId)
//                                 .then().statusCode(200);
//                     }, executorService))
//                     .toList();
//
//             CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//             triggerSchedulerSync();
//
//             long finalCount = getCurrentArchiveViewCount(archiveAId);
//
//             assertThat(finalCount).isEqualTo(initialCount + threadCount);
//         }
//
//         @Test
//         @DisplayName("SCENE 7: [Write-Back 정합성] 스케줄러 동기화(DB 반영) 도중에 대량의 조회 요청이 들어와도 누락이 없어야 한다.")
//         void concurrent_WriteBack_During_Traffic() {
//             // Given
//             long initialDbCount = getCurrentPostViewCount(postAId);
//             long prePopulatedRedisCount = 3000L; // 스케줄러가 가져갈 물량
//             int additionalTrafficCount = 500;   // 동기화 도중 치고 들어올 물량
//
//             // 1. Redis에 미리 대량의 조회수 적립 (스케줄러가 처리할 시간을 벌기 위함)
//             String key = "view:count:post:" + postAId;
//             redisTemplate.opsForValue().set(key, String.valueOf(prePopulatedRedisCount));
//
//             ExecutorService executorService = Executors.newFixedThreadPool(16);
//
//             // When
//             // Task A: 스케줄러 강제 실행 (Sync) - 별도 스레드에서 실행
//             CompletableFuture<Void> syncTask = CompletableFuture.runAsync(ViewCountApiTest.this::triggerSchedulerSync, executorService);
//
//             // Task B: 스케줄러가 도는 동안 500건의 추가 조회 요청 폭격
//             List<CompletableFuture<Void>> trafficTasks = IntStream.range(0, additionalTrafficCount)
//                     .mapToObj(i -> CompletableFuture.runAsync(() -> {
//                         try {
//                             // 스케줄러가 시작될 틈을 아주 살짝 줌 (현실적인 시나리오)
//                             Thread.sleep(5);
//
//                             // 어뷰징 방지 우회 (IP 조작)
//                             String fakeIp = "172.10.0." + (i + 1);
//                             given().header("X-Forwarded-For", fakeIp)
//                                     .get("/api/v1/posts/{id}", postAId)
//                                     .then().statusCode(200);
//                         } catch (Exception e) {
//                             System.err.println("Traffic request failed: " + e.getMessage());
//                         }
//                     }, executorService))
//                     .toList();
//
//             // 모든 작업(스케줄러 + 추가 요청)이 끝날 때까지 대기
//             CompletableFuture.allOf(syncTask).join();
//             CompletableFuture.allOf(trafficTasks.toArray(new CompletableFuture[0])).join();
//
//             // Then
//             // 검증 공식: (최종 DB 값) + (Redis 잔여 값) == (초기 DB 값) + (미리 넣은 값) + (추가 요청 값)
//             long finalDbCount = getCurrentPostViewCount(postAId);
//             String redisValStr = redisTemplate.opsForValue().get(key);
//             long finalRedisCount = (redisValStr != null) ? Long.parseLong(redisValStr) : 0;
//
//             long totalExpected = initialDbCount + prePopulatedRedisCount + additionalTrafficCount;
//             long totalActual = finalDbCount + finalRedisCount;
//
//             System.out.println("=== Write-Back Consistency Check ===");
//             System.out.println("Initial DB: " + initialDbCount);
//             System.out.println("Pre-populated Redis: " + prePopulatedRedisCount);
//             System.out.println("Additional Traffic: " + additionalTrafficCount);
//             System.out.println("------------------------------------");
//             System.out.println("Final DB: " + finalDbCount);
//             System.out.println("Final Redis (Remaining): " + finalRedisCount);
//             System.out.println("Total Actual (DB+Redis): " + totalActual);
//
//             assertThat(totalActual).isEqualTo(totalExpected);
//         }
//     }
//
//     // ========================================================================================
//     // Internal Helper Steps (패키지 접근 문제 해결을 위한 내부 정의)
//     // ========================================================================================
//
//     static class AuthSteps {
//         static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
//             String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
//             try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}
//
//             given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
//             try { Thread.sleep(500); } catch (InterruptedException ignored) {}
//
//             String code = getVerificationCode(email, mailhogUrl);
//             given().contentType(ContentType.JSON).body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
//                     .post("/api/v1/auth/email/verify").then().statusCode(200);
//
//             int userId = given().contentType(ContentType.JSON)
//                     .body(Map.of("email", email, "nickname", nickname, "password", password))
//                     .post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");
//
//             Response loginRes = given().contentType(ContentType.JSON)
//                     .body(Map.of("email", email, "password", password))
//                     .post("/api/v1/auth/login");
//
//             return Map.of("accessToken", loginRes.getCookie("ATK"), "userId", userId);
//         }
//
//         private static String getVerificationCode(String email, String mailhogUrl) {
//             for (int i = 0; i < 20; i++) {
//                 try {
//                     Response res = RestAssured.given().get(mailhogUrl);
//                     List<Map<String, Object>> messages = res.jsonPath().getList("items");
//                     if (messages != null) {
//                         for (Map<String, Object> msg : messages) {
//                             if (msg.toString().contains(email)) {
//                                 Matcher m = Pattern.compile("\\d{6}").matcher(((Map) msg.get("Content")).get("Body").toString());
//                                 if (m.find()) return m.group();
//                             }
//                         }
//                     }
//                     Thread.sleep(500);
//                 } catch (Exception ignored) {}
//             }
//             throw new RuntimeException("MailHog Fail: " + email);
//         }
//     }
//
//     static class PostSteps {
//         static Long create(String token, String title, String cat, Long fid) {
//             java.util.Map<String, Object> body = new java.util.HashMap<>();
//             body.put("title", title);
//             body.put("content", "Content");
//             body.put("category", cat);
//
//             if(fid != null) {
//                 body.put("files", List.of(Map.of("fileId", fid, "mediaRole", "PREVIEW", "sequence", 0)));
//             } else {
//                 body.put("files", List.of());
//             }
//
//             return given().cookie("ATK", token).contentType(ContentType.JSON).body(body)
//                     .post("/api/v1/posts").then().statusCode(201).extract().jsonPath().getLong("id");
//         }
//     }
//
//     static class ArchiveSteps {
//         static Long create(String token, String title, String visibility) {
//             return given().cookie("ATK", token).contentType(ContentType.JSON)
//                     .body(Map.of("title", title, "visibility", visibility))
//                     .post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
//         }
//     }
// }