package com.depth.deokive.domain.diary.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
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
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Diary API 통합 테스트 시나리오 (E2E)")
class DiaryApiTest extends ApiTestSupport { // 상속 변경

    // --- Repositories ---
    @Autowired private DiaryRepository diaryRepository;
    @Autowired private DiaryFileMapRepository diaryFileMapRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private ArchiveRepository archiveRepository; // Archive 생성용
    @Autowired private DiaryBookRepository diaryBookRepository; // Book 생성 확인용

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
        // [S3 Mocking - 부모 클래스의 s3Service 사용]
        mockS3Service();

        // [Global Setup] 최초 1회 실행
        if (tokenUserA == null) {
            // 1. Users Setup
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
                    .statusCode(HttpStatus.CREATED.value())
                    .body("id", notNullValue())
                    .body("title", equalTo("테스트 일기"))
                    .body("files.size()", equalTo(2))
                    .body("files[0].fileId", equalTo(file1Id.intValue()))
                    .body("files[0].mediaRole", equalTo("PREVIEW"))
                    .body("files[0].cdnUrl", containsString("http")) // Mock URL
                    .extract().jsonPath().getInt("id");

            // DB 검증
            assertThat(diaryRepository.existsById((long) diaryId)).isTrue();
            assertThat(diaryFileMapRepository.count()).isGreaterThanOrEqualTo(2);
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
                    .statusCode(HttpStatus.CREATED.value())
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
                    .statusCode(HttpStatus.CREATED.value())
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
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("SCENE 5. 예외 - 타인의 아카이브에 생성 시도")
        void create_Fail_Forbidden() {
            // Stranger creates Archive (will be used for failure case)
            Long strangerArchiveId = ArchiveSteps.create(tokenUserC, "C_Pub", "PUBLIC");

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack", "content", "x", "recordedAt", "2024-01-01", "color", "#000", "visibility", "PUBLIC"))
                    .when()
                    .post("/api/v1/diary/{archiveId}", strangerArchiveId)
                    .then()
                    .statusCode(HttpStatus.FORBIDDEN.value())
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("SCENE 6. 예외 - IDOR (내 다이어리에 남의 파일 첨부)")
        void create_Fail_IDOR() {
            // UserC uploads a file
            Long userCFileId = FileSteps.uploadFile(tokenUserC);

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "title", "IDOR Test",
                            "content", "content",
                            "recordedAt", "2024-01-01",
                            "color", "#000",
                            "visibility", "PUBLIC",
                            "files", List.of(Map.of("fileId", userCFileId, "mediaRole", "CONTENT", "sequence", 0))
                    ))
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.FORBIDDEN.value())
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
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("SCENE 8. 예외 - 날짜 형식 오류")
        void create_Fail_InvalidDateFormat() {
            // Given: 잘못된 날짜 형식
            Map<String, Object> request = Map.of(
                    "title", "Invalid Date",
                    "content", "Content",
                    "recordedAt", "2024-13-01", // 잘못된 월
                    "color", "#FF5733",
                    "visibility", "PUBLIC"
            );

            // When & Then
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("SCENE 9. 예외 - 날짜 null")
        void create_Fail_DateNull() {
            // Given: 날짜가 null
            Map<String, Object> request = new HashMap<>();
            request.put("title", "No Date");
            request.put("content", "Content");
            request.put("recordedAt", null);
            request.put("color", "#FF5733");
            request.put("visibility", "PUBLIC");

            // When & Then
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("SCENE 10. 예외 - 색상 코드 형식 오류 (HEX 코드 아님)")
        void create_Fail_InvalidColorFormat() {
            // Given: 잘못된 색상 코드 형식
            Map<String, Object> request = Map.of(
                    "title", "Invalid Color",
                    "content", "Content",
                    "recordedAt", "2024-01-01",
                    "color", "FF5733", // # 없음
                    "visibility", "PUBLIC"
            );

            // When & Then
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("SCENE 11. 예외 - 색상 코드 형식 오류 (잘못된 HEX 문자)")
        void create_Fail_InvalidColorHex() {
            // Given: 잘못된 HEX 문자 포함
            Map<String, Object> request = Map.of(
                    "title", "Invalid Color",
                    "content", "Content",
                    "recordedAt", "2024-01-01",
                    "color", "#GGGGGG", // 잘못된 HEX 문자
                    "visibility", "PUBLIC"
            );

            // When & Then
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/diary/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("error", notNullValue());
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail
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
        @Test @DisplayName("SCENE 8~11. PUBLIC Archive + PUBLIC Diary")
        void s8_11() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); // Stranger
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); // Friend
            given().get("/api/v1/diary/{id}", pub_pub).then().statusCode(200); // Anon
        }

        @Test @DisplayName("SCENE 12~15. PUBLIC Archive + RESTRICTED Diary")
        void s12_15() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_res).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_res).then().statusCode(200); // Friend
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_res).then().statusCode(403); // Stranger
            given().get("/api/v1/diary/{id}", pub_res).then().statusCode(403); // Anon
        }

        @Test @DisplayName("SCENE 16~18. PUBLIC Archive + PRIVATE Diary")
        void s16_18() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pub_pri).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pub_pri).then().statusCode(403); // Friend
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pub_pri).then().statusCode(403); // Stranger
        }

        // --- RESTRICTED Archive ---
        @Test @DisplayName("SCENE 19~21. RESTRICTED Archive + PUBLIC Diary")
        void s19_21() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_pub).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_pub).then().statusCode(200); // Friend
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", res_pub).then().statusCode(403); // Stranger
        }

        @Test @DisplayName("SCENE 22~24. RESTRICTED Archive + RESTRICTED Diary")
        void s22_24() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_res).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_res).then().statusCode(200); // Friend
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", res_res).then().statusCode(403); // Stranger
        }

        @Test @DisplayName("SCENE 25~26. RESTRICTED Archive + PRIVATE Diary")
        void s25_26() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", res_pri).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", res_pri).then().statusCode(403); // Friend
        }

        // --- PRIVATE Archive ---
        @Test @DisplayName("SCENE 27~29. PRIVATE Archive + PUBLIC Diary")
        void s27_29() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_pub).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_pub).then().statusCode(403); // Friend
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/{id}", pri_pub).then().statusCode(403); // Stranger
        }

        @Test @DisplayName("SCENE 30~31. PRIVATE Archive + RESTRICTED Diary")
        void s30_31() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_res).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_res).then().statusCode(403); // Friend
        }

        @Test @DisplayName("SCENE 32~33. PRIVATE Archive + PRIVATE Diary")
        void s32_33() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", pri_pri).then().statusCode(200); // Owner
            given().cookie("ATK", tokenUserB).get("/api/v1/diary/{id}", pri_pri).then().statusCode(403); // Friend
        }

        @Test @DisplayName("SCENE 34. 존재하지 않는 다이어리")
        void s34() {
            given().cookie("ATK", tokenUserA).get("/api/v1/diary/{id}", 999999L)
                    .then().statusCode(404).body("error", equalTo("DIARY_NOT_FOUND"));
        }
    }

    // ========================================================================================
    // [Category 3]. Update Diary
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 다이어리 수정")
    class UpdateDiary {
        private Long diaryId;

        @BeforeEach
        void init() {
            diaryId = DiarySteps.createWithFile(tokenUserA, publicArchiveId, "Original", "PUBLIC", file1Id);
        }

        @Test @DisplayName("SCENE 35. 정상 수정 - 내용 및 공개범위")
        void update_Info() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Updated", "content", "New", "recordedAt", "2024-12-31", "color", "#000", "visibility", "PRIVATE"))
                    .when().patch("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(200).body("title", equalTo("Updated")).body("visibility", equalTo("PRIVATE"));
        }

        @Test @DisplayName("SCENE 36. 정상 수정 - 파일 교체")
        void update_ReplaceFiles() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("files", List.of(Map.of("fileId", file2Id, "mediaRole", "PREVIEW", "sequence", 0))))
                    .when().patch("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(200).body("files.size()", equalTo(1)).body("files[0].fileId", equalTo(file2Id.intValue()));
        }

        @Test @DisplayName("SCENE 37. 정상 수정 - 파일 삭제")
        void update_DeleteFiles() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("files", List.of()))
                    .when().patch("/api/v1/diary/{id}", diaryId)
                    .then().statusCode(200).body("files.size()", equalTo(0));
        }

        @Test @DisplayName("SCENE 38~39. 타인 수정/존재하지 않는 다이어리")
        void update_Fail() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("title", "Hack"))
                    .patch("/api/v1/diary/{id}", diaryId).then().statusCode(403);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("title", "Ghost"))
                    .patch("/api/v1/diary/{id}", 999999L).then().statusCode(404);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Diary
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 다이어리 삭제")
    class DeleteDiary {
        private Long diaryId;

        @BeforeEach
        void init() {
            diaryId = DiarySteps.create(tokenUserA, publicArchiveId, "DeleteMe", "PUBLIC");
        }

        @Test @DisplayName("SCENE 40. 정상 삭제")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/diary/{id}", diaryId).then().statusCode(204);
            assertThat(diaryRepository.existsById(diaryId)).isFalse();
        }

        @Test @DisplayName("SCENE 41. 타인 삭제")
        void delete_Fail() {
            given().cookie("ATK", tokenUserC).delete("/api/v1/diary/{id}", diaryId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 5]. Update DiaryBook Title
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 다이어리북 제목 수정")
    class UpdateBookTitle {
        @Test @DisplayName("SCENE 42~43. 제목 수정")
        void updateBook() {
            // Normal
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("title", "New Book"))
                    .patch("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then().statusCode(200).body("updatedTitle", equalTo("New Book"));

            // Stranger
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("title", "Hack"))
                    .patch("/api/v1/diary/book/{archiveId}", publicArchiveId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 6]. Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] 다이어리 목록 조회")
    class Pagination {
        @BeforeEach
        void setUpListData() {
            for (int i = 0; i < 5; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Pub" + i, "PUBLIC");
            for (int i = 0; i < 3; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Res" + i, "RESTRICTED");
            for (int i = 0; i < 2; i++) DiarySteps.create(tokenUserA, publicArchiveId, "Pri" + i, "PRIVATE");
        }

        @Test @DisplayName("SCENE 44. 본인 조회 (전체)")
        void list_Owner() {
            given().cookie("ATK", tokenUserA).param("size", 20).get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(10));
        }

        @Test @DisplayName("SCENE 45. 친구 조회 (Private 제외)")
        void list_Friend() {
            given().cookie("ATK", tokenUserB).param("size", 20).get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.find { it.visibility == 'RESTRICTED' }", notNullValue())
                    .body("content.find { it.visibility == 'PRIVATE' }", nullValue());
        }

        @Test @DisplayName("SCENE 46. 타인 조회 (Public Only)")
        void list_Stranger() {
            given().cookie("ATK", tokenUserC).param("size", 20).get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test @DisplayName("SCENE 47. 비회원 조회 (Public Only)")
        void list_Anon() {
            given().param("size", 20).get("/api/v1/diary/book/{archiveId}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content.visibility", everyItem(equalTo("PUBLIC")));
        }

        @Test @DisplayName("SCENE 48. 접근 불가 아카이브")
        void list_Fail() {
            given().cookie("ATK", tokenUserC).get("/api/v1/diary/book/{archiveId}", privateArchiveId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 49. 정렬")
        void list_Sort() {
            given().cookie("ATK", tokenUserA).param("sort", "recordedAt").param("direction", "DESC")
                    .get("/api/v1/diary/book/{archiveId}", publicArchiveId).then().statusCode(200);
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================
    static class DiarySteps {
        static Long create(String token, Long archiveId, String title, String visibility) {
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("title", title, "content", "C", "recordedAt", "2024-01-01", "color", "#FFF", "visibility", visibility))
                    .post("/api/v1/diary/{archiveId}", archiveId).then().statusCode(201).extract().jsonPath().getLong("id");
        }
        static Long createWithFile(String token, Long archiveId, String title, String visibility, Long fileId) {
            return given().cookie("ATK", token).contentType(ContentType.JSON)
                    .body(Map.of("title", title, "content", "C", "recordedAt", "2024-01-01", "color", "#FFF", "visibility", visibility,
                            "files", List.of(Map.of("fileId", fileId, "mediaRole", "PREVIEW", "sequence", 0))))
                    .post("/api/v1/diary/{archiveId}", archiveId).then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
            try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}
            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            String code = getVerificationCode(email, mailhogUrl);
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