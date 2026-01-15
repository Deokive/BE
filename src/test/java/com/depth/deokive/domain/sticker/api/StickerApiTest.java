package com.depth.deokive.domain.sticker.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.s3.dto.S3ServiceDto;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Sticker API 통합 테스트 시나리오 (Full Coverage)")
class StickerApiTest extends ApiTestSupport {

    @Autowired private StickerRepository stickerRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;

    private static String tokenUserA; // Owner
    private static String tokenUserB; // Friend
    private static Long userAId, userBId;
    private static Long archiveId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        if (tokenUserA == null) {
            // Mock S3
            when(s3Service.initiateUpload(any())).thenReturn(S3ServiceDto.UploadInitiateResponse.builder().build());

            // Users Setup
            Map<String, Object> userA = AuthSteps.registerAndLogin("sticker.api.a@test.com", "StickerA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("sticker.api.b@test.com", "StickerB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            // Friend
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // Archive
            archiveId = ArchiveSteps.create(tokenUserA, "Sticker API Archive", "PUBLIC");
        }
    }

    // ========================================================================================
    // [Category 1]. Create (SCENE 1~5)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 스티커 생성")
    class Create {
        @BeforeEach
        void clean() { stickerRepository.deleteAll(); }

        @Test
        @DisplayName("SCENE 1: 정상 생성")
        void create_Normal() {
            Map<String, Object> req = Map.of("date", "2024-05-05", "stickerType", "HEART");

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("id", notNullValue())
                    .body("date", equalTo("2024-05-05"))
                    .body("stickerType", equalTo("HEART"));
        }

