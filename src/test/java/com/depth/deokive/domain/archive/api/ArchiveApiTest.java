package com.depth.deokive.domain.archive.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.post.repository.RepostBookRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

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

@DisplayName("Archive API 통합 테스트 시나리오")
class ArchiveApiTest extends ApiTestSupport {

    // --- Repositories ---
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private DiaryBookRepository diaryBookRepository;
    @Autowired private GalleryBookRepository galleryBookRepository;
    @Autowired private TicketBookRepository ticketBookRepository;
    @Autowired private RepostBookRepository repostBookRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;
    @Autowired private FileRepository fileRepository;

    // --- Actors (Token) ---
    private static String tokenUserA; // Me (Owner)
    private static String tokenUserB; // Friend
    private static String tokenUserC; // Stranger

    // --- Shared Data ---
    private static Long userAId;
    private static Long userBId;
    private static Long bannerImageId;

    @BeforeEach
    void setUp() {
        // [S3 Mocking]
        mockS3Service();

        // [Global Setup] 최초 1회만 실행
        if (tokenUserA == null) {
            // 1. 유저 생성 및 로그인
            Map<String, Object> userA = AuthSteps.registerAndLogin("archive.a@test.com", "ArchiveA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("archive.b@test.com", "ArchiveB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("archive.c@test.com", "ArchiveC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");

            // 2. 친구 관계 맺기 (UserA <-> UserB)
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // 3. 파일 업로드
            bannerImageId = FileSteps.uploadFile(tokenUserA);
        }
    }

    private void mockS3Service() {
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
    }

    // ========================================================================================
    // [Category 1]. Create Archive
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 아카이브 생성")
    class CreateArchive {

        @Test
        @DisplayName("SCENE 1. 정상 생성 - PUBLIC + 배너 이미지")
        void createArchive_Public_WithBanner() {
            Map<String, Object> request = new HashMap<>();
            request.put("title", "테스트 아카이브");
            request.put("visibility", "PUBLIC");
            request.put("bannerImageId", bannerImageId);

            int archiveId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.CREATED.value())
                    .body("id", notNullValue())
                    .body("title", equalTo("테스트 아카이브"))
                    .body("visibility", equalTo("PUBLIC"))
                    .body("badge", equalTo("NEWBIE"))
                    .body("isOwner", equalTo(true))
                    .body("bannerUrl", startsWith("http"))
                    .extract().jsonPath().getInt("id");

            Archive archive = archiveRepository.findById((long) archiveId).orElseThrow();
            assertThat(archive.getTitle()).isEqualTo("테스트 아카이브");
            assertThat(archive.getBannerFile().getId()).isEqualTo(bannerImageId);
            assertThat(diaryBookRepository.existsById((long) archiveId)).isTrue();
        }

        @Test
        @DisplayName("SCENE 2. 정상 생성 - RESTRICTED + 배너 없음")
        void createArchive_Restricted_NoBanner() {
            Map<String, Object> request = Map.of("title", "제한 아카이브", "visibility", "RESTRICTED");

            int archiveId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(request)
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.CREATED.value())
                    .body("visibility", equalTo("RESTRICTED"))
                    .body("bannerUrl", nullValue())
                    .extract().jsonPath().getInt("id");

            Archive archive = archiveRepository.findById((long) archiveId).orElseThrow();
            assertThat(archive.getBannerFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 3. 정상 생성 - PRIVATE")
        void createArchive_Private() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "비공개", "visibility", "PRIVATE"))
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.CREATED.value())
                    .body("visibility", equalTo("PRIVATE"));
        }

        @Test
        @DisplayName("SCENE 4. 예외 - 필수값 누락")
        void createArchive_Invalid() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "", "visibility", "PUBLIC"))
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 5. 예외 - IDOR (타인 파일)")
        void createArchive_IDOR() {
            Long userCFileId = FileSteps.uploadFile(tokenUserC);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack", "visibility", "PUBLIC", "bannerImageId", userCFileId))
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.FORBIDDEN.value());
        }

        @Test
        @DisplayName("SCENE 6. 예외 - 파일 없음")
        void createArchive_FileNotFound() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Fail", "visibility", "PUBLIC", "bannerImageId", 999999))
                    .when().post("/api/v1/archives")
                    .then().statusCode(HttpStatus.NOT_FOUND.value());
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail
    // ========================================================================================
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

        @Test @DisplayName("SCENE 7. PUBLIC - 본인")
        void readPublic_Owner() {
            given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", publicId)
                    .then().statusCode(200).body("isOwner", equalTo(true));
        }

        @Test @DisplayName("SCENE 8. PUBLIC - 친구")
        void readPublic_Friend() {
            given().cookie("ATK", tokenUserB).get("/api/v1/archives/{id}", publicId)
                    .then().statusCode(200).body("isOwner", equalTo(false));
        }

        @Test @DisplayName("SCENE 9. PUBLIC - 타인")
        void readPublic_Stranger() {
            given().cookie("ATK", tokenUserC).get("/api/v1/archives/{id}", publicId)
                    .then().statusCode(200);
        }

        @Test @DisplayName("SCENE 10. PUBLIC - 비회원")
        void readPublic_Anon() {
            given().get("/api/v1/archives/{id}", publicId).then().statusCode(200);
        }

        @Test @DisplayName("SCENE 11. RESTRICTED - 본인")
        void readRestricted_Owner() {
            given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", restrictedId).then().statusCode(200);
        }

        @Test @DisplayName("SCENE 12. RESTRICTED - 친구")
        void readRestricted_Friend() {
            given().cookie("ATK", tokenUserB).get("/api/v1/archives/{id}", restrictedId).then().statusCode(200);
        }

        @Test @DisplayName("SCENE 13. RESTRICTED - 타인(실패)")
        void readRestricted_Stranger() {
            given().cookie("ATK", tokenUserC).get("/api/v1/archives/{id}", restrictedId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 14. RESTRICTED - 비회원(실패)")
        void readRestricted_Anon() {
            given().get("/api/v1/archives/{id}", restrictedId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 15. PRIVATE - 본인")
        void readPrivate_Owner() {
            given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", privateId).then().statusCode(200);
        }

        @Test @DisplayName("SCENE 16~17. PRIVATE - 타인/친구(실패)")
        void readPrivate_Others() {
            given().cookie("ATK", tokenUserB).get("/api/v1/archives/{id}", privateId).then().statusCode(403);
            given().cookie("ATK", tokenUserC).get("/api/v1/archives/{id}", privateId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 18. 존재하지 않는 아카이브")
        void read_NotFound() {
            given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", 99999).then().statusCode(404);
        }

        @Test @DisplayName("SCENE 19. 조회수 증가")
        void checkViewCount() {
            long initial = given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", publicId).jsonPath().getLong("viewCount");
            given().cookie("ATK", tokenUserC).get("/api/v1/archives/{id}", publicId); // +1
            given().cookie("ATK", tokenUserA).get("/api/v1/archives/{id}", publicId)
                    .then().body("viewCount", equalTo((int) initial + 2)); // Owner read also +1
        }
    }

    // ========================================================================================
    // [Category 3]. Update Archive
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 아카이브 수정")
    class UpdateArchive {
        private Long archiveId;
        private Long file1Id;

        @BeforeEach
        void init() {
            file1Id = FileSteps.uploadFile(tokenUserA);
            archiveId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Origin", "visibility", "PUBLIC", "bannerImageId", file1Id))
                    .post("/api/v1/archives").jsonPath().getLong("id");
        }

        @Test @DisplayName("SCENE 20. 정상 수정 - 정보")
        void update_Info() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Updated", "visibility", "PRIVATE"))
                    .when().patch("/api/v1/archives/{id}", archiveId)
                    .then().statusCode(200)
                    .body("title", equalTo("Updated"))
                    .body("visibility", equalTo("PRIVATE"));
        }

        @Test @DisplayName("SCENE 21. 정상 수정 - 배너 교체")
        void update_BannerReplace() {
            Long file2Id = FileSteps.uploadFile(tokenUserA);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("bannerImageId", file2Id))
                    .when().patch("/api/v1/archives/{id}", archiveId)
                    .then().statusCode(200).body("bannerUrl", containsString("http"));
        }

        @Test @DisplayName("SCENE 22. 정상 수정 - 배너 삭제")
        void update_BannerDelete() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("bannerImageId", -1))
                    .when().patch("/api/v1/archives/{id}", archiveId)
                    .then().statusCode(200).body("bannerUrl", nullValue());
        }

        @Test @DisplayName("SCENE 23~24. 타인/친구 수정 시도(실패)")
        void update_Forbidden() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("title", "Hack")).patch("/api/v1/archives/{id}", archiveId).then().statusCode(403);
            given().cookie("ATK", tokenUserB).contentType(ContentType.JSON).body(Map.of("title", "Hack")).patch("/api/v1/archives/{id}", archiveId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 25. IDOR")
        void update_IDOR() {
            Long userCFile = FileSteps.uploadFile(tokenUserC);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("bannerImageId", userCFile))
                    .patch("/api/v1/archives/{id}", archiveId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Archive
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 아카이브 삭제")
    class DeleteArchive {
        private Long archiveId;

        @BeforeEach
        void init() {
            archiveId = ArchiveSteps.create(tokenUserA, "Del", "PUBLIC");
        }

        @Test @DisplayName("SCENE 26. 정상 삭제")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/archives/{id}", archiveId).then().statusCode(204);
            assertThat(archiveRepository.existsById(archiveId)).isFalse();
            assertThat(diaryBookRepository.existsById(archiveId)).isFalse();
        }

        @Test @DisplayName("SCENE 27. 타인 삭제 시도")
        void delete_Forbidden() {
            given().cookie("ATK", tokenUserC).delete("/api/v1/archives/{id}", archiveId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 28. 존재하지 않는 삭제")
        void delete_NotFound() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/archives/{id}", 99999).then().statusCode(404);
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

            // UserB: Restricted(1), Public(1)
            ArchiveSteps.create(tokenUserB, "B_Res1", "RESTRICTED");
            ArchiveSteps.create(tokenUserB, "B_Pub1", "PUBLIC");

            // UserC: Public(1)
            ArchiveSteps.create(tokenUserC, "C_Pub1", "PUBLIC");
        }

        @Test
        @DisplayName("SCENE 29. 전역 피드 (PUBLIC Only)")
        void globalFeed() {
            given().param("page", 0).param("size", 20).param("sort", "createdAt").param("direction", "DESC")
                    .when().get("/api/v1/archives/feed")
                    .then().statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")))
                    .body("content.size()", greaterThanOrEqualTo(4)); // A(2)+B(1)+C(1)
        }

        @Test
        @DisplayName("SCENE 30. 유저별 - 본인 (전체)")
        void userList_Owner() {
            given().cookie("ATK", tokenUserA).param("size", 20)
                    .when().get("/api/v1/archives/users/{userId}", userAId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'PRIVATE' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 31. 유저별 - 친구가 내꺼 조회 (Public + Restricted)")
        void userList_Friend_ViewMe() {
            // UserB(Friend)가 UserA 조회 -> UserA는 Restricted가 없음.
            // Setup 추가: UserA에 Restricted 생성
            ArchiveSteps.create(tokenUserA, "A_Res1", "RESTRICTED");

            given().cookie("ATK", tokenUserB).param("size", 20)
                    .when().get("/api/v1/archives/users/{userId}", userAId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", notNullValue())
                    .body("content.find { it.visibility == 'PRIVATE' }", nullValue());
        }

        @Test
        @DisplayName("SCENE 32. 유저별 - 내가 친구꺼 조회 (Public + Restricted)")
        void userList_Friend_ViewFriend() {
            // UserA(Me)가 UserB 조회 -> UserB는 Public, Restricted 보유
            given().cookie("ATK", tokenUserA).param("size", 20)
                    .when().get("/api/v1/archives/users/{userId}", userBId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", notNullValue())
                    .body("content.find { it.visibility == 'PUBLIC' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 33. 유저별 - 타인 조회 (Public Only)")
        void userList_Stranger() {
            // UserC가 UserA 조회 -> Private/Restricted(SCENE 31에서 생성됨) 숨김
            given().cookie("ATK", tokenUserC).param("size", 20)
                    .when().get("/api/v1/archives/users/{userId}", userAId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'PRIVATE' }", nullValue())
                    .body("content.find { it.visibility == 'RESTRICTED' }", nullValue())
                    .body("content.find { it.visibility == 'PUBLIC' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 34. 유저별 - 타인 조회 (Restricted 숨김 확인)")
        void userList_Stranger_HideRestricted() {
            // UserC가 UserB 조회 (Restricted 보유) -> Public만 보여야 함
            given().cookie("ATK", tokenUserC).param("size", 20)
                    .when().get("/api/v1/archives/users/{userId}", userBId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", nullValue())
                    .body("content.find { it.visibility == 'PUBLIC' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 35. 페이지네이션")
        void pagination() {
            given().cookie("ATK", tokenUserA).param("page", 0).param("size", 1)
                    .when().get("/api/v1/archives/users/{userId}", userAId)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(1))
                    .body("page.hasNext", equalTo(true));
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================

    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
            try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}

            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

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
            System.out.println("🔍 MailHog Searching: " + email);
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
            throw new RuntimeException("MailHog Code Fail: " + email);
        }
    }

    static class ArchiveSteps {
        static Long create(String token, String title, String visibility) {
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("title", title, "visibility", visibility))
                    .post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class FileSteps {
        static Long uploadFile(String token) {
            Response init = given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("originalFileName", "t.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT"))
                    .post("/api/v1/files/multipart/initiate");
            String uploadId = init.jsonPath().getString("uploadId");
            String key = init.jsonPath().getString("key");

            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("key", key, "uploadId", uploadId, "parts", List.of(Map.of("partNumber", 1, "etag", "e")),
                            "originalFileName", "t.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0))
                    .post("/api/v1/files/multipart/complete").then().statusCode(200).extract().jsonPath().getLong("fileId");
        }
    }

    static class FriendSteps {
        static void makeFriendDirectly(UserRepository uRepo, FriendMapRepository fRepo, Long uA, Long uB) {
            User A = uRepo.findById(uA).orElseThrow();
            User B = uRepo.findById(uB).orElseThrow();
            fRepo.save(FriendMap.builder().user(A).friend(B).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
            fRepo.save(FriendMap.builder().user(B).friend(A).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
        }
    }
}