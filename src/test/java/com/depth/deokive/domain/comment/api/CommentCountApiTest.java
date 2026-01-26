package com.depth.deokive.domain.comment.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.comment.repository.CommentRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@DisplayName("Comment Count Redis 캐싱 API 테스트")
class CommentCountApiTest extends ApiTestSupport {

    // 검증용 (데이터 조회만)
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private RedisTemplate<String, Long> longRedisTemplate;

    private static final String REDIS_KEY_PREFIX = "comment:count:";

    // 공유 데이터 (유저는 static)
    private static String tokenUserA;
    private static String tokenUserB;

    // 테스트별 격리 (게시글은 instance)
    private Long postId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // S3 Mocking
        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> {
            String uniqueKey = "files/" + UUID.randomUUID() + "__test.jpg";
            return S3ServiceDto.UploadInitiateResponse.builder()
                    .uploadId("mock-upload-id")
                    .key(uniqueKey)
                    .contentType("image/jpeg")
                    .build();
        });
        when(s3Service.calculatePartCount(any())).thenReturn(1);
        when(s3Service.generatePartPresignedUrls(any())).thenReturn(List.of());
        when(s3Service.completeUpload(any())).thenAnswer(invocation -> {
            S3ServiceDto.CompleteUploadRequest req = invocation.getArgument(0);
            return software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder()
                    .location("http://test-cdn.com/" + req.getKey())
                    .eTag("mock-etag")
                    .build();
        });

        // 유저 생성 (최초 1회)
        if (tokenUserA == null) {
            Map<String, Object> userA = AuthSteps.registerAndLogin("comment.count.a@test.com", "CommentA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");

            Map<String, Object> userB = AuthSteps.registerAndLogin("comment.count.b@test.com", "CommentB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
        }

        // 게시글 생성 (매 테스트마다 새로 생성 - 격리)
        postId = createPost(tokenUserA);

        // Redis 캐시 정리
        longRedisTemplate.delete(REDIS_KEY_PREFIX + postId);
    }

    // ========================================================================================
    // [Category 1] 데이터 정합성 테스트
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 데이터 정합성")
    class DataIntegrity {

        @Test
        @DisplayName("SCENE 1. 댓글 작성 시 댓글 수 +1")
        void createComment_IncrementsCount() {
            // Given: 캐시 warm-up (조회로 초기화)
            warmUpCache(postId);
            awaitCacheValue(postId, 0L);

            // When: 댓글 작성
            createComment(tokenUserA, postId, "첫 번째 댓글", null);

            // Then: Redis 캐시 +1
            awaitCacheValue(postId, 1L);
            assertDbCount(postId, 1L);
        }

        @Test
        @DisplayName("SCENE 2. 대댓글 작성 시 댓글 수 +1")
        void createReply_IncrementsCount() {
            // Given: 부모 댓글 생성 + 캐시 warm-up
            Long parentId = createComment(tokenUserA, postId, "부모 댓글", null);
            warmUpCache(postId);
            assertCacheValue(postId, 1L);

            // When: 대댓글 작성
            createComment(tokenUserB, postId, "대댓글", parentId);

            // Then: 캐시 +1 (총 2)
            awaitCacheValue(postId, 2L);
            assertDbCount(postId, 2L);
        }

        @Test
        @DisplayName("SCENE 3. 대댓글 삭제 시 댓글 수 -1")
        void deleteReply_DecrementsCount() {
            // Given: 부모 + 대댓글 생성
            Long parentId = createComment(tokenUserA, postId, "부모 댓글", null);
            Long replyId = createComment(tokenUserB, postId, "대댓글", parentId);
            warmUpCache(postId);
            assertCacheValue(postId, 2L);

            // When: 대댓글 삭제 (작성자 B)
            deleteComment(tokenUserB, replyId);

            // Then: 캐시 -1 (총 1)
            awaitCacheValue(postId, 1L);
            assertDbCount(postId, 1L);
        }

        @Test
        @DisplayName("SCENE 4. 부모댓글(+대댓글3) 삭제 시 -4")
        void deleteParentWithReplies_DecrementsCountByTotal() {
            // Given: 부모 + 대댓글 3개 생성
            Long parentId = createComment(tokenUserA, postId, "부모 댓글", null);
            createComment(tokenUserB, postId, "대댓글 1", parentId);
            createComment(tokenUserB, postId, "대댓글 2", parentId);
            createComment(tokenUserB, postId, "대댓글 3", parentId);
            warmUpCache(postId);
            assertCacheValue(postId, 4L);

            // When: 부모 댓글 삭제 (cascade로 대댓글도 삭제)
            deleteComment(tokenUserA, parentId);

            // Then: 캐시 -4 (총 0)
            awaitCacheValue(postId, 0L);
            assertDbCount(postId, 0L);
        }

        @Test
        @DisplayName("SCENE 5. Cache-DB 정합성 (1~4 과정 통합)")
        void cacheDbConsistency_ThroughAllOperations() {
            // Step 1: 초기 상태
            warmUpCache(postId);
            assertConsistency(postId, 0L);

            // Step 2: 댓글 3개 생성
            Long c1 = createComment(tokenUserA, postId, "댓글 1", null);
            Long c2 = createComment(tokenUserA, postId, "댓글 2", null);
            Long c3 = createComment(tokenUserB, postId, "댓글 3", null);
            awaitCacheValue(postId, 3L);
            assertConsistency(postId, 3L);

            // Step 3: 대댓글 2개 생성
            createComment(tokenUserB, postId, "대댓글 1-1", c1);
            createComment(tokenUserB, postId, "대댓글 1-2", c1);
            awaitCacheValue(postId, 5L);
            assertConsistency(postId, 5L);

            // Step 4: 대댓글 1개 삭제
            Long replyToDelete = getFirstReplyId(postId, c1);
            deleteComment(tokenUserB, replyToDelete);
            awaitCacheValue(postId, 4L);
            assertConsistency(postId, 4L);

            // Step 5: 부모 댓글 삭제 (남은 대댓글 1개 포함)
            deleteComment(tokenUserA, c1);
            awaitCacheValue(postId, 2L);
            assertConsistency(postId, 2L);
        }

        @Test
        @DisplayName("SCENE 6. Post 삭제 시 Comment Bulk 삭제 + Cache 삭제")
        void deletePost_RemovesAllCommentsAndCache() {
            // Given: 댓글 5개 생성 (부모 2 + 대댓글 3)
            Long p1 = createComment(tokenUserA, postId, "부모 1", null);
            Long p2 = createComment(tokenUserA, postId, "부모 2", null);
            createComment(tokenUserB, postId, "대댓글 1-1", p1);
            createComment(tokenUserB, postId, "대댓글 1-2", p1);
            createComment(tokenUserB, postId, "대댓글 2-1", p2);
            warmUpCache(postId);
            assertCacheValue(postId, 5L);

            // When: Post 삭제
            deletePost(tokenUserA, postId);

            // Then: DB에서 Comment 모두 삭제
            assertDbCount(postId, 0L);

            // Then: Redis 캐시도 삭제됨
            assertThat(longRedisTemplate.hasKey(REDIS_KEY_PREFIX + postId)).isFalse();
        }
    }

    // ========================================================================================
    // [Category 2] 비정상 시나리오
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 비정상 시나리오")
    class AbnormalScenarios {

        @Test
        @DisplayName("SCENE 7. Cache Miss 시 Warm-up")
        void cacheMiss_WarmsUpFromDb() {
            // Given: 댓글 3개 생성 (캐시 없이)
            createComment(tokenUserA, postId, "댓글 1", null);
            createComment(tokenUserA, postId, "댓글 2", null);
            createComment(tokenUserA, postId, "댓글 3", null);

            // 캐시 강제 삭제
            longRedisTemplate.delete(REDIS_KEY_PREFIX + postId);
            assertThat(longRedisTemplate.hasKey(REDIS_KEY_PREFIX + postId)).isFalse();

            // When: 댓글 조회 (Cache Miss 발생)
            given()
                    .get("/api/v1/posts/{postId}/comments", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("totalCount", equalTo(3));

            // Then: 캐시 자동 생성됨
            assertThat(longRedisTemplate.hasKey(REDIS_KEY_PREFIX + postId)).isTrue();
            assertCacheValue(postId, 3L);
        }

        // SCENE 8: 동시성 테스트 - K6로 이관
    }

    // ========================================================================================
    // [Category 3] Release 검증
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Release 검증")
    class ReleaseValidation {

        @Test
        @DisplayName("SCENE 9. totalCount 응답 포함 확인")
        void getComments_ReturnsTotalCount() {
            // Given: 댓글 5개 생성
            for (int i = 0; i < 5; i++) {
                createComment(tokenUserA, postId, "댓글 " + i, null);
            }

            // When & Then
            given()
                    .cookie("ATK", tokenUserA)
                    .get("/api/v1/posts/{postId}/comments", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("totalCount", equalTo(5))
                    .body("content.size()", equalTo(5))
                    .body("hasNext", equalTo(false));
        }

        @Test
        @DisplayName("SCENE 10. 비로그인 사용자 댓글 조회")
        void getComments_AnonymousUser() {
            // Given: 댓글 2개 생성
            createComment(tokenUserA, postId, "댓글 1", null);
            createComment(tokenUserA, postId, "댓글 2", null);

            // When & Then: 인증 없이 조회 가능
            given()
                    .get("/api/v1/posts/{postId}/comments", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("totalCount", equalTo(2));
        }

        @Test
        @DisplayName("SCENE 11. 2-Depth 제한 검증 (대대댓글 불가)")
        void createComment_ThirdDepth_Rejected() {
            // Given: 부모 + 대댓글 생성
            Long parentId = createComment(tokenUserA, postId, "부모 댓글", null);
            Long replyId = createComment(tokenUserB, postId, "대댓글", parentId);

            // When: 대대댓글 시도
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("postId", postId, "content", "대대댓글", "parentId", replyId))
                    .post("/api/v1/comments")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 12. 타인 댓글 삭제 시도 - 403")
        void deleteComment_OtherUser_Forbidden() {
            // Given: UserA가 댓글 작성
            Long commentId = createComment(tokenUserA, postId, "A의 댓글", null);

            // When: UserB가 삭제 시도
            given()
                    .cookie("ATK", tokenUserB)
                    .delete("/api/v1/comments/{commentId}", commentId)
                    .then()
                    .statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 13. 존재하지 않는 댓글 삭제 - 404")
        void deleteComment_NotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .delete("/api/v1/comments/{commentId}", 99999L)
                    .then()
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("COMMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 14. 존재하지 않는 게시글에 댓글 작성 - 404")
        void createComment_PostNotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("postId", 99999L, "content", "댓글"))
                    .post("/api/v1/comments")
                    .then()
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("POST_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 15. 빈 게시글 댓글 조회")
        void getComments_EmptyPost() {
            // When & Then: 댓글 없는 게시글 조회
            given()
                    .get("/api/v1/posts/{postId}/comments", postId)
                    .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("totalCount", equalTo(0))
                    .body("content", empty())
                    .body("hasNext", equalTo(false));
        }

        @Test
        @DisplayName("SCENE 16. Async 이벤트 타이밍 (AFTER_COMMIT)")
        void asyncEventTiming_AfterCommit() {
            // Given: 캐시 warm-up
            warmUpCache(postId);

            // When: 댓글 생성 (AFTER_COMMIT 비동기 이벤트)
            createComment(tokenUserA, postId, "비동기 테스트", null);

            // Then: Awaitility로 비동기 처리 대기 (최대 2초)
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                Long cached = longRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + postId);
                assertThat(cached).isEqualTo(1L);
            });
        }
    }

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    private Long createPost(String token) {
        return given()
                .cookie("ATK", token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "Test Post " + UUID.randomUUID(),
                        "content", "Test Content",
                        "category", "IDOL",
                        "files", List.of()
                ))
                .post("/api/v1/posts")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract().jsonPath().getLong("id");
    }

    private Long createComment(String token, Long postId, String content, Long parentId) {
        Map<String, Object> body = parentId == null
                ? Map.of("postId", postId, "content", content)
                : Map.of("postId", postId, "content", content, "parentId", parentId);

        given()
                .cookie("ATK", token)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/comments")
                .then()
                .statusCode(HttpStatus.OK.value());

        // 댓글 ID 조회 (최신 댓글 - 계층 구조 고려)
        // 대댓글의 경우 부모의 children 배열에 있으므로, 모든 댓글을 평탄화해서 찾아야 함
        Response response = given()
                .cookie("ATK", token)
                .get("/api/v1/posts/{postId}/comments", postId);
        
        List<Map<String, Object>> commentList = response.jsonPath().getList("content");
        Long latestCommentId = null;
        Long maxId = -1L;
        
        // 모든 댓글(부모 + 자식)을 순회하며 내용이 일치하는 댓글 중 ID가 가장 큰 것 찾기
        for (Map<String, Object> comment : commentList) {
            Long commentId = ((Number) comment.get("commentId")).longValue();
            String commentContent = (String) comment.get("content");
            
            // 내용이 일치하고 ID가 더 큰 경우
            if (content.equals(commentContent) && commentId > maxId) {
                latestCommentId = commentId;
                maxId = commentId;
            }
            
            // 자식 댓글들도 확인
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) comment.get("children");
            if (children != null) {
                for (Map<String, Object> child : children) {
                    Long childId = ((Number) child.get("commentId")).longValue();
                    String childContent = (String) child.get("content");
                    
                    if (content.equals(childContent) && childId > maxId) {
                        latestCommentId = childId;
                        maxId = childId;
                    }
                }
            }
        }
        
        if (latestCommentId == null) {
            throw new RuntimeException("Failed to find created comment with content: " + content);
        }
        
        return latestCommentId;
    }

    private void deleteComment(String token, Long commentId) {
        given()
                .cookie("ATK", token)
                .delete("/api/v1/comments/{commentId}", commentId)
                .then()
                .statusCode(HttpStatus.OK.value());
    }

    private void deletePost(String token, Long postId) {
        given()
                .cookie("ATK", token)
                .delete("/api/v1/posts/{postId}", postId)
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());
    }

    private void warmUpCache(Long postId) {
        given()
                .get("/api/v1/posts/{postId}/comments", postId)
                .then()
                .statusCode(HttpStatus.OK.value());
        // 캐시가 생성될 때까지 대기 (최대 1초)
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(longRedisTemplate.hasKey(REDIS_KEY_PREFIX + postId)).isTrue();
        });
    }

    private Long getFirstReplyId(Long postId, Long parentId) {
        return given()
                .get("/api/v1/posts/{postId}/comments", postId)
                .jsonPath()
                .getLong("content.find { it.commentId == " + parentId + " }.children[0].commentId");
    }

    private void assertCacheValue(Long postId, Long expected) {
        Long actual = longRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + postId);
        assertThat(actual).isEqualTo(expected);
    }

    private void awaitCacheValue(Long postId, Long expected) {
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Long actual = longRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + postId);
            assertThat(actual).isEqualTo(expected);
        });
    }

    private void assertDbCount(Long postId, Long expected) {
        long actual = commentRepository.countByPostId(postId);
        assertThat(actual).isEqualTo(expected);
    }

    private void assertConsistency(Long postId, Long expected) {
        awaitCacheValue(postId, expected);
        assertDbCount(postId, expected);
    }

    // ========================================================================================
    // Auth Helper
    // ========================================================================================
    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            try { RestAssured.given().delete(ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages"); } catch (Exception ignored) {}
            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            String code = getVerificationCode(email, ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages");
            given().contentType(ContentType.JSON).body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify").then().statusCode(200);
            int userId = given().contentType(ContentType.JSON).body(Map.of("email", email, "nickname", nickname, "password", password))
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
}
