package com.depth.deokive.common.api.ticket;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
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
import java.util.ArrayList;
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

@DisplayName("Ticket API 통합 테스트 시나리오")
class TicketApiTest extends ApiTestSupport {

    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketBookRepository ticketBookRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;
    @Autowired private FileRepository fileRepository;

    private static String tokenUserA, tokenUserB, tokenUserC;
    private static Long userAId, userBId;
    private static Long publicArchiveId, restrictedArchiveId, privateArchiveId;
    private static List<Long> userAFiles;
    private static List<Long> userCFiles;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // [S3 Mocking]
        mockS3();

        // [Global Setup]
        if (tokenUserA == null) {
            Map<String, Object> userA = AuthSteps.registerAndLogin("ticket.a@test.com", "TicketA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("ticket.b@test.com", "TicketB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("ticket.c@test.com", "TicketC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");

            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            publicArchiveId = ArchiveSteps.create(tokenUserA, "T_Public", "PUBLIC");
            restrictedArchiveId = ArchiveSteps.create(tokenUserA, "T_Restricted", "RESTRICTED");
            privateArchiveId = ArchiveSteps.create(tokenUserA, "T_Private", "PRIVATE");

            userAFiles = new ArrayList<>();
            userAFiles.add(FileSteps.uploadFile(tokenUserA));
            userAFiles.add(FileSteps.uploadFile(tokenUserA));

            userCFiles = new ArrayList<>();
            userCFiles.add(FileSteps.uploadFile(tokenUserC));
        }
    }

