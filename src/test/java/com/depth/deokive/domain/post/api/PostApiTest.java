package com.depth.deokive.domain.post.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostStats;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@DisplayName("Post API 통합 테스트 시나리오")
class PostApiTest extends ApiTestSupport {

    // --- Repositories ---
    @Autowired private PostRepository postRepository;
    @Autowired private PostFileMapRepository postFileMapRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private com.depth.deokive.domain.post.repository.PostStatsRepository postStatsRepository;
    @Autowired private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    // --- Actors (Token) ---
    private static String tokenUserA; // Writer
    private static String tokenUserB; // Stranger

    // --- Shared Data ---
    private static Long userAId;

    // --- Files ---
    private static List<Long> userAFiles; // FileA_1 ~ FileA_5
    private static List<Long> userBFiles; // FileB_1

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // [S3 Mocking]
        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> {
            String uniqueKey = "files/" + UUID.randomUUID() + "__test.jpg";
            return S3ServiceDto.UploadInitiateResponse.builder()
                    .uploadId("mock-upload-id")
                    .key(uniqueKey)
                    .contentType("image/jpeg")
                    .build();
        });
        when(s3Service.calculatePartCount(any())).thenReturn(1);
        when(s3Service.generatePartPresignedUrls(any())).thenAnswer(invocation -> List.of());
        when(s3Service.completeUpload(any())).thenAnswer(invocation -> {
            S3ServiceDto.CompleteUploadRequest req = invocation.getArgument(0);
            return software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder()
                    .location("http://test-cdn.com/" + req.getKey())
                    .eTag("mock-etag")
                    .build();
        });

        // [Global Setup] 최초 1회 실행
        if (tokenUserA == null) {
            // 1. Users Setup
            Map<String, Object> userA = AuthSteps.registerAndLogin("post.a@test.com", "PostA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("post.b@test.com", "PostB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");

            // 2. Files
            userAFiles = new ArrayList<>();
            for(int i=0; i<5; i++) userAFiles.add(FileSteps.uploadFile(tokenUserA));

            userBFiles = new ArrayList<>();
            userBFiles.add(FileSteps.uploadFile(tokenUserB));
        }
    }

    // ========================================================================================
    // [Category 1]. Create Post (POST /api/v1/posts)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 게시글 생성")
    class CreatePost {

        @Test
        @DisplayName("SCENE 1. 정상 생성 - 파일 포함 (썸네일 지정)")
        void createPost_WithThumbnail() {
            // Given
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userAFiles.get(0), "mediaRole", "PREVIEW", "sequence", 0),
                    Map.of("fileId", userAFiles.get(1), "mediaRole", "CONTENT", "sequence", 1)
            );

            Map<String, Object> request = Map.of(
                    "title", "Idol Post",
                    "content", "Content",
                    "category", "IDOL",
                    "files", files
            );

            // When
            int postId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    // Then: Response Validation (DTO에 thumbnailUrl 없음)
                    .body("files.size()", equalTo(2))
                    .body("files[0].mediaRole", equalTo("PREVIEW"))
                    .body("files[0].cdnUrl", containsString("http"))
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation (DB에는 썸네일 키가 저장되어야 함)
            Post post = postRepository.findById((long) postId).orElseThrow();
            assertThat(post.getThumbnailKey()).isNotNull();
            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc((long) postId)).hasSize(2);
        }

        @Test
        @DisplayName("SCENE 2. 정상 생성 - 파일 없이 생성")
        void createPost_NoFiles() {
            // Given
            Map<String, Object> request = Map.of(
                    "title", "스포츠 게시글",
                    "content", "내용",
                    "category", "SPORT",
                    "files", List.of()
            );

            // When
            int postId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("title", equalTo("스포츠 게시글"))
                    .body("category", equalTo("SPORT"))
                    .body("files", empty())
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation
            Post post = postRepository.findById((long) postId).orElseThrow();
            assertThat(post.getTitle()).isEqualTo("스포츠 게시글");
            assertThat(post.getCategory()).isEqualTo(Category.SPORT);
            assertThat(post.getThumbnailKey()).isNull();
            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc((long) postId)).isEmpty();
        }

        @Test
        @DisplayName("SCENE 3. 정상 생성 - 모든 카테고리 지원 확인")
        void createPost_AllCategories() {
            for (Category category : Category.values()) {
                Map<String, Object> request = Map.of("title", "C", "content", "C", "category", category.name());

                given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                        .post("/api/v1/posts")
                        .then().statusCode(HttpStatus.CREATED.value())
                        .body("category", equalTo(category.name()));
            }
        }

        @Test
        @DisplayName("SCENE 4. 예외 - 필수값 누락")
        void createPost_BadRequest() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("category", "IDOL"))
                    .post("/api/v1/posts").then().statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 5. 예외 - IDOR (타인의 파일 첨부 시도)")
        void createPost_IDOR() {
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userBFiles.get(0), "mediaRole", "CONTENT", "sequence", 0)
            );
            Map<String, Object> request = Map.of("title", "Hack", "content", "C", "category", "IDOL", "files", files);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .post("/api/v1/posts").then().statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 6. 예외 - 존재하지 않는 파일 ID")
        void createPost_FileNotFound() {
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", 99999, "mediaRole", "CONTENT", "sequence", 0)
            );
            Map<String, Object> request = Map.of("title", "Fail", "content", "C", "category", "IDOL", "files", files);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .post("/api/v1/posts").then().statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("FILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 7. 예외 - 중복된 파일 ID 전송")
        void createPost_DuplicateFile() {
            Long fileId = userAFiles.get(0);
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", fileId, "mediaRole", "CONTENT", "sequence", 0),
                    Map.of("fileId", fileId, "mediaRole", "CONTENT", "sequence", 1)
            );
            Map<String, Object> request = Map.of("title", "Dup", "content", "C", "category", "IDOL", "files", files);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .post("/api/v1/posts").then().statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("FILE_NOT_FOUND")); // Service logic: distinct count check
        }

        @Test
        @DisplayName("SCENE 8. 썸네일 선정 로직 - PREVIEW 없이 Sequence 0번만 있는 경우")
        void createPost_Thumbnail_NoPreview_Sequence0() {
            // Given: PREVIEW 없이 CONTENT만 있고 Sequence가 0번인 경우
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userAFiles.get(0), "mediaRole", "CONTENT", "sequence", 0)
            );

            Map<String, Object> request = Map.of(
                    "title", "No Preview Post",
                    "content", "Content",
                    "category", "IDOL",
                    "files", files
            );

            // When
            int postId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .extract().jsonPath().getInt("id");

            // Then: Sequence 0번 파일이 썸네일로 선정되어야 함
            Post post = postRepository.findById((long) postId).orElseThrow();
            assertThat(post.getThumbnailKey()).isNotNull();

            // 실제 File 엔티티 조회하여 S3 Key 비교
            File expectedFile = fileRepository.findById(userAFiles.get(0)).orElseThrow();
            String expectedThumbKey = ThumbnailUtils.getMediumThumbnailKey(expectedFile.getS3ObjectKey());
            assertThat(post.getThumbnailKey()).isEqualTo(expectedThumbKey);
        }

        @Test
        @DisplayName("SCENE 9. 예외 - 필수값 누락 (에러 메시지 검증)")
        void createPost_BadRequest_ErrorMessage() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("category", "IDOL"))
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue()); // 에러 코드 검증
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail (GET /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 게시글 상세 조회")
    class ReadPost {
        private Long postId;

        @BeforeEach
        void initPost() {
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userAFiles.get(0), "mediaRole", "PREVIEW", "sequence", 0)
            );
            Map<String, Object> request = Map.of("title", "ReadMe", "content", "C", "category", "IDOL", "files", files);
            postId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .post("/api/v1/posts").jsonPath().getLong("id");
        }

        @Test
        @DisplayName("SCENE 8. 정상 조회 - 작성자 본인")
        void readPost_Owner() {
            given().cookie("ATK", tokenUserA).get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200)
                    .body("title", equalTo("ReadMe"))
                    .body("files.size()", equalTo(1))
                    // DTO에 thumbnailUrl 필드가 없으므로 제거
                    .body("files[0].cdnUrl", containsString("http"));
        }

        @Test
        @DisplayName("SCENE 9. 정상 조회 - 타인 (UserB)")
        void readPost_Stranger() {
            given().cookie("ATK", tokenUserB).get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("SCENE 10. 정상 조회 - 비회원 (Anonymous)")
        void readPost_Anonymous() {
            given().get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("SCENE 11. 조회수 증가 확인")
        void readPost_ViewCount() {
            // 초기 조회수는 DB에서 직접 가져옴 (API 호출 시 조회수 증가 방지)
            long initial = postStatsRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("PostStats not found"))
                    .getViewCount();
            
            // 2번 조회 (다른 IP로 시뮬레이션)
            given().header("X-Forwarded-For", "1.1.1.1")
                    .get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);
            
            given().header("X-Forwarded-For", "1.1.1.2")
                    .get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);
            
            // 스케줄러 강제 실행 (DB 반영) - API를 통해 호출
            given().contentType(ContentType.JSON)
                    .post("/api/system/test/scheduler/view-count")
                    .then()
                    .statusCode(HttpStatus.OK.value());
            
            // DB 반영 후 검증
            given().get("/api/v1/posts/{id}", postId)
                    .then().body("viewCount", equalTo((int) initial + 2));
        }

        @Test
        @DisplayName("SCENE 16. 조회수 증가 확인 (DB 반영 확인)")
        void readPost_ViewCount_DB_Sync() {
            String countKey = "view:count:post:" + postId;
            long initialCount = postStatsRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("PostStats not found"))
                    .getViewCount();

            // 1. 3번 조회 (다른 IP로 시뮬레이션)
            given().header("X-Forwarded-For", "1.1.1.1")
                    .get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);

            given().header("X-Forwarded-For", "1.1.1.2")
                    .get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);

            given().header("X-Forwarded-For", "1.1.1.3")
                    .get("/api/v1/posts/{id}", postId)
                    .then().statusCode(200);

            // 2. Redis에 쌓였는지 확인 (아직 DB 반영 전)
            String redisValue = redisTemplate.opsForValue().get(countKey);
            assertThat(redisValue).isNotNull();
            assertThat(Long.parseLong(redisValue)).isEqualTo(3L);

            // 3. 스케줄러 강제 실행 (DB 반영) - API를 통해 호출
            given().contentType(ContentType.JSON)
                    .post("/api/system/test/scheduler/view-count")
                    .then()
                    .statusCode(HttpStatus.OK.value());

            // 4. DB 검증 (PostStats에서 조회)
            long finalCount = postStatsRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("PostStats not found"))
                    .getViewCount();
            assertThat(finalCount).isEqualTo(initialCount + 3);

            // 5. Redis 키 삭제 확인
            assertThat(redisTemplate.hasKey(countKey)).isFalse();
        }

        @Test
        @DisplayName("SCENE 12. 예외 - 존재하지 않는 게시글")
        void readPost_NotFound() {
            given().get("/api/v1/posts/{id}", 99999)
                    .then()
                    .statusCode(404)
                    .body("error", equalTo("POST_NOT_FOUND"));
        }
    }

    // ========================================================================================
    // [Category 3]. Update Post (PATCH /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 게시글 수정")
    class UpdatePost {
        private Long postId;

        @BeforeEach
        void initPost() {
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userAFiles.get(0), "mediaRole", "PREVIEW", "sequence", 0)
            );
            Map<String, Object> request = Map.of("title", "Old", "content", "Old", "category", "IDOL", "files", files);
            postId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .post("/api/v1/posts").jsonPath().getLong("id");
        }

        @Test
        @DisplayName("SCENE 13. 정상 수정 - 텍스트 정보만 변경")
        void updatePost_TextOnly() {
            Map<String, Object> request = Map.of("title", "Updated", "content", "Updated");
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .patch("/api/v1/posts/{id}", postId)
                    .then().statusCode(200)
                    .body("title", equalTo("Updated"))
                    .body("content", equalTo("Updated"))
                    .body("files.size()", equalTo(1)); // Files preserved
        }

        @Test
        @DisplayName("SCENE 14. 정상 수정 - 파일 전체 교체 (Replace)")
        void updatePost_ReplaceFiles() {
            List<Map<String, Object>> newFiles = List.of(
                    Map.of("fileId", userAFiles.get(1), "mediaRole", "PREVIEW", "sequence", 0)
            );
            Map<String, Object> request = Map.of("files", newFiles);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .patch("/api/v1/posts/{id}", postId)
                    .then().statusCode(200)
                    .body("files.size()", equalTo(1))
                    .body("files[0].fileId", equalTo(userAFiles.get(1).intValue()));

            // DB Thumbnail check
            Post post = postRepository.findById(postId).orElseThrow();

            // 실제 File 엔티티 조회하여 S3 Key 비교 (IDOR 방지용 검증)
            File expectedFile = fileRepository.findById(userAFiles.get(1)).orElseThrow();
            String expectedThumbKey = ThumbnailUtils.getMediumThumbnailKey(expectedFile.getS3ObjectKey());

            assertThat(post.getThumbnailKey()).isEqualTo(expectedThumbKey);
        }

        @Test
        @DisplayName("SCENE 15. 정상 수정 - 파일 전체 삭제")
        void updatePost_DeleteFiles() {
            Map<String, Object> request = Map.of("files", List.of());
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .patch("/api/v1/posts/{id}", postId)
                    .then().statusCode(200)
                    .body("files", empty());

            Post post = postRepository.findById(postId).orElseThrow();
            assertThat(post.getThumbnailKey()).isNull();
        }

        @Test
        @DisplayName("SCENE 16. 예외 - 타인(UserB)이 수정 시도")
        void updatePost_Forbidden() {
            given().cookie("ATK", tokenUserB).contentType(ContentType.JSON).body(Map.of("title", "Hack"))
                    .patch("/api/v1/posts/{id}", postId).then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 17. 예외 - IDOR (타인 파일 사용)")
        void updatePost_IDOR() {
            List<Map<String, Object>> files = List.of(
                    Map.of("fileId", userBFiles.get(0), "mediaRole", "CONTENT", "sequence", 0)
            );
            Map<String, Object> request = Map.of("files", files);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .patch("/api/v1/posts/{id}", postId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Post (DELETE /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 게시글 삭제")
    class DeletePost {
        private Long postId;

        @BeforeEach
        void initPost() {
            postId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Del", "content", "C", "category", "SPORT"))
                    .post("/api/v1/posts").jsonPath().getLong("id");
        }

        @Test
        @DisplayName("SCENE 18. 정상 삭제 - 본인")
        void deletePost_Normal() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/posts/{id}", postId).then().statusCode(204);

            assertThat(postRepository.existsById(postId)).isFalse();
            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId)).isEmpty();
        }

        @Test
        @DisplayName("SCENE 19. 예외 - 타인 삭제 시도")
        void deletePost_Forbidden() {
            given().cookie("ATK", tokenUserB)
                    .delete("/api/v1/posts/{id}", postId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }
    }

    // ========================================================================================
    // [Category 5]. Feed & Pagination (GET /api/v1/posts)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 게시글 피드 조회")
    class PostFeed {
        private static String feedTestUserToken;
        private static Long recentId, idolId, actorId;

        @BeforeEach
        void setUpFeedData() {
            // 최초 1회만 유저 생성 및 게시글 생성 (데이터 재사용)
            if (feedTestUserToken == null) {
                // API를 통해 유저 등록 및 로그인
                // NOTE: 닉네임은 2~10자 제한이므로 UUID 8자리만 사용
                String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                Map<String, Object> userInfo = AuthSteps.registerAndLogin(
                        "feed.test." + UUID.randomUUID() + "@test.com",
                        "FT" + uuid, // 최대 10자 (FT + 8자 UUID)
                        "Password123!"
                );
                feedTestUserToken = (String) userInfo.get("accessToken");

                // API를 통해 게시글 생성 (실제 사용자 시나리오와 동일)
                // 정렬 테스트를 위해 createdAt을 명시적으로 설정
                java.time.LocalDateTime baseTime = java.time.LocalDateTime.now();
                
                // 1. Post_Actor (Created oldest, View 100, Hot 50)
                actorId = createPostViaApi("Actor", Category.ACTOR, 100L, 50.0, baseTime.minusDays(3));

                // 2. Post_Idol (Created middle, View 10, Hot 100)
                idolId = createPostViaApi("Idol", Category.IDOL, 10L, 100.0, baseTime.minusDays(2));

                // 3. Post_Recent (Created newest, View 1, Hot 0.1)
                // viewCount와 hotScore를 0보다 크게 설정하여 추가 Post들(0, 0)보다 높게 정렬되도록 함
                recentId = createPostViaApi("Recent", Category.IDOL, 1L, 0.1, baseTime.minusDays(1));

                // 정렬 테스트를 위해 추가 Post 생성 (30개)
                // 테스트 Post가 상위에 오도록 낮은 값과 과거 createdAt 설정
                for (int i = 1; i <= 30; i++) {
                    Category cat = (i % 2 == 0) ? Category.SPORT : Category.IDOL;
                    // 테스트 Post보다 훨씬 과거로 설정하여 정렬 순서 보장
                    createPostViaApi("Post " + i, cat, 0L, 0.0, baseTime.minusDays(10 + i));
                }
            }
        }

        private Long createPostViaApi(String title, Category category, Long viewCount, Double hotScore) {
            return createPostViaApi(title, category, viewCount, hotScore, null);
        }

        private Long createPostViaApi(String title, Category category, Long viewCount, Double hotScore, java.time.LocalDateTime customCreatedAt) {
            // API를 통해 게시글 생성
            Map<String, Object> request = Map.of(
                    "title", title,
                    "content", "Content",
                    "category", category.name(),
                    "files", List.of()
            );

            Long postId = given()
                    .cookie("ATK", feedTestUserToken)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/posts")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .extract().jsonPath().getLong("id");

            // PostStats의 viewCount와 hotScore를 테스트용으로 설정
            // postService.createPost()가 PostStats를 기본값(0, 0, 0.0)으로 자동 생성하므로,
            // Native Query로 직접 업데이트하여 detached entity 문제 회피
            Post post = postRepository.findById(postId).orElseThrow();
            java.time.LocalDateTime createdAt = customCreatedAt != null 
                    ? customCreatedAt 
                    : (post.getCreatedAt() != null ? post.getCreatedAt() : java.time.LocalDateTime.now());
            
            // Native Query로 직접 업데이트 (DataInitializer처럼)
            jdbcTemplate.update(
                    "UPDATE post_stats SET view_count = ?, hot_score = ?, created_at = ? WHERE post_id = ?",
                    viewCount, hotScore, createdAt, postId
            );

            return postId;
        }

        @Test
        @DisplayName("SCENE 20. 전체 조회 + 최신순 정렬")
        void feed_All_Recent() {
            given().param("sort", "createdAt").param("direction", "DESC").param("size", 3)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(3))
                    .body("content[0].postId", equalTo(recentId.intValue())) // Newest
                    .body("content[1].postId", equalTo(idolId.intValue()))
                    .body("content[2].postId", equalTo(actorId.intValue())); // Oldest
        }

        @Test
        @DisplayName("SCENE 21. 카테고리 필터링 (IDOL)")
        void feed_Category_Idol() {
            given().param("category", "IDOL").param("size", 2)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(2)) // Recent + Idol
                    .body("content.category", everyItem(equalTo("IDOL")));
        }

        @Test
        @DisplayName("SCENE 26. 카테고리 필터링 강화 - 다른 카테고리 제외 확인")
        void feed_Category_FilterExclusion() {
            // Given: 여러 카테고리 데이터가 이미 setUpFeedData에서 생성됨
            // Actor, Idol(2개) 총 3개

            // When: IDOL 카테고리만 조회
            given().param("category", "IDOL").param("size", 2)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(2)) // Idol만 2개
                    .body("content.category", everyItem(equalTo("IDOL")))
                    .body("content.postId", not(hasItem(actorId.intValue()))); // Actor 제외 확인

            // When: ACTOR 카테고리만 조회
            given().param("category", "ACTOR").param("size", 1)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(1)) // Actor만 1개
                    .body("content.category", everyItem(equalTo("ACTOR")))
                    .body("content.postId", not(hasItem(idolId.intValue()))) // Idol 제외 확인
                    .body("content.postId", not(hasItem(recentId.intValue()))); // Recent 제외 확인
        }

        @Test
        @DisplayName("SCENE 22. 인기순 정렬 (Hot Score)")
        void feed_HotScore() {
            given().param("sort", "hotScore").param("direction", "DESC").param("size", 3)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(3))
                    .body("content[0].postId", equalTo(idolId.intValue())) // Hot 100
                    .body("content[1].postId", equalTo(actorId.intValue())) // Hot 50
                    .body("content[2].postId", equalTo(recentId.intValue())); // Hot 0.1
        }

        @Test
        @DisplayName("SCENE 23. 조회수 정렬")
        void feed_ViewCount() {
            log.info("🧐 호출은 되고 있나?");
            given().param("sort", "viewCount").param("direction", "DESC").param("size", 3)
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content.size()", equalTo(3))
                    .body("content[0].postId", equalTo(actorId.intValue())) // View 100
                    .body("content[1].postId", equalTo(idolId.intValue())) // View 10
                    .body("content[2].postId", equalTo(recentId.intValue())); // View 1
        }

        @Test
        @DisplayName("SCENE 24. 빈 결과 조회")
        void feed_Empty() {
            given().param("category", "ANIMATION")
                    .get("/api/v1/posts")
                    .then().statusCode(200)
                    .body("content", empty());
        }

        @Test
        @DisplayName("SCENE 25. 페이지 범위 초과")
        void feed_PageOut() {
            given().param("page", 100)
                    .get("/api/v1/posts")
                    .then().statusCode(404)
                    .body("error", equalTo("PAGE_NOT_FOUND"));
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================

    static class AuthSteps {
        private static final String MAILHOG_API = "http://localhost:8025/api/v2/messages";

        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            try { RestAssured.given().delete(MAILHOG_API); } catch (Exception ignored) {}
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

    static class FileSteps {
        static Long uploadFile(String token) {
            Response init = given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("originalFileName", "p.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT"))
                    .post("/api/v1/files/multipart/initiate");
            String uploadId = init.jsonPath().getString("uploadId");
            String key = init.jsonPath().getString("key");
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("key", key, "uploadId", uploadId, "parts", List.of(Map.of("partNumber", 1, "etag", "e")),
                            "originalFileName", "p.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0))
                    .post("/api/v1/files/multipart/complete").then().statusCode(200).extract().jsonPath().getLong("fileId");
        }
    }
}