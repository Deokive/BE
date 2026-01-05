package com.depth.deokive.common.api.gallery;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.entity.Gallery;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.s3.service.S3Service;
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
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Gallery API 통합 테스트 시나리오 (E2E)")
class GalleryApiTest extends ApiTestSupport {

    // --- Repositories ---
    @Autowired private GalleryRepository galleryRepository;
    @Autowired private GalleryBookRepository galleryBookRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;

    // --- Actors (Token) ---
    private static String tokenUserA; // Me (Owner)
    private static String tokenUserB; // Friend
    private static String tokenUserC; // Stranger

    // --- Shared Data ---
    private static Long userAId;
    private static Long userBId;
    private static Long userCId;

    // --- Fixtures for UserA (Static ID reuse causes conflict -> Re-create per test if needed or clean up) ---
    // Instead of static, we'll create fresh archives/files in each nested class or use unique files.
    // However, Archive IDs can be reused if we clear gallery data.
    private static Long publicArchiveId;
    private static Long restrictedArchiveId;
    private static Long privateArchiveId;

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

        // [Global Setup] 최초 1회 실행
        if (tokenUserA == null) {
            // 1. Users Setup
            Map<String, Object> userA = AuthSteps.registerAndLogin("gallery.a@test.com", "GalleryA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("gallery.b@test.com", "GalleryB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("gallery.c@test.com", "GalleryC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");
            userCId = ((Number) userC.get("userId")).longValue();

            // 2. Friend (A-B)
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // 3. Archives (For UserA) - Created ONCE
            publicArchiveId = ArchiveSteps.create(tokenUserA, "G_Public", "PUBLIC");
            restrictedArchiveId = ArchiveSteps.create(tokenUserA, "G_Restricted", "RESTRICTED");
            privateArchiveId = ArchiveSteps.create(tokenUserA, "G_Private", "PRIVATE");
        }
    }

    // ========================================================================================
    // [Category 1]. Create Gallery (POST /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 갤러리 이미지 등록 (Bulk Create)")
    class CreateGallery {
        private List<Long> files;

        @BeforeEach
        void prepareFiles() {
            // 각 테스트마다 새로운 파일을 업로드하여 ID 충돌 방지
            files = new ArrayList<>();
            for (int i = 0; i < 5; i++) files.add(FileSteps.uploadFile(tokenUserA));

            // 테스트 전 갤러리 비우기 (멱등성 보장)
            galleryRepository.deleteAll();
        }

        @Test
        @DisplayName("SCENE 1. 정상 등록 - 단일 파일")
        void createGallery_Single() {
            Long fileId = files.get(0);
            Map<String, Object> request = Map.of("fileIds", List.of(fileId));

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.CREATED.value())
                    .body("createdCount", equalTo(1))
                    .body("archiveId", equalTo(publicArchiveId.intValue()));

            assertThat(galleryRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 2. 정상 등록 - 다중 파일")
        void createGallery_Multiple() {
            List<Long> fileIds = List.of(files.get(1), files.get(2), files.get(3));
            Map<String, Object> request = Map.of("fileIds", fileIds);

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.CREATED.value())
                    .body("createdCount", equalTo(3));

            List<Gallery> galleries = galleryRepository.findAll();
            assertThat(galleries).hasSize(3);
        }

        @Test
        @DisplayName("SCENE 3. 정상 등록 - Private 아카이브")
        void createGallery_Private() {
            List<Long> fileIds = List.of(files.get(4));
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("fileIds", fileIds))
                    .when().post("/api/v1/gallery/{archiveId}", privateArchiveId)
                    .then().statusCode(HttpStatus.CREATED.value());
        }

