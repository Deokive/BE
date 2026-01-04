package com.depth.deokive.common.api.diary;

import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.s3.service.S3Service;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Diary API 통합 테스트 시나리오 (E2E)")
class DiaryApiTest {

    @LocalServerPort private int port;

    // --- Mocks ---
    @MockitoBean private S3Service s3Service;

    // --- Repositories ---
    @Autowired private DiaryRepository diaryRepository;
    @Autowired private DiaryFileMapRepository diaryFileMapRepository;
    @Autowired private com.depth.deokive.domain.user.repository.UserRepository userRepository;
    @Autowired private com.depth.deokive.domain.friend.repository.FriendMapRepository friendMapRepository;
    @Autowired private FileRepository fileRepository;

    // --- Actors (Token) ---
    private static String tokenUserA; // Me (Owner)
    private static String tokenUserB; // Friend
    private static String tokenUserC; // Stranger

    // --- Shared Data ---
    private static Long userAId;
    private static Long userBId;
    private static Long userCId;

    // --- Fixtures for UserA ---
    private static Long publicArchiveId;
    private static Long restrictedArchiveId;
    private static Long privateArchiveId;
    private static Long file1Id;
    private static Long file2Id;
    private static Long file3Id;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // [S3 Mocking] UUID Key 생성으로 중복 방지 (ArchiveApiTest와 동일 전략)
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
            // 1. Users
            Map<String, Object> userA = AuthSteps.registerAndLogin("diarya@test.com", "DiaryA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("diaryb@test.com", "DiaryB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("diaryc@test.com", "DiaryC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");
            userCId = ((Number) userC.get("userId")).longValue();

            // 2. Friend (A-B)
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // 3. Archives (For UserA)
            publicArchiveId = ArchiveSteps.create(tokenUserA, "A_Public", "PUBLIC");
            restrictedArchiveId = ArchiveSteps.create(tokenUserA, "A_Restricted", "RESTRICTED");
            privateArchiveId = ArchiveSteps.create(tokenUserA, "A_Private", "PRIVATE");

            // 4. Files (For UserA)
            file1Id = FileSteps.uploadFile(tokenUserA);
            file2Id = FileSteps.uploadFile(tokenUserA);
            file3Id = FileSteps.uploadFile(tokenUserA);
        }
    }

    // ========================================================================================
    // [Category 1]. Create Diary (Scenes 1-7)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 다이어리 생성")
    class CreateDiary {

        @Test
        @DisplayName("SCENE 1. 정상 생성 - 파일 포함 (썸네일 지정)")
        void create_Normal_WithFiles() {
            Map<String, Object> f1 = Map.of("fileId", file1Id, "mediaRole", "PREVIEW", "sequence", 0);
            Map<String, Object> f2 = Map.of("fileId", file2Id, "mediaRole", "CONTENT", "sequence", 1);

            int diaryId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "테스트 일기",
                            "content", "내용",
                            "recordedAt", "2024-01-01",
                            "color", "#FF5733",
                            "visibility", "PUBLIC",
                            "files", List.of(f1, f2)
                    ))
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(201)
                    .body("id", notNullValue())
                    .body("title", equalTo("테스트 일기"))
                    .body("files.size()", equalTo(2))
                    .body("files[0].fileId", equalTo(file1Id.intValue()))
                    .body("files[0].mediaRole", equalTo("PREVIEW"))
                    .body("files[0].cdnUrl", containsString("test-cdn.com"))
                    .extract().jsonPath().getInt("id");

            // DB 검증
            Assertions.assertTrue(diaryRepository.existsById((long) diaryId));
            Assertions.assertTrue(diaryFileMapRepository.count() >= 2);
        }

        @Test
        @DisplayName("SCENE 2. 정상 생성 - 파일 없음")
        void create_NoFiles() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "파일 없는 일기",
                            "content", "글만 있음",
                            "recordedAt", "2024-01-02",
                            "color", "#FFFFFF",
                            "visibility", "PUBLIC"
                    ))
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(201)
                    .body("files", empty())
                    .body("thumbnailUrl", nullValue());
        }

        @Test
        @DisplayName("SCENE 3. 정상 생성 - Private 다이어리")
        void create_Private() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "비밀 일기",
                            "content", "쉿",
                            "recordedAt", "2024-01-03",
                            "color", "#000000",
                            "visibility", "PRIVATE"
                    ))
                    .when()
                    .post("/api/v1/diary/{archiveId}", privateArchiveId)
                    .then()
                    .statusCode(201)
                    .body("visibility", equalTo("PRIVATE"));
        }

        @Test
        @DisplayName("SCENE 4. 예외 - 존재하지 않는 아카이브")
        void create_Fail_ArchiveNotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Fail", "content", "x", "recordedAt", "2024-01-01", "color", "#000", "visibility", "PUBLIC"))
                    .when()
                    .post("/api/v1/diary/{archiveId}", 999999L)
                    .then()
                    .statusCode(404)
                    .body("error", equalTo("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 5. 예외 - 타인의 아카이브에 생성 시도")
        void create_Fail_Forbidden() {
            given()
                    .cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack", "content", "x", "recordedAt", "2024-01-01", "color", "#000", "visibility", "PUBLIC"))
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 6. 예외 - IDOR (내 다이어리에 남의 파일 첨부)")
        void create_Fail_IDOR() {
            // UserC uploads a file
            Long userCFile = FileSteps.uploadFile(tokenUserC);

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "IDOR Test",
                            "content", "content",
                            "recordedAt", "2024-01-01",
                            "color", "#000",
                            "visibility", "PUBLIC",
                            "files", List.of(Map.of("fileId", userCFile, "mediaRole", "CONTENT", "sequence", 0))
                    ))
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 7. 예외 - 필수값 누락")
        void create_Fail_BadRequest() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("content", "제목이 없음")) // title 누락
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400);
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail (Scenes 8-34)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 다이어리 상세 조회 (권한 매트릭스)")
    class ReadDiary {
        private Long pub_pub, pub_res, pub_pri;
        private Long res_pub, res_res, res_pri;
        private Long pri_pub, pri_res, pri_pri;

        @BeforeEach
        void setUpMatrix() {
            // Public Archive
            pub_pub = DiarySteps.create(tokenUserA, publicArchiveId, "Pub_Pub", "PUBLIC");
            pub_res = DiarySteps.create(tokenUserA, publicArchiveId, "Pub_Res", "RESTRICTED");
            pub_pri = DiarySteps.create(tokenUserA, publicArchiveId, "Pub_Pri", "PRIVATE");

            // Restricted Archive
            res_pub = DiarySteps.create(tokenUserA, restrictedArchiveId, "Res_Pub", "PUBLIC");
            res_res = DiarySteps.create(tokenUserA, restrictedArchiveId, "Res_Res", "RESTRICTED");
            res_pri = DiarySteps.create(tokenUserA, restrictedArchiveId, "Res_Pri", "PRIVATE");

            // Private Archive
            pri_pub = DiarySteps.create(tokenUserA, privateArchiveId, "Pri_Pub", "PUBLIC");
            pri_res = DiarySteps.create(tokenUserA, privateArchiveId, "Pri_Res", "RESTRICTED");
            pri_pri = DiarySteps.create(tokenUserA, privateArchiveId, "Pri_Pri", "PRIVATE");
        }

        // --- PUBLIC Archive ---
        @Test @DisplayName("SCENE 8. PUBLIC Archive + PUBLIC Diary - 본인")
        void s8() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 9. PUBLIC Archive + PUBLIC Diary - 타인")
        void s9() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 10. PUBLIC Archive + PUBLIC Diary - 친구")
        void s10() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 11. PUBLIC Archive + PUBLIC Diary - 비회원")
        void s11() { given().get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); }

        @Test @DisplayName("SCENE 12. PUBLIC Archive + RESTRICTED Diary - 본인")
        void s12() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_res).then().statusCode(200); }
        @Test @DisplayName("SCENE 13. PUBLIC Archive + RESTRICTED Diary - 친구")
        void s13() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_res).then().statusCode(200); }
        @Test @DisplayName("SCENE 14. PUBLIC Archive + RESTRICTED Diary - 타인")
        void s14() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_res).then().statusCode(403); }
        @Test @DisplayName("SCENE 15. PUBLIC Archive + RESTRICTED Diary - 비회원")
        void s15() { given().get("/api/v1/diary/{id}", pub_res).then().statusCode(403); }

        @Test @DisplayName("SCENE 16. PUBLIC Archive + PRIVATE Diary - 본인")
        void s16() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_pri).then().statusCode(200); }
        @Test @DisplayName("SCENE 17. PUBLIC Archive + PRIVATE Diary - 친구")
        void s17() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_pri).then().statusCode(403); }
        @Test @DisplayName("SCENE 18. PUBLIC Archive + PRIVATE Diary - 타인")
        void s18() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_pri).then().statusCode(403); }

        // --- RESTRICTED Archive ---
        @Test @DisplayName("SCENE 19. RESTRICTED Archive + PUBLIC Diary - 본인")
        void s19() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 20. RESTRICTED Archive + PUBLIC Diary - 친구")
        void s20() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 21. RESTRICTED Archive + PUBLIC Diary - 타인")
        void s21() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", res_pub).then().statusCode(403); }

        @Test @DisplayName("SCENE 22. RESTRICTED Archive + RESTRICTED Diary - 본인")
        void s22() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_res).then().statusCode(200); }
        @Test @DisplayName("SCENE 23. RESTRICTED Archive + RESTRICTED Diary - 친구")
        void s23() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_res).then().statusCode(200); }
        @Test @DisplayName("SCENE 24. RESTRICTED Archive + RESTRICTED Diary - 타인")
        void s24() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", res_res).then().statusCode(403); }

        @Test @DisplayName("SCENE 25. RESTRICTED Archive + PRIVATE Diary - 본인")
        void s25() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_pri).then().statusCode(200); }
        @Test @DisplayName("SCENE 26. RESTRICTED Archive + PRIVATE Diary - 친구")
        void s26() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_pri).then().statusCode(403); }

        // --- PRIVATE Archive ---
        @Test @DisplayName("SCENE 27. PRIVATE Archive + PUBLIC Diary - 본인")
        void s27() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_pub).then().statusCode(200); }
        @Test @DisplayName("SCENE 28. PRIVATE Archive + PUBLIC Diary - 친구")
        void s28() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_pub).then().statusCode(403); }
        @Test @DisplayName("SCENE 29. PRIVATE Archive + PUBLIC Diary - 타인")
        void s29() { given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pri_pub).then().statusCode(403); }

        @Test @DisplayName("SCENE 30. PRIVATE Archive + RESTRICTED Diary - 본인")
        void s30() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_res).then().statusCode(200); }
        @Test @DisplayName("SCENE 31. PRIVATE Archive + RESTRICTED Diary - 친구")
        void s31() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_res).then().statusCode(403); }

        @Test @DisplayName("SCENE 32. PRIVATE Archive + PRIVATE Diary - 본인")
        void s32() { given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_pri).then().statusCode(200); }
        @Test @DisplayName("SCENE 33. PRIVATE Archive + PRIVATE Diary - 친구")
        void s33() { given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_pri).then().statusCode(403); }

        @Test @DisplayName("SCENE 34. 존재하지 않는 다이어리")
        void s34() {
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/diary/{id}", 999999L)
                    .then().statusCode(404)
                    .body("error", equalTo("DIARY_NOT_FOUND"));
        }
    }

    // ========================================================================================
    // [Category 3]. Update Diary (Scenes 35-39)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 다이어리 수정")
    class UpdateDiary {
        private Long diaryId;

        @BeforeEach
        void init() {
            diaryId = DiarySteps.createWithFile(tokenUserA, publicArchiveId, "Original", "PUBLIC", file1Id);
        }

        @Test
        @DisplayName("SCENE 35. 정상 수정 - 내용 및 공개범위 변경")
        void update_Info() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "Updated",
                            "content", "Updated Content",
                            "recordedAt", "2024-12-31",
                            "color", "#000000",
                            "visibility", "PRIVATE"
                    ))
                    .when()
                    .patch("/api/v1/diary/{id}", diaryId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("Updated"))
                    .body("visibility", equalTo("PRIVATE"));
        }

        @Test
        @DisplayName("SCENE 36. 정상 수정 - 파일 전체 교체")
        void update_ReplaceFiles() {
            // file1Id -> file2Id 교체
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "files", List.of(Map.of("fileId", file2Id, "mediaRole", "PREVIEW", "sequence", 0))
                    ))
                    .when()
                    .patch("/api/v1/diary/{id}", diaryId)
                    .then()
                    .statusCode(200)
                    .body("files.size()", equalTo(1))
                    .body("files[0].fileId", equalTo(file2Id.intValue()));
        }

        @Test
        @DisplayName("SCENE 37. 정상 수정 - 파일 삭제")
        void update_DeleteFiles() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("files", List.of())) // Empty
                    .when()
                    .patch("/api/v1/diary/{id}", diaryId)
                    .then()
                    .statusCode(200)
                    .body("files.size()", equalTo(0))
                    .body("thumbnailUrl", nullValue());
        }

        @Test
        @DisplayName("SCENE 38. 예외 - 타인이 수정 시도")
        void update_Fail_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hacked"))
                    .when()
                    .patch("/api/v1/diary/{id}", diaryId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 39. 예외 - 존재하지 않는 다이어리")
        void update_Fail_NotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Ghost"))
                    .when()
                    .patch("/api/v1/diary/{id}", 999999L)
                    .then()
                    .statusCode(404);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Diary (Scenes 40-41)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 다이어리 삭제")
    class DeleteDiary {
        private Long diaryId;

        @BeforeEach
        void init() {
            diaryId = DiarySteps.create(tokenUserA, publicArchiveId, "DeleteMe", "PUBLIC");
        }

        @Test
        @DisplayName("SCENE 40. 정상 삭제 - 본인")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA)
                    .delete("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(204);

            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(404);
        }

        @Test
        @DisplayName("SCENE 41. 예외 - 타인이 삭제 시도")
        void delete_Fail_Stranger() {
            given().cookie("ATK", tokenUserC)
                    .delete("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 5]. Update DiaryBook Title (Scenes 42-43)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 다이어리북 제목 수정")
    class UpdateBookTitle {

        @Test
        @DisplayName("SCENE 42. 정상 수정")
        void updateBook_Normal() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "New Book Title"))
                    .when()
                    .patch("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("updatedTitle", equalTo("New Book Title"));
        }

        @Test
        @DisplayName("SCENE 43. 예외 - 타인이 수정 시도")
        void updateBook_Fail_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hacked"))
                    .when()
                    .patch("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 6]. Pagination (Scenes 44-49)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] 다이어리 목록 조회")
    class Pagination {
        @BeforeEach
        void setUpListData() {
            // UserA Archive에 데이터 주입: 5 Public, 3 Restricted, 2 Private
            for (int i = 0; i < 5; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Pub" + i, "PUBLIC");
            for (int i = 0; i < 3; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Res" + i, "RESTRICTED");
            for (int i = 0; i < 2; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Pri" + i, "PRIVATE");
        }

        @Test
        @DisplayName("SCENE 44. 본인 조회 (전체 노출)")
        void list_Owner() {
            given()
                    .cookie("ATK", tokenUserA)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(10))
                    .body("content.find { it.visibility == 'PRIVATE' }", notNullValue());
        }

        @Test
        @DisplayName("SCENE 45. 친구 조회 (Private 제외)")
        void list_Friend() {
            given()
                    .cookie("ATK", tokenUserB)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", notNullValue())
                    .body("content.find { it.visibility == 'PRIVATE' }", nullValue());
        }

        @Test
        @DisplayName("SCENE 46. 타인 조회 (Public Only)")
        void list_Stranger() {
            given()
                    .cookie("ATK", tokenUserC)
                    .param("size", 20)
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test
        @DisplayName("SCENE 47. 비회원 조회 (Public Only)")
        void list_Anonymous() {
            given()
                    .param("size", 20)
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test
        @DisplayName("SCENE 48. 아카이브 접근 불가 케이스")
        void list_Fail_ArchiveAccess() {
            // Private Archive 조회 시도
            given()
                    .cookie("ATK", tokenUserC)
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", privateArchiveId)
                    .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("SCENE 49. 정렬 확인")
        void list_Sorting() {
            given()
                    .cookie("ATK", tokenUserA)
                    .param("sort", "recordedAt")
                    .param("direction", "DESC")
                    .when()
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThan(0));
        }
    }

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    static class DiarySteps {
        static Long create(String token, Long archiveId, String title, String visibility) {
            return given()
                    .cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", title,
                            "content", "Content",
                            "recordedAt", "2024-01-01",
                            "color", "#FFFFFF",
                            "visibility", visibility
                    ))
                    .post("/api/v1/diary/{archiveId}", archiveId)
                    .then().statusCode(201)
                    .extract().jsonPath().getLong("id");
        }

        static Long createWithFile(String token, Long archiveId, String title, String visibility, Long fileId) {
            return given()
                    .cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", title,
                            "content", "Content",
                            "recordedAt", "2024-01-01",
                            "color", "#FFFFFF",
                            "visibility", visibility,
                            "files", List.of(Map.of("fileId", fileId, "mediaRole", "PREVIEW", "sequence", 0))
                    ))
                    .post("/api/v1/diary/{archiveId}", archiveId)
                    .then().statusCode(201)
                    .extract().jsonPath().getLong("id");
        }
    }

    static class AuthSteps {
        private static final String MAILHOG_HOST = "http://localhost:8025";
        private static final String MAILHOG_MESSAGES_API = MAILHOG_HOST + "/api/v2/messages";

        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            // [Fix 1] 테스트 시작 전 MailHog 비우기 (데이터 간섭 방지)
            clearMailHog();

            // 1. 이메일 발송 요청
            given().param("email", email)
                    .post("/api/v1/auth/email/send")
                    .then().statusCode(202);

            // 2. 비동기 처리 대기
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // 3. 인증 코드 파싱 (디버깅 로그 포함)
            String code = getVerificationCode(email);

            // 4. 이메일 검증 수행
            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "code", code, "purpose", "SIGNUP"))
                    .post("/api/v1/auth/email/verify")
                    .then().statusCode(200);

            // 5. 회원가입
            int userId = given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "nickname", nickname, "password", password))
                    .post("/api/v1/auth/register")
                    .then().statusCode(200)
                    .extract().jsonPath().getInt("id");

            // 6. 로그인
            Response loginRes = given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", password))
                    .post("/api/v1/auth/login");

            loginRes.then().statusCode(200);

            return Map.of("accessToken", loginRes.getCookie("ATK"), "userId", userId);
        }

        // MailHog 메시지 전체 삭제 (초기화)
        private static void clearMailHog() {
            try {
                RestAssured.given().delete(MAILHOG_MESSAGES_API);
            } catch (Exception e) {
                System.err.println("⚠️ MailHog 초기화 실패 (무시 가능): " + e.getMessage());
            }
        }

        private static String getVerificationCode(String email) {
            System.out.println("🔍 MailHog에서 인증코드 조회 시도: " + email);

            for (int i = 0; i < 20; i++) {
                try {
                    // MailHog API 호출
                    Response res = RestAssured.given().get(MAILHOG_MESSAGES_API);
                    List<Map<String, Object>> messages = res.jsonPath().getList("items");

                    if (messages == null || messages.isEmpty()) {
                        System.out.println("   Mining... (메일함 비어있음) " + i);
                        Thread.sleep(500);
                        continue;
                    }

                    for (Map<String, Object> msg : messages) {
                        // Content가 null인 경우 방어 로직
                        Map<String, Object> content = (Map<String, Object>) msg.get("Content");
                        if (content == null) continue;

                        String body = (String) content.get("Body");
                        String headers = msg.get("Content").toString(); // 헤더 정보도 포함해서 검색

                        // 수신자 확인 (Body나 Header에 이메일이 포함되어 있는지)
                        if ((body != null && body.contains(email)) || headers.contains(email)) {
                            Matcher m = Pattern.compile("\\d{6}").matcher(body);
                            if (m.find()) {
                                String code = m.group();
                                System.out.println("✅ 인증코드 발견: " + code);
                                return code;
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    // 예외를 무시하지 않고 출력 (원인 파악용)
                    System.err.println("⚠️ MailHog 파싱 에러: " + e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
            throw new RuntimeException("MailHog Fail - 인증 코드를 찾을 수 없습니다. (Email: " + email + ")");
        }
    }

    static class ArchiveSteps {
        static Long create(String token, String title, String visibility) {
            return given().cookie("ATK", token).contentType(ContentType.JSON).body(Map.of("title", title, "visibility", visibility)).post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class FileSteps {
        static Long uploadFile(String token) {
            Response initRes = given().cookie("ATK", token).contentType(ContentType.JSON).body(Map.of("originalFileName", "test.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT")).post("/api/v1/files/multipart/initiate").then().statusCode(200).extract().response();
            String uploadId = initRes.jsonPath().getString("uploadId");
            String key = initRes.jsonPath().getString("key");
            return given().cookie("ATK", token).contentType(ContentType.JSON).body(Map.of("key", key, "uploadId", uploadId, "parts", List.of(Map.of("partNumber", 1, "etag", "mock-etag")), "originalFileName", "test.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0)).post("/api/v1/files/multipart/complete").then().statusCode(200).extract().jsonPath().getLong("fileId");
        }
    }

    static class FriendSteps {
        static void makeFriendDirectly(com.depth.deokive.domain.user.repository.UserRepository userRepo, com.depth.deokive.domain.friend.repository.FriendMapRepository friendRepo, Long userA, Long userB) {
            var uA = userRepo.findById(userA).orElseThrow();
            var uB = userRepo.findById(userB).orElseThrow();
            friendRepo.save(com.depth.deokive.domain.friend.entity.FriendMap.builder().user(uA).friend(uB).requestedBy(uA).friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED).acceptedAt(java.time.LocalDateTime.now()).build());
            friendRepo.save(com.depth.deokive.domain.friend.entity.FriendMap.builder().user(uB).friend(uA).requestedBy(uA).friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED).acceptedAt(java.time.LocalDateTime.now()).build());
        }
    }
}