    private void mockS3() {
        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> {
            String uniqueKey = "files/" + UUID.randomUUID() + "__test.jpg";
            return S3ServiceDto.UploadInitiateResponse.builder()
                    .uploadId("mock-upload-id").key(uniqueKey).contentType("image/jpeg").build();
        });
        when(s3Service.calculatePartCount(any())).thenReturn(1);
        when(s3Service.generatePartPresignedUrls(any())).thenReturn(List.of());
        when(s3Service.completeUpload(any())).thenAnswer(invocation -> software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder().build());
    }

    // ========================================================================================
    // [Category 1]. Create Ticket
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 티켓 생성")
    class CreateTicket {

        @BeforeEach
        void cleanUp() { ticketRepository.deleteAll(); }

        @Test @DisplayName("SCENE 1. 정상 생성 - 이미지 O")
        void create_WithImage() {
            Map<String, Object> req = new HashMap<>();
            req.put("title", "뮤지컬 시카고");
            req.put("date", "2024-12-25T19:00:00");
            req.put("location", "대성 디큐브아트센터");
            req.put("seat", "VIP석 1열 1번");
            req.put("casting", "최재림, 아이비");
            req.put("score", 5);
            req.put("review", "최고의 공연!");
            req.put("fileId", userAFiles.get(0));

            int ticketId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then().statusCode(201)
                    .body("title", equalTo("뮤지컬 시카고"))
                    .body("file.fileId", equalTo(userAFiles.get(0).intValue()))
                    .extract().jsonPath().getInt("id");

            Ticket ticket = ticketRepository.findById((long) ticketId).orElseThrow();
            assertThat(ticket.getTitle()).isEqualTo("뮤지컬 시카고");
            assertThat(ticket.getFile().getId()).isEqualTo(userAFiles.get(0));
            assertThat(ticket.getOriginalKey()).isNotNull();
        }

        @Test @DisplayName("SCENE 2. 정상 생성 - 이미지 X")
        void create_NoImage() {
            Map<String, Object> req = Map.of("title", "전시회", "date", "2024-01-01T19:00:00");

            int ticketId = given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", restrictedArchiveId)
                    .then().statusCode(201)
                    .body("file", nullValue())
                    .extract().jsonPath().getInt("id");

            Ticket ticket = ticketRepository.findById((long) ticketId).orElseThrow();
            assertThat(ticket.getFile()).isNull();
            assertThat(ticket.getOriginalKey()).isNull();
        }

        @Test @DisplayName("SCENE 3. 예외 - 필수값 누락")
        void create_BadRequest() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("location", "No Title"))
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test @DisplayName("SCENE 4. 예외 - 평점 범위 초과")
        void create_ScoreExceed() {
            Map<String, Object> req = Map.of("title", "T", "date", "2024-01-01T00:00:00", "score", 10);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test @DisplayName("SCENE 7. 예외 - 평점 범위 하한선 (0)")
        void create_ScoreZero() {
            Map<String, Object> req = Map.of("title", "T", "date", "2024-01-01T00:00:00", "score", 0);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(201); // 0은 유효한 값 (Min(0)이므로)
        }

        @Test @DisplayName("SCENE 8. 예외 - 평점 범위 하한선 (음수)")
        void create_ScoreNegative() {
            Map<String, Object> req = Map.of("title", "T", "date", "2024-01-01T00:00:00", "score", -1);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test @DisplayName("SCENE 9. 예외 - 날짜 형식 오류")
        void create_InvalidDateFormat() {
            Map<String, Object> req = Map.of("title", "T", "date", "2024-13-01T00:00:00"); // 잘못된 월
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test @DisplayName("SCENE 10. 예외 - 날짜 null")
        void create_DateNull() {
            Map<String, Object> req = new HashMap<>();
            req.put("title", "T");
            req.put("date", null);
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test @DisplayName("SCENE 5. 예외 - IDOR (타인 파일)")
        void create_IDOR() {
            Map<String, Object> req = Map.of("title", "Hack", "date", "2024-01-01T00:00:00", "fileId", userCFiles.get(0));
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test @DisplayName("SCENE 6. 예외 - 타인 아카이브")
        void create_Forbidden() {
            Map<String, Object> req = Map.of("title", "Hack", "date", "2024-01-01T00:00:00");
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/tickets/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 티켓 상세 조회")
    class ReadTicket {
        private Long tPub, tRes, tPri;

        @BeforeEach
        void setUpData() {
            ticketRepository.deleteAll();
            tPub = TicketSteps.createWithFile(tokenUserA, publicArchiveId, "Pub", userAFiles.get(0));
            tRes = TicketSteps.createWithFile(tokenUserA, restrictedArchiveId, "Res", null);
            tPri = TicketSteps.createWithFile(tokenUserA, privateArchiveId, "Pri", null);
        }

        @Test @DisplayName("SCENE 7. PUBLIC 조회")
        void readPublic() {
            given().cookie("ATK", tokenUserC).get("/api/v1/tickets/{id}", tPub)
                    .then().statusCode(200)
                    .body("title", equalTo("Pub"))
                    .body("file.cdnUrl", containsString("http"));
        }

        @Test @DisplayName("SCENE 8~9. RESTRICTED 조회")
        void readRestricted() {
            // Friend -> OK
            given().cookie("ATK", tokenUserB).get("/api/v1/tickets/{id}", tRes).then().statusCode(200);
            // Stranger -> Fail
            given().cookie("ATK", tokenUserC).get("/api/v1/tickets/{id}", tRes).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 10. PRIVATE 조회")
        void readPrivate() {
            given().cookie("ATK", tokenUserA).get("/api/v1/tickets/{id}", tPri).then().statusCode(200);
            given().cookie("ATK", tokenUserB).get("/api/v1/tickets/{id}", tPri).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 11. 존재하지 않는 티켓")
        void readNotFound() {
            given().cookie("ATK", tokenUserA)
                    .get("/api/v1/tickets/{id}", 99999)
                    .then()
                    .statusCode(404)
                    .body("error", notNullValue());
        }
    }

    // ========================================================================================
    // [Category 3]. Update Ticket
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 티켓 수정")
    class UpdateTicket {
        private Long ticketId;

        @BeforeEach
        void init() {
            ticketRepository.deleteAll();
            ticketId = TicketSteps.createWithFile(tokenUserA, publicArchiveId, "Origin", userAFiles.get(0));
        }

        @Test @DisplayName("SCENE 12. 텍스트 수정")
        void update_Text() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("title", "Updated", "score", 3))
                    .patch("/api/v1/tickets/{id}", ticketId)
                    .then().statusCode(200)
                    .body("title", equalTo("Updated"))
                    .body("file.fileId", equalTo(userAFiles.get(0).intValue())); // File kept
        }

        @Test @DisplayName("SCENE 13. 이미지 교체")
        void update_ReplaceImage() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("fileId", userAFiles.get(1)))
                    .patch("/api/v1/tickets/{id}", ticketId)
                    .then().statusCode(200)
                    .body("file.fileId", equalTo(userAFiles.get(1).intValue()));

            Ticket t = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(t.getFile().getId()).isEqualTo(userAFiles.get(1));
        }

        @Test @DisplayName("SCENE 14. 이미지 삭제")
        void update_DeleteImage() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("deleteFile", true))
                    .patch("/api/v1/tickets/{id}", ticketId)
                    .then().statusCode(200)
                    .body("file", nullValue());

            Ticket t = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(t.getFile()).isNull();
            assertThat(t.getOriginalKey()).isNull();
        }

        @Test @DisplayName("SCENE 15. 타인 수정")
        void update_Forbidden() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("title", "Hack"))
                    .patch("/api/v1/tickets/{id}", ticketId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 16. IDOR")
        void update_IDOR() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("fileId", userCFiles.get(0)))
                    .patch("/api/v1/tickets/{id}", ticketId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Ticket
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 티켓 삭제")
    class DeleteTicket {
        private Long ticketId;

        @BeforeEach
        void init() {
            ticketRepository.deleteAll();
            ticketId = TicketSteps.createWithFile(tokenUserA, publicArchiveId, "Del", null);
        }

        @Test @DisplayName("SCENE 17. 정상 삭제")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/tickets/{id}", ticketId).then().statusCode(204);
            assertThat(ticketRepository.existsById(ticketId)).isFalse();
        }

        @Test @DisplayName("SCENE 18. 타인 삭제")
        void delete_Forbidden() {
            given().cookie("ATK", tokenUserC).delete("/api/v1/tickets/{id}", ticketId).then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 5]. Update TicketBook Title
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 티켓북 제목 수정")
    class UpdateBookTitle {
        @Test @DisplayName("SCENE 19. 정상 수정")
        void updateTitle() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(Map.of("title", "2024"))
                    .patch("/api/v1/tickets/book/{id}", publicArchiveId).then().statusCode(200)
                    .body("updatedTitle", equalTo("2024"));

            TicketBook book = ticketBookRepository.findById(publicArchiveId).orElseThrow();
            assertThat(book.getTitle()).isEqualTo("2024");
        }

        @Test @DisplayName("SCENE 20. 타인 수정")
        void updateTitle_Forbidden() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON).body(Map.of("title", "Hack"))
                    .patch("/api/v1/tickets/book/{id}", publicArchiveId)
                    .then()
                    .statusCode(403)
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }
    }

    // ========================================================================================
    // [Category 6]. Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] 티켓 목록 조회")
    class TicketList {
        @BeforeEach
        void setUpData() {
            ticketRepository.deleteAll();
            // Create 3 tickets with different dates
            TicketSteps.createWithDate(tokenUserA, publicArchiveId, "T1", "2024-01-01T19:00:00");
            TicketSteps.createWithDate(tokenUserA, publicArchiveId, "T2", "2024-01-03T19:00:00");
            TicketSteps.createWithDate(tokenUserA, publicArchiveId, "T3", "2024-01-02T19:00:00");
        }

        @Test @DisplayName("SCENE 21. 페이징 및 정렬")
        void list_Sort() {
            // [Fix] totalElements -> page.totalElements (페이지 래퍼 구조 반영)
            // DESC (Latest first): T2(03) -> T3(02) -> T1(01)
            given().cookie("ATK", tokenUserA)
                    .param("page", 0).param("size", 10).param("sort", "date").param("direction", "DESC")
                    .get("/api/v1/tickets/book/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("page.totalElements", equalTo(3)) // Root -> Page Wrapper
                    .body("content[0].title", equalTo("T2"))
                    .body("content[1].title", equalTo("T3"))
                    .body("content[2].title", equalTo("T1"));

            // ASC (Oldest first): T1 -> T3 -> T2
            given().cookie("ATK", tokenUserA)
                    .param("sort", "date").param("direction", "ASC")
                    .get("/api/v1/tickets/book/{id}", publicArchiveId)
                    .then().statusCode(200)
                    .body("content[0].title", equalTo("T1"));
        }

        @Test @DisplayName("SCENE 22. 권한 필터링")
        void list_Restricted() {
            TicketSteps.createWithDate(tokenUserA, restrictedArchiveId, "R1", "2024-01-01T00:00:00");

            given().cookie("ATK", tokenUserB).get("/api/v1/tickets/book/{id}", restrictedArchiveId).then().statusCode(200);
            given().cookie("ATK", tokenUserC).get("/api/v1/tickets/book/{id}", restrictedArchiveId).then().statusCode(403);
        }

        @Test @DisplayName("SCENE 23. 빈 결과")
        void list_Empty() {
            ticketRepository.deleteAll();
            given().cookie("ATK", tokenUserA).get("/api/v1/tickets/book/{id}", publicArchiveId)
                    .then().statusCode(200).body("content", empty());
        }

        @Test @DisplayName("SCENE 24. 존재하지 않는 아카이브")
        void list_NotFound() {
            given().cookie("ATK", tokenUserA).get("/api/v1/tickets/book/{id}", 99999).then().statusCode(404);
        }
    }

    // --- Helpers ---
    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String e, String n, String p) {
            boolean isExist = given().param("email", e).get("/api/v1/auth/email-exist").then().statusCode(200).extract().jsonPath().getBoolean("isExist");
            int id = 0;
            if (!isExist) {
                try { RestAssured.given().delete(ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages"); } catch (Exception ignored) {}
                given().param("email", e).post("/api/v1/auth/email/send").then().statusCode(202);
                String c = null;
                for(int i=0; i<10; i++) {
                    try { Thread.sleep(1000); c = getVerificationCode(e); if(c != null) break; } catch (Exception ignored) {}
                }
                given().contentType(ContentType.JSON).body(Map.of("email", e, "code", c, "purpose", "SIGNUP")).post("/api/v1/auth/email/verify").then().statusCode(200);
                id = given().contentType(ContentType.JSON).body(Map.of("email", e, "nickname", n, "password", p)).post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");
            }
            Response l = given().contentType(ContentType.JSON).body(Map.of("email", e, "password", p)).post("/api/v1/auth/login");
            String atk = l.getCookie("ATK");
            if (id == 0) id = given().cookie("ATK", atk).get("/api/v1/users/me").then().statusCode(200).extract().jsonPath().getInt("id");
            return Map.of("accessToken", atk, "userId", id);
        }
        static String getVerificationCode(String e) {
            try {
                Response r = RestAssured.given().get(ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages");
                List<Map<String, Object>> msgs = r.jsonPath().getList("items");
                if (msgs != null) {
                    for (Map<String, Object> m : msgs) {
                        if (m.toString().contains(e)) {
                            Matcher mat = Pattern.compile("\\d{6}").matcher(((Map) m.get("Content")).get("Body").toString());
                            if (mat.find()) return mat.group();
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    static class ArchiveSteps {
        static Long create(String t, String n, String v) {
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("title", n, "visibility", v)).post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class FileSteps {
        static Long uploadFile(String t) {
            Response i = given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("originalFileName", "t.jpg", "mimeType", "image/jpeg", "fileSize", 100, "mediaRole", "CONTENT")).post("/api/v1/files/multipart/initiate");
            return given().cookie("ATK", t).contentType(ContentType.JSON).body(Map.of("key", i.jsonPath().getString("key"), "uploadId", i.jsonPath().getString("uploadId"), "parts", List.of(Map.of("partNumber", 1, "etag", "e")), "originalFileName", "t.jpg", "fileSize", 100, "mimeType", "image/jpeg", "mediaRole", "CONTENT", "sequence", 0)).post("/api/v1/files/multipart/complete").then().statusCode(200).extract().jsonPath().getLong("fileId");
        }
    }

    static class FriendSteps {
        static void makeFriendDirectly(UserRepository ur, FriendMapRepository fr, Long a, Long b) {
            User ua = ur.findById(a).get(); User ub = ur.findById(b).get();
            fr.save(com.depth.deokive.domain.friend.entity.FriendMap.builder().user(ua).friend(ub).requestedBy(ua).friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
            fr.save(com.depth.deokive.domain.friend.entity.FriendMap.builder().user(ub).friend(ua).requestedBy(ua).friendStatus(com.depth.deokive.domain.friend.entity.enums.FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
        }
    }

    static class TicketSteps {
        static Long createWithFile(String token, Long aid, String title, Long fid) {
            Map<String, Object> req = new HashMap<>();
            req.put("title", title); req.put("date", "2024-01-01T00:00:00");
            if(fid != null) req.put("fileId", fid);
            return given().cookie("ATK", token).contentType(ContentType.JSON).body(req).post("/api/v1/tickets/{id}", aid).then().statusCode(201).extract().jsonPath().getLong("id");
        }
        static Long createWithDate(String token, Long aid, String title, String date) {
            return given().cookie("ATK", token).contentType(ContentType.JSON).body(Map.of("title", title, "date", date))
                    .post("/api/v1/tickets/{id}", aid).then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }
}