        @Test
        @DisplayName("SCENE 2: 예외 - 중복 날짜 (409)")
        void create_Duplicate() {
            StickerSteps.create(tokenUserA, archiveId, "2024-05-05", "HEART");
            Map<String, Object> req = Map.of("date", "2024-05-05", "stickerType", "STAR");

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then()
                    .statusCode(HttpStatus.CONFLICT.value())
                    .body("error", equalTo("STICKER_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("SCENE 3: 예외 - 필수값 누락 (400) - 날짜 없음")
        void create_BadRequest_NoDate() {
            Map<String, Object> req = Map.of("stickerType", "HEART");

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 4: 예외 - 필수값 누락 (400) - 타입 없음")
        void create_BadRequest_NoType() {
            Map<String, Object> req = Map.of("date", "2024-05-05");

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 5: 예외 - 잘못된 Enum 타입 (400)")
        void create_BadRequest_InvalidEnum() {
            Map<String, Object> req = Map.of("date", "2024-05-05", "stickerType", "INVALID_TYPE");

            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }
    }

    // [Category 2]. Update (SCENE 6~8)
    @Nested
    @DisplayName("[Category 2] 스티커 수정")
    class Update {
        private Long stickerId;

        @BeforeEach
        void init() {
            stickerRepository.deleteAll();
            stickerId = StickerSteps.create(tokenUserA, archiveId, "2024-05-05", "HEART");
        }

        @Test
        @DisplayName("SCENE 6: 정상 수정")
        void update_Normal() {
            Map<String, Object> req = Map.of("stickerType", "STAR");
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .patch("/api/v1/stickers/{id}", stickerId)
                    .then().statusCode(200)
                    .body("stickerType", equalTo("STAR"));
        }

        @Test
        @DisplayName("SCENE 7: 예외 - 날짜 충돌 (409)")
        void update_Duplicate() {
            StickerSteps.create(tokenUserA, archiveId, "2024-05-10", "CIRCLE");
            Map<String, Object> req = Map.of("date", "2024-05-10");

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON).body(req)
                    .patch("/api/v1/stickers/{id}", stickerId)
                    .then().statusCode(409)
                    .body("error", equalTo("STICKER_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("SCENE 8: 예외 - 타인 수정 (403)")
        void update_Forbidden() {
            Map<String, Object> req = Map.of("stickerType", "STAR");
            given().cookie("ATK", tokenUserB).contentType(ContentType.JSON).body(req)
                    .patch("/api/v1/stickers/{id}", stickerId)
                    .then().statusCode(403);
        }
    }

    // [Category 3]. Delete (SCENE 9~10)
    @Nested
    @DisplayName("[Category 3] 스티커 삭제")
    class Delete {
        private Long stickerId;
        @BeforeEach
        void init() {
            stickerRepository.deleteAll();
            stickerId = StickerSteps.create(tokenUserA, archiveId, "2024-05-05", "HEART");
        }

        @Test
        @DisplayName("SCENE 9: 정상 삭제 (204)")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/stickers/{id}", stickerId)
                    .then().statusCode(204);

            assertThat(stickerRepository.existsById(stickerId)).isFalse();
        }

        @Test
        @DisplayName("SCENE 10: 예외 - 존재하지 않는 스티커 (404)")
        void delete_NotFound() {
            given().cookie("ATK", tokenUserA).delete("/api/v1/stickers/{id}", 99999L)
                    .then().statusCode(404);
        }
    }

    // [Category 4]. Monthly Read (SCENE 11~12)
    @Nested
    @DisplayName("[Category 4] 월별 조회")
    class Monthly {
        @BeforeEach
        void init() {
            stickerRepository.deleteAll();
            StickerSteps.create(tokenUserA, archiveId, "2024-05-01", "HEART");
            StickerSteps.create(tokenUserA, archiveId, "2024-05-31", "STAR");
        }

        @Test
        @DisplayName("SCENE 11: 정상 조회 (200)")
        void getMonthly_Normal() {
            given().cookie("ATK", tokenUserA)
                    .param("year", 2024).param("month", 5)
                    .get("/api/v1/stickers/monthly/{archiveId}", archiveId)
                    .then().statusCode(200)
                    .body("size()", equalTo(2));
        }

        @Test
        @DisplayName("SCENE 12: 빈 결과 조회 (200)")
        void getMonthly_Empty() {
            given().cookie("ATK", tokenUserA)
                    .param("year", 2025).param("month", 1)
                    .get("/api/v1/stickers/monthly/{archiveId}", archiveId)
                    .then().statusCode(200)
                    .body("size()", equalTo(0));
        }
    }

    // --- Helpers ---
    static class StickerSteps {
        static Long create(String token, Long archiveId, String date, String type) {
            Map<String, Object> req = Map.of("date", date, "stickerType", type);
            return given().cookie("ATK", token)
                    .contentType(ContentType.JSON)
                    .body(req)
                    .post("/api/v1/stickers/{archiveId}", archiveId)
                    .then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class FriendSteps {
        static void makeFriendDirectly(UserRepository uRepo, FriendMapRepository fRepo, Long uA, Long uB) {
            com.depth.deokive.domain.user.entity.User A = uRepo.findById(uA).get();
            com.depth.deokive.domain.user.entity.User B = uRepo.findById(uB).get();
            fRepo.save(FriendMap.builder().user(A).friend(B).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(java.time.LocalDateTime.now()).build());
            fRepo.save(FriendMap.builder().user(B).friend(A).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(java.time.LocalDateTime.now()).build());
        }
    }

    static class ArchiveSteps {
        static Long create(String token, String n, String v) {
            return given().cookie("ATK", token).contentType(ContentType.JSON).body(Map.of("title", n, "visibility", v)).post("/api/v1/archives").then().statusCode(201).extract().jsonPath().getLong("id");
        }
    }

    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String e, String n, String p) {
            // 1. 이메일 중복 확인
            boolean isExist = given().param("email", e)
                    .get("/api/v1/auth/email-exist")
                    .then().statusCode(200)
                    .extract().jsonPath().getBoolean("isExist");

            int id = 0;

            // 2. 존재하지 않으면 회원가입 진행
            if (!isExist) {
                String url = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
                try { RestAssured.given().delete(url); } catch (Exception ignored) {}

                given().param("email", e).post("/api/v1/auth/email/send").then().statusCode(202);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                String code = getCode(e, url);
                given().contentType(ContentType.JSON).body(Map.of("email", e, "code", code, "purpose", "SIGNUP"))
                        .post("/api/v1/auth/email/verify").then().statusCode(200);

                id = given().contentType(ContentType.JSON).body(Map.of("email", e, "nickname", n, "password", p))
                        .post("/api/v1/auth/register").then().statusCode(200).extract().jsonPath().getInt("id");
            }

            // 3. 로그인 및 토큰 획득
            Response l = given().contentType(ContentType.JSON).body(Map.of("email", e, "password", p))
                    .post("/api/v1/auth/login");
            String atk = l.getCookie("ATK");

            // 4. (이미 존재했다면) ID 조회 필요
            if (id == 0) {
                id = given().cookie("ATK", atk).get("/api/v1/users/me")
                        .then().statusCode(200).extract().jsonPath().getInt("id");
            }

            return Map.of("accessToken", atk, "userId", id);
        }

        static String getCode(String e, String url) {
            for(int i=0; i<20; i++) { // 재시도 횟수 증가
                try {
                    List<Map<String, Object>> msgs = RestAssured.given().get(url).jsonPath().getList("items");
                    if(msgs != null) for(Map m : msgs) if(m.toString().contains(e)) {
                        Matcher matcher = Pattern.compile("\\d{6}").matcher(((Map)m.get("Content")).get("Body").toString());
                        if(matcher.find()) return matcher.group();
                    }
                    Thread.sleep(500);
                } catch(Exception ignored){}
            }
            throw new RuntimeException("MailHog Verification Code Not Found for " + e);
        }
    }
}