        @Test
        @DisplayName("SCENE 4. 예외 - IDOR (타인의 파일로 등록 시도)")
        void createGallery_IDOR() {
            Long userCFile = FileSteps.uploadFile(tokenUserC); // UserC File
            List<Long> fileIds = List.of(userCFile);

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("fileIds", fileIds))
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 5. 예외 - 타인의 아카이브에 등록 시도")
        void createGallery_Forbidden() {
            Long userCFile = FileSteps.uploadFile(tokenUserC);
            List<Long> fileIds = List.of(userCFile);

            given().cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("fileIds", fileIds))
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 6. 예외 - 존재하지 않는 파일 ID")
        void createGallery_FileNotFound() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("fileIds", List.of(99999L)))
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("FILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 7. 예외 - 빈 리스트 요청")
        void createGallery_EmptyList() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("fileIds", List.of()))
                    .when().post("/api/v1/gallery/{archiveId}", publicArchiveId)
                    .then().statusCode(HttpStatus.BAD_REQUEST.value());
        }
    }

    // ========================================================================================
    // [Category 2]. Read Gallery List (GET /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 갤러리 목록 조회 (권한/페이지네이션)")
    class ReadGallery {

        @BeforeEach
        void setUpData() {
            galleryRepository.deleteAll(); // Clean up

            // Create fresh files and galleries for each archive
            List<Long> files = new ArrayList<>();
            for (int i=0; i<10; i++) files.add(FileSteps.uploadFile(tokenUserA));

            // Public Archive (5 images)
            GallerySteps.create(tokenUserA, publicArchiveId, files.subList(0, 5));
            // Restricted Archive (5 images)
            GallerySteps.create(tokenUserA, restrictedArchiveId, files.subList(5, 10));
        }

        @Test
        @DisplayName("SCENE 8. PUBLIC 아카이브 조회 - 누구나 가능")
        void readPublic() {
            // Owner
            given().cookie("ATK", tokenUserA).get("/api/v1/gallery/{id}?page=0&size=10", publicArchiveId)
                    .then().statusCode(200).body("page.totalElements", greaterThanOrEqualTo(5));
            // Friend
            given().cookie("ATK", tokenUserB).get("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200);
            // Stranger
            given().cookie("ATK", tokenUserC).get("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200);
            // Anonymous
            given().get("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content[0].thumbnailUrl", containsString("thumbnails/medium/"))
                    .body("content[0].originalUrl", not(equalTo(null)));
        }

        @Test
        @DisplayName("SCENE 9~10. RESTRICTED 아카이브 조회")
        void readRestricted() {
            // 9: Friend -> OK
            given().cookie("ATK", tokenUserB).get("/api/v1/gallery/{id}", restrictedArchiveId)
                    .then().statusCode(200);

            // 10: Stranger -> Forbidden
            given().cookie("ATK", tokenUserC).get("/api/v1/gallery/{id}", restrictedArchiveId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 11. PRIVATE 아카이브 조회")
        void readPrivate() {
            // Setup Private Data
            List<Long> files = List.of(FileSteps.uploadFile(tokenUserA));
            GallerySteps.create(tokenUserA, privateArchiveId, files);

            // Owner -> OK
            given().cookie("ATK", tokenUserA).get("/api/v1/gallery/{id}", privateArchiveId)
                    .then().statusCode(200);

            // Friend -> Forbidden
            given().cookie("ATK", tokenUserB).get("/api/v1/gallery/{id}", privateArchiveId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 12. 페이지네이션 검증")
        void pagination() {
            // Given: Public Archive has at least 5 items
            // When: Page 0, Size 2
            given().cookie("ATK", tokenUserA)
                    .param("page", 0).param("size", 2)
                    .get("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(2))
                    .body("page.hasNext", equalTo(true));

            // When: Next Page
            given().cookie("ATK", tokenUserA)
                    .param("page", 1).param("size", 2)
                    .get("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(2));
        }

        @Test
        @DisplayName("SCENE 13. 존재하지 않는 아카이브 조회")
        void readNotFound() {
            given().cookie("ATK", tokenUserA).get("/api/v1/gallery/{id}", 999999)
                    .then().statusCode(404);
        }
    }

    // ========================================================================================
    // [Category 3]. Update GalleryBook Title (PATCH /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 갤러리북 제목 수정")
    class UpdateGalleryBook {

        @Test
        @DisplayName("SCENE 14. 정상 수정")
        void updateTitle_Normal() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "New Title"))
                    .when().patch("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("updatedTitle", equalTo("New Title"));

            GalleryBook book = galleryBookRepository.findById(publicArchiveId).orElseThrow();
            assertThat(book.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("SCENE 15. 예외 - 타인이 수정 시도")
        void updateTitle_Forbidden() {
            given().cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack"))
                    .when().patch("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 16. 예외 - 빈 제목")
        void updateTitle_BadRequest() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", ""))
                    .when().patch("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(400);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Gallery (DELETE /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 갤러리 이미지 삭제 (Bulk Delete)")
    class DeleteGallery {
        private List<Long> galleryIds;
        private List<Long> files;

        @BeforeEach
        void init() {
            galleryRepository.deleteAll(); // Clean up

            files = new ArrayList<>();
            for (int i=0; i<3; i++) files.add(FileSteps.uploadFile(tokenUserA));

            // Create specific items for deletion test
            GallerySteps.create(tokenUserA, publicArchiveId, files);

            // Fetch IDs
            galleryIds = galleryRepository.findAll().stream()
                    .filter(g -> g.getArchiveId().equals(publicArchiveId))
                    .map(Gallery::getId)
                    .collect(Collectors.toList());
        }

        @Test
        @DisplayName("SCENE 17. 정상 삭제 - 선택 삭제")
        void delete_Normal() {
            List<Long> toDelete = galleryIds.subList(0, 2); // Delete 2 items

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("galleryIds", toDelete))
                    .when().delete("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(204);

            assertThat(galleryRepository.findAllById(toDelete)).isEmpty();
            assertThat(galleryRepository.existsById(galleryIds.get(2))).isTrue(); // Remaining item
            assertThat(fileRepository.existsById(files.get(0))).isTrue(); // File preserved
        }

        @Test
        @DisplayName("SCENE 18. 예외 - 타인이 삭제 시도")
        void delete_Forbidden() {
            List<Long> toDelete = List.of(galleryIds.get(0));
            given().cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("galleryIds", toDelete))
                    .when().delete("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 19. 보안 검증 - Cross Archive Deletion 시도")
        void delete_CrossArchive() {
            // UserA has another archive
            Long anotherArchiveId = ArchiveSteps.create(tokenUserA, "Other", "PUBLIC");
            Long otherFile = FileSteps.uploadFile(tokenUserA);
            GallerySteps.create(tokenUserA, anotherArchiveId, List.of(otherFile));

            Long otherGalleryId = galleryRepository.findAll().stream()
                    .filter(g -> g.getArchiveId().equals(anotherArchiveId))
                    .findFirst().orElseThrow().getId();

            // Try to delete 'otherGalleryId' via 'publicArchiveId' endpoint
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("galleryIds", List.of(otherGalleryId)))
                    .when().delete("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(204); // Should return success (idempotent)

            // But item should NOT be deleted
            assertThat(galleryRepository.existsById(otherGalleryId)).isTrue();
        }

        @Test
        @DisplayName("SCENE 20. 예외 - 빈 리스트 삭제 요청")
        void delete_BadRequest() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("galleryIds", List.of()))
                    .when().delete("/api/v1/gallery/{id}", publicArchiveId)
                    .then().statusCode(400);
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================

    static class GallerySteps {
        static void create(String token, Long archiveId, List<Long> fileIds) {
            given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("fileIds", fileIds))
                    .post("/api/v1/gallery/{id}", archiveId)
                    .then().statusCode(201);
        }
    }

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
                    .body(Map.of("originalFileName", "g.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT"))
                    .post("/api/v1/files/multipart/initiate");
            String uploadId = init.jsonPath().getString("uploadId");
            String key = init.jsonPath().getString("key");
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("key", key, "uploadId", uploadId, "parts", List.of(Map.of("partNumber", 1, "etag", "e")),
                            "originalFileName", "g.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0))
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