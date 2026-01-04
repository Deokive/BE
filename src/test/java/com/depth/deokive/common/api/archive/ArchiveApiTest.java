package com.depth.deokive.common.api.archive;

import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.s3.service.S3Service;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Archive API 통합 테스트 시나리오 (E2E)")
class ArchiveApiTest {

    @LocalServerPort private int port;

    @MockitoBean private S3Service s3Service; // 외부 서비스 한하여 MockitoBean을 사용한다.

    // 데이터 셋업을 위한 Repository
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository; // 아직 친구 API가 구현되지 않았으므로 Repo로 생성

    // --- Actors (Token) ---
    private static String tokenUserA; // Me (Owner)
    private static String tokenUserB; // Friend
    private static String tokenUserC; // Stranger

    // --- Shared Data ---
    private static Long userAId;
    private static Long userBId;
    private static Long userCId;
    private static Long bannerImageId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> {
            String uniqueKey = "files/" + UUID.randomUUID() + "__test.jpg";
            return S3ServiceDto.UploadInitiateResponse.builder()
                    .uploadId("mock-upload-id")
                    .key(uniqueKey)
                    .contentType("image/jpeg")
                    .build();
        });

        when(s3Service.calculatePartCount(any())).thenReturn(1);

        when(s3Service.generatePartPresignedUrls(any())).thenAnswer(invocation -> {
            S3ServiceDto.PartPresignedUrlRequest req = invocation.getArgument(0);
            return List.of(S3ServiceDto.PartPresignedUrlResponse.builder()
                    .partNumber(1)
                    .presignedUrl("http://localhost/mock-s3-url/" + req.getKey())
                    .contentLength(100L)
                    .build());
        });

        when(s3Service.completeUpload(any())).thenAnswer(invocation -> {
            S3ServiceDto.CompleteUploadRequest req = invocation.getArgument(0);
            return software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder()
                    .location("http://test-cdn.com/" + req.getKey())
                    .eTag("mock-etag")
                    .build();
        });

        if (tokenUserA == null) {
            // 1. 유저 생성 및 로그인
            Map<String, Object> userA = AuthSteps.registerAndLogin("usera@test.com", "UserA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("userb@test.com", "UserB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("userc@test.com", "UserC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");
            userCId = ((Number) userC.get("userId")).longValue();

            // 2. 친구 관계 맺기 (UserA <-> UserB)
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // 3. 파일 업로드 (UserA가 배너 이미지 업로드)
            bannerImageId = FileSteps.uploadFile(tokenUserA);
        }
    }

    // ========================================================================================
    // [Category 1]. Create Archive (POST /api/v1/archives)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 아카이브 생성")
    class CreateArchive {

        @Test
        @DisplayName("SCENE 1. 정상 생성 - PUBLIC + 배너 이미지 포함")
        void createArchive_Public_WithBanner() {
            String title = "테스트 아카이브";

            int archiveId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", title,
                            "visibility", "PUBLIC",
                            "bannerImageId", bannerImageId
                    ))
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("id", notNullValue())
                    .body("title", equalTo(title))
                    .body("visibility", equalTo("PUBLIC"))
                    .body("badge", equalTo("NEWBIE"))
                    .body("owner", equalTo(true))
                    .body("viewCount", equalTo(0))
                    .body("likeCount", equalTo(0))
                    .body("bannerUrl", startsWith("http"))
                    .extract().jsonPath().getInt("id");

            // DB 검증
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", archiveId)
                    .then().statusCode(200)
                    .body("title", equalTo(title));

            // TODO: 아카이브 생성 시, 자동으로 Books가 생성되는지 점검
        }

        @Test
        @DisplayName("SCENE 2. 정상 생성 - RESTRICTED + 배너 없음")
        void createArchive_Restricted_NoBanner() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(new HashMap<String, Object>() {{
                        put("title", "제한 아카이브");
                        put("visibility", "RESTRICTED");
                        put("bannerImageId", null);
                    }})
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("title", equalTo("제한 아카이브"))
                    .body("visibility", equalTo("RESTRICTED"))
                    .body("bannerUrl", nullValue())
                    .body("owner", equalTo(true));
        }

        @Test
        @DisplayName("SCENE 3. 정상 생성 - PRIVATE")
        void createArchive_Private() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "비공개 아카이브",
                            "visibility", "PRIVATE"
                    ))
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.CREATED.value());
        }

        @Test
        @DisplayName("SCENE 4. 예외 - 필수값 누락 (제목 없음)")
        void createArchive_Invalid_NoTitle() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "",
                            "visibility", "PUBLIC"
                    ))
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 5. 예외 - IDOR (타인의 파일로 배너 설정 시도)")
        void createArchive_IDOR() {
            // UserC가 파일 업로드 (새로운 Unique Key 생성됨)
            Long userCFileId = FileSteps.uploadFile(tokenUserC);

            // UserA가 UserC의 파일로 아카이브 생성 시도
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "Hacked Archive",
                            "visibility", "PUBLIC",
                            "bannerImageId", userCFileId
                    ))
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 6. 예외 - 존재하지 않는 파일 ID")
        void createArchive_FileNotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "Fail Archive",
                            "visibility", "PUBLIC",
                            "bannerImageId", 999999L
                    ))
                    .when()
                    .post("/api/v1/archives")
                    .then()
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("FILE_NOT_FOUND"));
        }
    }

    // ========================================================================================
    // [Category 3]. Update Archive
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 아카이브 수정")
    class UpdateArchive {
        private Long archiveId;

        @BeforeEach
        void init() {
            archiveId = ArchiveSteps.create(tokenUserA, "Original", "PUBLIC");
        }

        @Test
        @DisplayName("SCENE 20. 정상 수정 - 제목 및 공개범위 변경")
        void update_TitleAndVisibility() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "수정된 제목", "visibility", "PRIVATE"))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("수정된 제목"))
                    .body("visibility", equalTo("PRIVATE"));
        }

        @Test
        @DisplayName("SCENE 21. 정상 수정 - 배너 이미지 교체")
        void update_BannerReplace() {
            Long newFileId = FileSteps.uploadFile(tokenUserA);

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("bannerImageId", newFileId))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(200)
                    .body("bannerUrl", containsString("http://test-cdn.com/files/"))
                    .body("bannerUrl", endsWith(".jpg"));
        }

        @Test
        @DisplayName("SCENE 22. 정상 수정 - 배너 이미지 삭제")
        void update_BannerDelete() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("bannerImageId", -1))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(200)
                    .body("bannerUrl", nullValue());
        }

        @Test
        @DisplayName("SCENE 23. 예외 - 타인(UserC)이 수정 시도")
        void update_Forbidden_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack"))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 24. 예외 - 친구(UserB)가 수정 시도")
        void update_Forbidden_Friend() {
            given()
                    .cookie("ATK", tokenUserB)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Friend Hack"))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 25. 예외 - IDOR (타인의 파일로 배너 교체 시도)")
        void update_IDOR() {
            Long userCFile = FileSteps.uploadFile(tokenUserC);

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("bannerImageId", userCFile))
                    .when()
                    .patch("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(403);
        }
    }

    @Nested
    @DisplayName("[Category 2] 아카이브 상세 조회")
    class ReadArchive {

        private Long publicId;
        private Long restrictedId;
        private Long privateId;

        @BeforeEach
        void initArchives() {
            publicId = ArchiveSteps.create(tokenUserA, "Public", "PUBLIC");
            restrictedId = ArchiveSteps.create(tokenUserA, "Restricted", "RESTRICTED");
            privateId = ArchiveSteps.create(tokenUserA, "Private", "PRIVATE");
        }

        @Test
        @DisplayName("SCENE 7. PUBLIC 조회 - 본인(UserA)")
        void readPublic_Owner() {
            given()
                    .cookie("ATK", tokenUserA)
                    .when()
                    .get("/api/v1/archives/{id}", publicId)
                    .then()
                    .statusCode(200)
                    .body("owner", equalTo(true));
        }

        @Test
        @DisplayName("SCENE 8. PUBLIC 조회 - 친구(UserB)")
        void readPublic_Friend() {
            given()
                    .cookie("ATK", tokenUserB)
                    .when()
                    .get("/api/v1/archives/{id}", publicId)
                    .then()
                    .statusCode(200)
                    .body("owner", equalTo(false));
        }

        @Test
        @DisplayName("SCENE 9. PUBLIC 조회 - 타인(UserC)")
        void readPublic_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .when()
                    .get("/api/v1/archives/{id}", publicId)
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("SCENE 10. PUBLIC 조회 - 비회원(No Token)")
        void readPublic_Anonymous() {
            given()
                    .when()
                    .get("/api/v1/archives/{id}", publicId)
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("SCENE 11. RESTRICTED 조회 - 본인(UserA)")
        void readRestricted_Owner() {
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", restrictedId)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("SCENE 12. RESTRICTED 조회 - 친구(UserB)")
        void readRestricted_Friend() {
            given().cookie("ATK", tokenUserB)
                    .get("/api/v1/archives/{id}", restrictedId)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("SCENE 13. RESTRICTED 조회 - 타인(UserC)")
        void readRestricted_Stranger() {
            given().cookie("ATK", tokenUserC)
                    .get("/api/v1/archives/{id}", restrictedId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 14. RESTRICTED 조회 - 비회원")
        void readRestricted_Anonymous() {
            given()
                    .get("/api/v1/archives/{id}", restrictedId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 15. PRIVATE 조회 - 본인(UserA)")
        void readPrivate_Owner() {
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", privateId)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("SCENE 16. PRIVATE 조회 - 친구(UserB)")
        void readPrivate_Friend() {
            given().cookie("ATK", tokenUserB)
                    .get("/api/v1/archives/{id}", privateId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 17. PRIVATE 조회 - 타인(UserC)")
        void readPrivate_Stranger() {
            given().cookie("ATK", tokenUserC)
                    .get("/api/v1/archives/{id}", privateId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 18. 존재하지 않는 아카이브 조회")
        void read_NotFound() {
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", 999999L)
                    .then()
                    .statusCode(404)
                    .body("error", equalTo("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 19. 조회수 증가 확인")
        void checkViewCount() {
            long initialViews = given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", publicId)
                    .jsonPath().getLong("viewCount");

            given().cookie("ATK", tokenUserC).get("/api/v1/archives/{id}", publicId);

            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", publicId)
                    .then()
                    .body("viewCount", equalTo((int) initialViews + 2));
        }
    }

    @Nested
    @DisplayName("[Category 4] 아카이브 삭제")
    class DeleteArchive {
        private Long archiveId;

        @BeforeEach
        void init() {
            archiveId = ArchiveSteps.create(tokenUserA, "DelTarget", "PUBLIC");
        }

        @Test
        @DisplayName("SCENE 26. 정상 삭제 - 본인 요청")
        void delete_Normal() {
            given()
                    .cookie("ATK", tokenUserA)
                    .when()
                    .delete("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(204);

            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/archives/{id}", archiveId)
                    .then().statusCode(404);

            // TODO: 아카이브에 종속된 도메인들도 일괄 삭제 되었는지 점검 (아카이브 생성 시, Books가 자동 생성되므로)
        }

        @Test
        @DisplayName("SCENE 27. 예외 - 타인(UserC)이 삭제 시도")
        void delete_Forbidden() {
            given()
                    .cookie("ATK", tokenUserC)
                    .when()
                    .delete("/api/v1/archives/{id}", archiveId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 28. 예외 - 존재하지 않는 아카이브 삭제")
        void delete_NotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .when()
                    .delete("/api/v1/archives/{id}", 999999L)
                    .then()
                    .statusCode(404);
        }
    }


    // ========================================================================================
    // [Category 5]. Feed & List (Pagination)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 피드 및 목록 조회")
    class FeedAndList {
        @BeforeEach
        void setUpFeed() {
            // UserA: Public(2), Private(1)
            ArchiveSteps.create(tokenUserA, "A_Pub1", "PUBLIC");
            ArchiveSteps.create(tokenUserA, "A_Pub2", "PUBLIC");
            ArchiveSteps.create(tokenUserA, "A_Pri1", "PRIVATE");

            // UserB: Restricted(1)
            ArchiveSteps.create(tokenUserB, "B_Res1", "RESTRICTED");

            // UserC: Public(1)
            ArchiveSteps.create(tokenUserC, "C_Pub1", "PUBLIC");
        }

        @Test
        @DisplayName("SCENE 29. 전역 피드 조회 - PUBLIC만 노출")
        void globalFeed() {
            given()
                    .param("page", 0)
                    .param("size", 20)
                    .param("sort", "createdAt")
                    .param("direction", "DESC")
                    .when()
                    .get("/api/v1/archives/feed")
                    .then()
                    .statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test
        @DisplayName("SCENE 30. 유저별 조회 - 본인 (전체 노출)")
        void userList_Owner() {
            given()
                    .cookie("ATK", tokenUserA)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/archives/users/{userId}", userAId)
                    .then()
                    .statusCode(200)
                    // setUp()이 매번 돌아서 데이터가 누적되므로 '개수' 검증보다는 '존재 여부' 검증으로 변경
                    .body("content.size()", greaterThanOrEqualTo(3))
                    .body("content.find { it.visibility == 'PRIVATE' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 31. 유저별 조회 - 친구 (Public + Restricted)")
        void userList_Friend() {
            given()
                    .cookie("ATK", tokenUserA) // UserA가 UserB(Restricted 보유) 조회
                    .param("size", 20)
                    .when()
                    .get("/api/v1/archives/users/{userId}", userBId)
                    .then()
                    .statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 33. 유저별 조회 - 타인 (Public only)")
        void userList_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/archives/users/{userId}", userAId)
                    .then()
                    .statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test
        @DisplayName("SCENE 34. 유저별 조회 - 타인 (Restricted 숨김)")
        void userList_Stranger_HideRestricted() {
            given()
                    .cookie("ATK", tokenUserC)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/archives/users/{userId}", userBId)
                    .then()
                    .statusCode(200)
                    // UserB는 Restricted만 만들었으므로 Public은 0개여야 함
                    // 단, 이전 테스트 데이터가 남아있을 수 있으므로 "Restricted가 포함되지 않음"을 검증
                    .body("content.find { it.visibility == 'RESTRICTED' }", nullValue());
        }

        @Test
        @DisplayName("SCENE 35. 페이지네이션 및 정렬")
        void pagination() {
            given()
                    .cookie("ATK", tokenUserA)
                    .param("page", 0)
                    .param("size", 2)
                    .param("sort", "createdAt")
                    .when()
                    .get("/api/v1/archives/users/{userId}", userAId)
                    .then()
                    .statusCode(200)
                    .body("content.size()", equalTo(2))
                    .body("page.hasNext", equalTo(true));
        }
    }

    // ========================================================================================
    // Helper Classes
    // ========================================================================================

    static class AuthSteps {
        private static final String MAILHOG_API = "http://localhost:8025/api/v2/messages";

        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            given().param("email", email)
                    .post("/api/v1/auth/email/send")
                    .then().statusCode(202);

            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            String code = getVerificationCode(email);

            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify")
                    .then().statusCode(200);

            int userId = given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "nickname", nickname, "password", password))
                    .post("/api/v1/auth/register")
                    .then().statusCode(200)
                    .extract().jsonPath().getInt("id");

            Response loginRes = given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", password))
                    .post("/api/v1/auth/login");

            loginRes.then().statusCode(200);

            return Map.of("accessToken", loginRes.getCookie("ATK"), "userId", userId);
        }

        private static String getVerificationCode(String email) {
            for (int i = 0; i < 20; i++) {
                try {
                    Response res = RestAssured.given().get(MAILHOG_API);
                    List<Map<String, Object>> messages = res.jsonPath().getList("items");

                    if (messages != null) {
                        for (Map<String, Object> msg : messages) {
                            Map<String, Object> content = (Map<String, Object>) msg.get("Content");
                            String body = (String) content.get("Body");
                            if (body.contains(email) || msg.toString().contains(email)) {
                                Matcher m = Pattern.compile("\\d{6}").matcher(body);
                                if (m.find()) return m.group();
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("MailHog 인증 코드 파싱 실패: " + email);
        }
    }

    static class ArchiveSteps {
        static Long create(String token, String title, String visibility) {
            return given()
                    .cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", title, "visibility", visibility))
                    .post("/api/v1/archives")
                    .then().statusCode(201)
                    .extract().jsonPath().getLong("id");
        }
    }

    static class FileSteps {
        static Long uploadFile(String token) {
            Response initRes = given().cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "originalFileName", "test.jpg",
                            "mimeType", "image/jpeg",
                            "fileSize", 100,
                            "mediaRole", "CONTENT"
                    ))
                    .post("/api/v1/files/multipart/initiate")
                    .then().statusCode(200).extract().response();

            String uploadId = initRes.jsonPath().getString("uploadId");
            String key = initRes.jsonPath().getString("key");

            return given().cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "key", key,
                            "uploadId", uploadId,
                            "parts", List.of(Map.of("partNumber", 1, "etag", "mock-etag")),
                            "originalFileName", "test.jpg",
                            "fileSize", 100,
                            "mimeType", "image/jpeg",
                            "mediaRole", "CONTENT",
                            "sequence", 0
                    ))
                    .post("/api/v1/files/multipart/complete")
                    .then().statusCode(200)
                    .extract().jsonPath().getLong("fileId");
        }
    }

    static class FriendSteps {
        static void makeFriendDirectly(
                UserRepository userRepo,
                FriendMapRepository friendRepo,
                Long userA, Long userB
        ) {
            User uA = userRepo.findById(userA).orElseThrow();
            User uB = userRepo.findById(userB).orElseThrow();

            friendRepo.save(com.depth.deokive.domain.friend.entity.FriendMap.builder()
                    .user(uA).friend(uB).requestedBy(uA)
                    .friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED)
                    .acceptedAt(java.time.LocalDateTime.now())
                    .build());

            friendRepo.save(com.depth.deokive.domain.friend.entity.FriendMap.builder()
                    .user(uB).friend(uA).requestedBy(uA)
                    .friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED)
                    .acceptedAt(java.time.LocalDateTime.now())
                    .build());
        }
    }
}