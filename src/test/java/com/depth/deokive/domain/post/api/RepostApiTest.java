package com.depth.deokive.domain.post.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.post.repository.RepostRepository;
import com.depth.deokive.domain.post.repository.RepostTabRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Repost API 통합 테스트 시나리오")
class RepostApiTest extends ApiTestSupport {

    @Autowired private RepostRepository repostRepository;
    @Autowired private RepostTabRepository repostTabRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;
    @Autowired private FileRepository fileRepository;

    // --- Static Variables (Test Context Shared) ---
    private static String tokenUserA, tokenUserB, tokenUserC;
    private static Long userAId, userBId, userCId;
    private static Long publicArchiveId, restrictedArchiveId, privateArchiveId;
    private static Long postAId, postBId, postCId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mockS3(); // S3 Mocking 공통 분리

        // [단계별 초기화]
        // 1. 유저가 없으면 유저 생성
        if (tokenUserA == null) {
            initUsers();
        }

        // 2. 아카이브가 없으면 아카이브 생성
        if (publicArchiveId == null) {
            initArchives();
        }

        // 3. 게시글이 없으면 게시글 생성 (여기가 NPE 원인이었음 -> 이제 안전함)
        if (postBId == null) {
            initPosts();
        }
    }

    private void mockS3() {
        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> {
            String uniqueKey = "files/" + UUID.randomUUID() + "__test.jpg";
            return S3ServiceDto.UploadInitiateResponse.builder()
                    .uploadId("mock-upload-id") // 필수
                    .key(uniqueKey)             // 필수
                    .contentType("image/jpeg")
                    .build();
        });

        when(s3Service.calculatePartCount(any())).thenReturn(1);

        when(s3Service.generatePartPresignedUrls(any())).thenAnswer(invocation -> {
            S3ServiceDto.PartPresignedUrlRequest req = invocation.getArgument(0);
            return List.of(S3ServiceDto.PartPresignedUrlResponse.builder()
                    .partNumber(1)
                    .presignedUrl("http://localhost/mock-url")
                    .build());
        });

        when(s3Service.completeUpload(any())).thenAnswer(invocation -> {
            S3ServiceDto.CompleteUploadRequest req = invocation.getArgument(0);
            return software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder()
                    .location("http://test-cdn.com/" + req.getKey())
                    .eTag("mock-etag")
                    .build();
        });
    }

    private void initUsers() {
        Map<String, Object> userA = AuthSteps.registerAndLogin("repost.a@test.com", "RepostA", "Password123!");
        tokenUserA = (String) userA.get("accessToken");
        userAId = ((Number) userA.get("userId")).longValue();

        Map<String, Object> userB = AuthSteps.registerAndLogin("repost.b@test.com", "RepostB", "Password123!");
        tokenUserB = (String) userB.get("accessToken");
        userBId = ((Number) userB.get("userId")).longValue();

        Map<String, Object> userC = AuthSteps.registerAndLogin("repost.c@test.com", "RepostC", "Password123!");
        tokenUserC = (String) userC.get("accessToken");
        userCId = ((Number) userC.get("userId")).longValue();

        FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);
    }

    private void initArchives() {
        publicArchiveId = ArchiveSteps.create(tokenUserA, "R_Public", "PUBLIC");
        restrictedArchiveId = ArchiveSteps.create(tokenUserA, "R_Restricted", "RESTRICTED");
        privateArchiveId = ArchiveSteps.create(tokenUserA, "R_Private", "PRIVATE");
    }

    private void initPosts() {
        postAId = PostSteps.create(tokenUserA, "Post A", "IDOL", null);

        Long fileId = FileSteps.uploadFile(tokenUserB);
        postBId = PostSteps.create(tokenUserB, "Post B", "ACTOR", fileId);

        postCId = PostSteps.create(tokenUserC, "Post C", "SPORT", null);
    }

    // ========================================================================================
    // [Category 1]. RepostTab Management
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 리포스트 탭 관리")
    class TabLifecycle {

        @BeforeEach
        void cleanUp() {
            repostRepository.deleteAll();
            repostTabRepository.deleteAll();
        }

        @Test @DisplayName("SCENE 1. 탭 생성")
        void createTab_Normal() {
            given().cookie("ATK", tokenUserA).post("/api/v1/repost/tabs/{archiveId}", publicArchiveId)
                    .then().statusCode(201)
                    .body("id", notNullValue())
                    .body("repostBookId", equalTo(publicArchiveId.intValue()));
        }

        @Test @DisplayName("SCENE 2. 탭 생성 - 이름 자동 증가")
        void createTab_AutoIncrementName() {
            given().cookie("ATK", tokenUserA).post("/api/v1/repost/tabs/{id}", publicArchiveId)
                    .then().statusCode(201).body("title", equalTo("1번째 탭"));
            given().cookie("ATK", tokenUserA).post("/api/v1/repost/tabs/{id}", publicArchiveId)
                    .then().statusCode(201).body("title", equalTo("2번째 탭"));
        }

        @Test @DisplayName("SCENE 3. 탭 수정")
        void updateTab() {
            Long tabId = RepostSteps.createTab(tokenUserA, publicArchiveId);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("title", "맛집"))
                    .patch("/api/v1/repost/tabs/{id}", tabId).then().statusCode(200).body("title", equalTo("맛집"));
        }

        @Test @DisplayName("SCENE 4. 탭 삭제")
        void deleteTab() {
            Long tabId = RepostSteps.createTab(tokenUserA, publicArchiveId);
            given().cookie("ATK", tokenUserA).delete("/api/v1/repost/tabs/{id}", tabId).then().statusCode(204);
            assertThat(repostTabRepository.existsById(tabId)).isFalse();
        }

        @Test @DisplayName("SCENE 5. 탭 생성 제한 (10개)")
        void createTab_Limit() {
            for(int i=0; i<10; i++) RepostSteps.createTab(tokenUserA, publicArchiveId);
            given().cookie("ATK", tokenUserA).post("/api/v1/repost/tabs/{id}", publicArchiveId)
                    .then().statusCode(500); // REPOST_TAB_LIMIT_EXCEED
        }

        @Test @DisplayName("SCENE 6. 예외 - 타인이 탭 생성")
        void tab_Forbidden() {
            given().cookie("ATK", tokenUserC).post("/api/v1/repost/tabs/{id}", publicArchiveId)
                    .then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 2]. Repost Lifecycle
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 리포스트 생명주기")
    class RepostLifecycle {
        private Long tabId;

        @BeforeEach
        void init() {
            repostRepository.deleteAll();
            repostTabRepository.deleteAll();
            tabId = RepostSteps.createTab(tokenUserA, publicArchiveId);
        }

        @Test @DisplayName("SCENE 7. 리포스트 생성 (썸네일 O)")
        void createRepost_WithThumb() {
            int repostId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("postId", postBId))
                    .post("/api/v1/repost/{tabId}", tabId).then().statusCode(201)
                    .body("thumbnailUrl", containsString("http"))
                    .extract().jsonPath().getInt("id");

            Repost repost = repostRepository.findById((long) repostId).orElseThrow();
            assertThat(repost.getTitle()).isEqualTo("Post B");
        }

        @Test @DisplayName("SCENE 8. 리포스트 생성 (썸네일 X)")
        void createRepost_NoThumb() {
            int repostId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("postId", postCId))
                    .post("/api/v1/repost/{tabId}", tabId).then().statusCode(201)
                    .body("thumbnailUrl", nullValue())
                    .extract().jsonPath().getInt("id");

            Repost repost = repostRepository.findById((long) repostId).orElseThrow();
            assertThat(repost.getThumbnailKey()).isNull();
        }

        @Test @DisplayName("SCENE 9. 중복 생성 방지")
        void createRepost_Duplicate() {
            RepostSteps.createRepost(tokenUserA, tabId, postBId);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("postId", postBId))
                    .post("/api/v1/repost/{tabId}", tabId).then().statusCode(409);
        }

        @Test @DisplayName("SCENE 10. 존재하지 않는 게시글")
        void createRepost_NotFound() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("postId", 99999))
                    .post("/api/v1/repost/{tabId}", tabId).then().statusCode(404);
        }

        @Test @DisplayName("SCENE 11. 리포스트 수정")
        void updateRepost() {
            Long repostId = RepostSteps.createRepost(tokenUserA, tabId, postBId);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("title", "New"))
                    .patch("/api/v1/repost/{id}", repostId).then().statusCode(200).body("title", equalTo("New"));
        }

        @Test @DisplayName("SCENE 12. 리포스트 삭제")
        void deleteRepost() {
            Long repostId = RepostSteps.createRepost(tokenUserA, tabId, postBId);
            given().cookie("ATK", tokenUserA).delete("/api/v1/repost/{id}", repostId).then().statusCode(204);
            assertThat(repostRepository.existsById(repostId)).isFalse();
        }

        @Test @DisplayName("SCENE 13. 타인 생성 시도")
        void createRepost_Forbidden() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("postId", postBId))
                    .post("/api/v1/repost/{tabId}", tabId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 3]. Bulk Delete via Tab
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 탭 삭제 시 리포스트 일괄 삭제")
    class TabDeleteCascade {

        @Test
        @DisplayName("SCENE 14. 탭 삭제 시 하위 리포스트 삭제 확인")
        void deleteTab_Cascade() {
            repostRepository.deleteAll();
            repostTabRepository.deleteAll();
            Long tabId = RepostSteps.createTab(tokenUserA, publicArchiveId);

            // Create temporary posts
            Long p1 = PostSteps.create(tokenUserA, "T1", "IDOL", null);
            Long p2 = PostSteps.create(tokenUserA, "T2", "IDOL", null);

            RepostSteps.createRepost(tokenUserA, tabId, p1);
            RepostSteps.createRepost(tokenUserA, tabId, p2);

            given().cookie("ATK", tokenUserA).delete("/api/v1/repost/tabs/{id}", tabId).then().statusCode(204);

            assertThat(repostTabRepository.existsById(tabId)).isFalse();
            assertThat(repostRepository.count()).isZero();
        }
    }

    // ========================================================================================
    // [Category 4]. Read List
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 리포스트 목록 조회")
    class ReadReposts {
        private Long tab1;

        @BeforeEach
        void setUpData() {
            repostRepository.deleteAll();
            repostTabRepository.deleteAll();

            tab1 = RepostSteps.createTab(tokenUserA, publicArchiveId);
            RepostSteps.createRepost(tokenUserA, tab1, postAId);
        }

        @Test @DisplayName("SCENE 15. PUBLIC 탭 조회")
        void readPublic() {
            given().cookie("ATK", tokenUserC).param("tabId", tab1)
                    .get("/api/v1/repost/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(1));
        }

        @Test @DisplayName("SCENE 16. Default Tab 조회")
        void readPublic_Default() {
            given().cookie("ATK", tokenUserC)
                    .get("/api/v1/repost/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("tabId", equalTo(tab1.intValue()));
        }

        @Test @DisplayName("SCENE 17. RESTRICTED - 친구 조회")
        void readRestricted_Friend() {
            given().cookie("ATK", tokenUserB)
                    .get("/api/v1/repost/{id}", restrictedArchiveId)
                    .then().statusCode(200);
        }

        @Test @DisplayName("SCENE 18. RESTRICTED - 타인 조회 실패")
        void readRestricted_Fail() {
            given().cookie("ATK", tokenUserC)
                    .get("/api/v1/repost/{id}", restrictedArchiveId)
                    .then().statusCode(403);
        }

        @Test @DisplayName("SCENE 19. PRIVATE - 타인 실패")
        void readPrivate_Fail() {
            given().cookie("ATK", tokenUserB)
                    .get("/api/v1/repost/{id}", privateArchiveId)
                    .then().statusCode(403);
        }

        @Test @DisplayName("SCENE 20. 존재하지 않는 탭 ID")
        void read_TabNotFound() {
            given().cookie("ATK", tokenUserA).param("tabId", 99999)
                    .get("/api/v1/repost/{id}", publicArchiveId)
                    .then().statusCode(404);
        }

        @Test @DisplayName("SCENE 21. Cross Archive 탭 요청")
        void read_CrossArchive() {
            Long otherTab = RepostSteps.createTab(tokenUserA, restrictedArchiveId);

            given().cookie("ATK", tokenUserA).param("tabId", otherTab)
                    .get("/api/v1/repost/{id}", publicArchiveId)
                    .then().statusCode(404); // REPOST_TAB_NOT_FOUND
        }
    }

    // --- Helpers ---
    // [중요] AuthSteps는 원래의 순수한 형태로 복귀 (NPE/Conflict가 근본적으로 해결되었으므로)
    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            try { RestAssured.given().delete(ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages"); } catch (Exception x) {}
            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(500); } catch (InterruptedException i) {}
            String code = getVerificationCode(email);
            given().contentType(ContentType.JSON).body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify").then().statusCode(200);
            int id = given().contentType(ContentType.JSON).body(Map.of("email", email, "nickname", nickname, "password", password))
                    .post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");
            Response l = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                    .post("/api/v1/auth/login");
            return Map.of("accessToken", l.getCookie("ATK"), "userId", id);
        }
        static String getVerificationCode(String email) {
            for (int i = 0; i < 20; i++) {
                try {
                    Response res = RestAssured.given().get(ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages");
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

    // ArchiveSteps, FriendSteps, FileSteps, PostSteps, RepostSteps는 기존과 동일
    static class ArchiveSteps {
        static Long create(String t, String n, String v) {
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("title", n, "visibility", v)).post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }
    static class FriendSteps {
        static void makeFriendDirectly(UserRepository ur, FriendMapRepository fr, Long a, Long b) {
            User ua = ur.findById(a).get(); User ub = ur.findById(b).get();
            fr.save(FriendMap.builder().user(ua).friend(ub).requestedBy(ua).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
            fr.save(FriendMap.builder().user(ub).friend(ua).requestedBy(ua).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
        }
    }
    static class FileSteps {
        static Long uploadFile(String t) {
            Response i = given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("originalFileName", "f.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT")).post("/api/v1/files/multipart/initiate");
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("key", i.jsonPath().getString("key"), "uploadId", i.jsonPath().getString("uploadId"), "parts", List.of(Map.of("partNumber", 1, "etag", "e")), "originalFileName", "f.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0)).post("/api/v1/files/multipart/complete").then().statusCode(200).extract().jsonPath().getLong("fileId");
        }
    }
    static class PostSteps {
        static Long create(String t, String title, String cat, Long fid) {
            Map<String, Object> body = new HashMap<>(); body.put("title", title); body.put("content", "C"); body.put("category", cat);
            if(fid != null) body.put("files", List.of(Map.of("fileId", fid, "mediaRole", "PREVIEW", "sequence", 0)));
            else body.put("files", List.of());
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(body).post("/api/v1/posts").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }
    static class RepostSteps {
        static Long createTab(String t, Long aid) {
            return given().cookie("ATK", t).post("/api/v1/repost/tabs/{id}", aid).then().statusCode(201).extract().jsonPath().getLong("id");
        }
        static Long createRepost(String t, Long tid, Long pid) {
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("postId", pid)).post("/api/v1/repost/{id}", tid).then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }
}