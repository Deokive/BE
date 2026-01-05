package com.depth.deokive.common.api.event;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.entity.Event;
import com.depth.deokive.domain.event.entity.SportRecord;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.event.repository.HashtagRepository;
import com.depth.deokive.domain.event.repository.SportRecordRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

@DisplayName("Event API 통합 테스트 시나리오")
class EventApiTest extends ApiTestSupport {

    // --- Repositories ---
    @Autowired private EventRepository eventRepository;
    @Autowired private SportRecordRepository sportRecordRepository;
    @Autowired private HashtagRepository hashtagRepository;
    @Autowired private EventHashtagMapRepository eventHashtagMapRepository;
    @Autowired private ArchiveRepository archiveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendMapRepository friendMapRepository;

    // --- Actors (Token) ---
    private static String tokenUserA; // Me (Owner)
    private static String tokenUserB; // Friend
    private static String tokenUserC; // Stranger

    // --- Shared Data ---
    private static Long userAId;
    private static Long userBId;

    // --- Fixtures for UserA ---
    private static Long publicArchiveId;
    private static Long restrictedArchiveId;
    private static Long privateArchiveId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // [S3 Mocking] - Event 도메인에선 S3를 직접 쓰진 않지만, Archive 생성 시 내부적으로 사용될 수 있으므로 설정
        when(s3Service.initiateUpload(any())).thenAnswer(invocation -> S3ServiceDto.UploadInitiateResponse.builder().build());
        when(s3Service.calculatePartCount(any())).thenReturn(1);
        when(s3Service.generatePartPresignedUrls(any())).thenReturn(List.of());
        when(s3Service.completeUpload(any())).thenAnswer(invocation -> software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse.builder().build());

        // [Global Setup] 최초 1회 실행
        if (tokenUserA == null) {
            // 1. Users
            Map<String, Object> userA = AuthSteps.registerAndLogin("event.a@test.com", "EventA", "Password123!");
            tokenUserA = (String) userA.get("accessToken");
            userAId = ((Number) userA.get("userId")).longValue();

            Map<String, Object> userB = AuthSteps.registerAndLogin("event.b@test.com", "EventB", "Password123!");
            tokenUserB = (String) userB.get("accessToken");
            userBId = ((Number) userB.get("userId")).longValue();

            Map<String, Object> userC = AuthSteps.registerAndLogin("event.c@test.com", "EventC", "Password123!");
            tokenUserC = (String) userC.get("accessToken");

            // 2. Friend (A-B)
            FriendSteps.makeFriendDirectly(userRepository, friendMapRepository, userAId, userBId);

            // 3. Archives (For UserA)
            publicArchiveId = ArchiveSteps.create(tokenUserA, "E_Public", "PUBLIC");
            restrictedArchiveId = ArchiveSteps.create(tokenUserA, "E_Restricted", "RESTRICTED");
            privateArchiveId = ArchiveSteps.create(tokenUserA, "E_Private", "PRIVATE");
        }
    }

    // ========================================================================================
    // [Category 1]. Create Event (POST /api/v1/events/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 이벤트 생성")
    class CreateEvent {

        @Test
        @DisplayName("SCENE 1. 정상 생성 - 일반 이벤트 (시간, 해시태그 포함)")
        void createEvent_Normal() {
            // Given
            Map<String, Object> request = Map.of(
                    "title", "콘서트",
                    "date", "2024-01-01",
                    "hasTime", true,
                    "time", "14:30:00",
                    "color", "#FF5733",
                    "hashtags", List.of("concert", "live")
            );

            // When
            int eventId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/events/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    // Then: Response Validation
                    .body("id", notNullValue())
                    .body("title", equalTo("콘서트"))
                    .body("date", equalTo("2024-01-01"))
                    .body("hasTime", equalTo(true))
                    .body("time", equalTo("14:30"))
                    .body("hashtags", hasItems("concert", "live"))
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation
            Event event = eventRepository.findById((long) eventId).orElseThrow();
            assertThat(event.getTitle()).isEqualTo("콘서트");
            assertThat(event.isHasTime()).isTrue();
            assertThat(event.getDate().toLocalTime()).isEqualTo(LocalTime.of(14, 30));
            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId((long) eventId)).hasSize(2);
        }

        @Test
        @DisplayName("SCENE 2. 정상 생성 - 시간 없는 이벤트 (All Day)")
        void createEvent_NoTime() {
            // Given: hasTime=false
            Map<String, Object> request = Map.of(
                    "title", "All Day Event",
                    "date", "2024-01-01",
                    "hasTime", false,
                    "color", "#FF5733"
            );

            // When
            int eventId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/events/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("hasTime", equalTo(false))
                    .body("time", nullValue())
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation
            Event event = eventRepository.findById((long) eventId).orElseThrow();
            assertThat(event.isHasTime()).isFalse();
            // 시간은 00:00:00으로 저장되어야 함 (LocalDateTime 특성상)
            assertThat(event.getDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 3. 정상 생성 - 스포츠 이벤트")
        void createEvent_Sport() {
            // Given
            Map<String, Object> sportInfo = Map.of("team1", "A", "team2", "B", "score1", 1, "score2", 0);
            Map<String, Object> request = Map.of(
                    "title", "야구 경기",
                    "date", "2024-01-01",
                    "color", "#FF5733",
                    "isSportType", true,
                    "sportInfo", sportInfo
            );

            // When
            int eventId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/events/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("isSportType", equalTo(true))
                    .body("sportInfo.team1", equalTo("A"))
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation
            Event event = eventRepository.findById((long) eventId).orElseThrow();
            assertThat(event.isSportType()).isTrue();
            SportRecord record = sportRecordRepository.findById((long) eventId).orElseThrow();
            assertThat(record.getTeam1()).isEqualTo("A");
            assertThat(record.getScore1()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 4. 정상 생성 - 중복 해시태그 처리")
        void createEvent_DuplicateTags() {
            // Given
            Map<String, Object> request = Map.of(
                    "title", "Dup Tags",
                    "date", "2024-01-01",
                    "color", "#FF5733",
                    "hashtags", List.of("tag1", "tag1", "tag2")
            );

            // When
            int eventId = given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/events/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .extract().jsonPath().getInt("id");

            // Then: DB Validation (중복 제거되어 2개만 저장)
            List<String> tags = eventHashtagMapRepository.findHashtagNamesByEventId((long) eventId);
            assertThat(tags).hasSize(2).containsExactlyInAnyOrder("tag1", "tag2");
        }

        @Test
        @DisplayName("SCENE 5. 예외 - 필수값 누락")
        void createEvent_BadRequest() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("date", "2024-01-01")) // title 없음
                    .when()
                    .post("/api/v1/events/{archiveId}", publicArchiveId)
                    .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        @DisplayName("SCENE 6. 예외 - 타인 아카이브 생성 시도")
        void createEvent_Forbidden() {
            // Stranger creates Archive
            Long strangerArchiveId = ArchiveSteps.create(tokenUserC, "C_Pub", "PUBLIC");

            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack", "date", "2024-01-01", "color", "#000"))
                    .when()
                    .post("/api/v1/events/{archiveId}", strangerArchiveId)
                    .then()
                    .statusCode(HttpStatus.FORBIDDEN.value());
        }

        @Test
        @DisplayName("SCENE 7. 예외 - 존재하지 않는 아카이브")
        void createEvent_NotFound() {
            given()
                    .cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Fail", "date", "2024-01-01", "color", "#000"))
                    .when()
                    .post("/api/v1/events/{archiveId}", 999999)
                    .then()
                    .statusCode(HttpStatus.NOT_FOUND.value());
        }
    }

    // ========================================================================================
    // [Category 2]. Read Detail
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 이벤트 상세 조회")
    class ReadEvent {
        private Long normalEventId;
        private Long sportEventId;
        private Long restrictedEventId;
        private Long privateEventId;

        @BeforeEach
        void initEvents() {
            // Public Archive Events
            normalEventId = EventSteps.create(tokenUserA, publicArchiveId, "Normal", false, false);
            sportEventId = EventSteps.create(tokenUserA, publicArchiveId, "Sport", true, true);

            // Restricted Archive Event
            restrictedEventId = EventSteps.create(tokenUserA, restrictedArchiveId, "Restricted", false, false);

            // Private Archive Event
            privateEventId = EventSteps.create(tokenUserA, privateArchiveId, "Private", false, false);
        }

        @Test
        @DisplayName("SCENE 8. PUBLIC 일반 이벤트 조회")
        void readPublic_Normal() {
            // Stranger Read
            given().cookie("ATK", tokenUserC)
                    .when().get("/api/v1/events/{id}", normalEventId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("Normal"))
                    .body("sportInfo", nullValue());
        }

        @Test
        @DisplayName("SCENE 9. PUBLIC 스포츠 이벤트 조회")
        void readPublic_Sport() {
            // Stranger Read
            given().cookie("ATK", tokenUserC)
                    .when().get("/api/v1/events/{id}", sportEventId)
                    .then()
                    .statusCode(200)
                    .body("isSportType", equalTo(true))
                    .body("sportInfo", notNullValue());
        }

        @Test
        @DisplayName("SCENE 10~11. RESTRICTED 조회 권한")
        void readRestricted() {
            // 10: Friend -> OK
            given().cookie("ATK", tokenUserB).get("/api/v1/events/{id}", restrictedEventId).then().statusCode(200);

            // 11: Stranger -> Forbidden
            given().cookie("ATK", tokenUserC).get("/api/v1/events/{id}", restrictedEventId).then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 12. PRIVATE 조회 - 타인 실패")
        void readPrivate() {
            given().cookie("ATK", tokenUserC).get("/api/v1/events/{id}", privateEventId).then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 13. 존재하지 않는 이벤트")
        void readNotFound() {
            given().cookie("ATK", tokenUserA).get("/api/v1/events/{id}", 999999).then().statusCode(404);
        }
    }

    // ========================================================================================
    // [Category 3]. Update Event
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 이벤트 수정")
    class UpdateEvent {
        private Long eventId;

        @BeforeEach
        void init() {
            // Normal Event (Time=True, Sport=False)
            eventId = EventSteps.create(tokenUserA, publicArchiveId, "Original", false, true);
        }

        @Test
        @DisplayName("SCENE 14. 정상 수정 - 기본 정보")
        void update_Info() {
            given().cookie("ATK", tokenUserA)
                    .contentType(ContentType.JSON)
                    .body(Map.of("title", "Updated", "color", "#000000"))
                    .when().patch("/api/v1/events/{id}", eventId)
                    .then().statusCode(200)
                    .body("title", equalTo("Updated"))
                    .body("color", equalTo("#000000"));
        }

        @Test
        @DisplayName("SCENE 15. 로직 - 시간 끄기")
        void update_TurnOffTime() {
            // hasTime: True -> False
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("hasTime", false))
                    .when().patch("/api/v1/events/{id}", eventId)
                    .then().statusCode(200)
                    .body("hasTime", equalTo(false))
                    .body("time", nullValue());
        }

        @Test
        @DisplayName("SCENE 17. 로직 - 스포츠 끄기")
        void update_TurnOffSport() {
            // Setup Sport Event first
            Long sportId = EventSteps.create(tokenUserA, publicArchiveId, "Sport", true, true);

            // Turn Off
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("isSportType", false))
                    .when().patch("/api/v1/events/{id}", sportId)
                    .then().statusCode(200)
                    .body("isSportType", equalTo(false))
                    .body("sportInfo", nullValue());

            assertThat(sportRecordRepository.existsById(sportId)).isFalse();
        }

        @Test
        @DisplayName("SCENE 18. 로직 - 스포츠 켜기")
        void update_TurnOnSport() {
            Map<String, Object> sportInfo = Map.of("team1", "A", "team2", "B", "score1", 1, "score2", 0);

            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("isSportType", true, "sportInfo", sportInfo))
                    .when().patch("/api/v1/events/{id}", eventId)
                    .then().statusCode(200)
                    .body("isSportType", equalTo(true))
                    .body("sportInfo.team1", equalTo("A"));

            assertThat(sportRecordRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("SCENE 19. 로직 - 해시태그 교체")
        void update_ReplaceHashtags() {
            given().cookie("ATK", tokenUserA).contentType(ContentType.JSON)
                    .body(Map.of("hashtags", List.of("new1", "new2")))
                    .when().patch("/api/v1/events/{id}", eventId)
                    .then().statusCode(200)
                    .body("hashtags", hasSize(2));

            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId(eventId))
                    .containsExactlyInAnyOrder("new1", "new2");
        }

        @Test
        @DisplayName("SCENE 21. 예외 - 타인 수정")
        void update_Forbidden() {
            given().cookie("ATK", tokenUserC).contentType(ContentType.JSON)
                    .body(Map.of("title", "Hack"))
                    .when().patch("/api/v1/events/{id}", eventId)
                    .then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 4]. Delete Event
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 이벤트 삭제")
    class DeleteEvent {
        private Long eventId;

        @BeforeEach
        void init() {
            // Sport Event with hashtags
            eventId = EventSteps.create(tokenUserA, publicArchiveId, "Del", true, true);
        }

        @Test
        @DisplayName("SCENE 22. 정상 삭제 - 연관 데이터 포함")
        void delete_Normal() {
            given().cookie("ATK", tokenUserA)
                    .when().delete("/api/v1/events/{id}", eventId)
                    .then().statusCode(204);

            assertThat(eventRepository.existsById(eventId)).isFalse();
            assertThat(sportRecordRepository.existsById(eventId)).isFalse();
            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId(eventId)).isEmpty();
        }

        @Test
        @DisplayName("SCENE 23. 예외 - 타인 삭제")
        void delete_Forbidden() {
            given().cookie("ATK", tokenUserC)
                    .when().delete("/api/v1/events/{id}", eventId)
                    .then().statusCode(403);
        }
    }

    // ========================================================================================
    // [Category 5]. Monthly List (GET /api/v1/events/monthly/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 월별 이벤트 조회")
    class MonthlyEvents {
        private final int YEAR = 2024;
        private final int MONTH = 5;

        // 독립적인 아카이브 ID 사용
        private Long monthlyPublicArchiveId;
        private Long monthlyRestrictedArchiveId;

        @BeforeEach
        void setUpMonthlyData() {
            // [중요] 테스트 간 데이터 간섭 방지를 위해 새로운 아카이브 생성
            monthlyPublicArchiveId = ArchiveSteps.create(tokenUserA, "Monthly_Pub", "PUBLIC");
            monthlyRestrictedArchiveId = ArchiveSteps.create(tokenUserA, "Monthly_Res", "RESTRICTED");

            // 4/30, 5/1, 5/15, 5/31, 6/1 생성 (새로 만든 아카이브에 연결)
            EventSteps.createDirectly(userRepository, archiveRepository, eventRepository, userAId, monthlyPublicArchiveId, LocalDate.of(YEAR, 4, 30));
            EventSteps.createDirectly(userRepository, archiveRepository, eventRepository, userAId, monthlyPublicArchiveId, LocalDate.of(YEAR, 5, 1));
            EventSteps.createDirectly(userRepository, archiveRepository, eventRepository, userAId, monthlyPublicArchiveId, LocalDate.of(YEAR, 5, 15));
            EventSteps.createDirectly(userRepository, archiveRepository, eventRepository, userAId, monthlyPublicArchiveId, LocalDate.of(YEAR, 5, 31));
            EventSteps.createDirectly(userRepository, archiveRepository, eventRepository, userAId, monthlyPublicArchiveId, LocalDate.of(YEAR, 6, 1));
        }

        @Test
        @DisplayName("SCENE 24. 정상 조회 - 5월 데이터 조회 (경계값 검증)")
        void getMonthly_Normal() {
            // 5/1, 5/15, 5/31 만 조회되어야 함 (총 3개)
            given().cookie("ATK", tokenUserA)
                    .param("year", YEAR).param("month", MONTH)
                    .when().get("/api/v1/events/monthly/{archiveId}", monthlyPublicArchiveId) // 수정된 ID 사용
                    .then().statusCode(200)
                    .body("size()", equalTo(3))
                    .body("date", hasItems("2024-05-01", "2024-05-15", "2024-05-31"))
                    .body("date", not(hasItems("2024-04-30", "2024-06-01")));
        }

        @Test
        @DisplayName("SCENE 25. 권한 필터링 - RESTRICTED 아카이브")
        void getMonthly_Restricted() {
            // Stranger Access to Restricted Archive
            given().cookie("ATK", tokenUserC)
                    .param("year", YEAR).param("month", MONTH)
                    .when().get("/api/v1/events/monthly/{archiveId}", monthlyRestrictedArchiveId) // 수정된 ID 사용
                    .then().statusCode(403);
        }

        @Test
        @DisplayName("SCENE 26. 빈 달 조회")
        void getMonthly_Empty() {
            given().cookie("ATK", tokenUserA)
                    .param("year", YEAR).param("month", 12)
                    .when().get("/api/v1/events/monthly/{archiveId}", monthlyPublicArchiveId) // 수정된 ID 사용
                    .then().statusCode(200)
                    .body("size()", equalTo(0));
        }

        @Test
        @DisplayName("SCENE 27. 해시태그 Fetch Join 검증")
        void getMonthly_FetchJoin() {
            given().cookie("ATK", tokenUserA)
                    .param("year", YEAR).param("month", MONTH)
                    .when().get("/api/v1/events/monthly/{archiveId}", monthlyPublicArchiveId)
                    .then().statusCode(200)
                    .body("hashtags", notNullValue());
        }
    }

    // ========================================================================================
    // Helper Steps
    // ========================================================================================
    static class EventSteps {
        static Long create(String token, Long archiveId, String title, boolean isSport, boolean hasTime) {
            Map<String, Object> req = new java.util.HashMap<>(Map.of(
                    "title", title,
                    "date", "2024-01-01",
                    "color", "#000",
                    "isSportType", isSport,
                    "hasTime", hasTime
            ));
            if(hasTime) req.put("time", "12:00:00");
            if(isSport) req.put("sportInfo", Map.of("team1", "A", "team2", "B", "score1", 1, "score2", 0));

            return given().cookie("ATK", token).contentType(ContentType.JSON).body(req)
                    .post("/api/v1/events/{archiveId}", archiveId)
                    .then().statusCode(201).extract().jsonPath().getLong("id");
        }

        // Setup for Monthly (Direct DB Insert to avoid API overhead)
        static void createDirectly(UserRepository uRepo, ArchiveRepository aRepo, EventRepository eRepo, Long userId, Long archiveId, LocalDate date) {
            Event event = Event.builder()
                    .title("Boundary")
                    .date(date.atStartOfDay())
                    .color("#000")
                    .hasTime(false)
                    .archive(aRepo.findById(archiveId).get())
                    .build();
            eRepo.save(event);
        }
    }

    // ... (AuthSteps, ArchiveSteps, FileSteps, FriendSteps는 ArchiveApiTest와 동일) ...
    // 복사하여 사용하거나 공통 클래스로 분리 가능
    static class AuthSteps {
        static Map<String, Object> registerAndLogin(String email, String nickname, String password) {
            String mailhogUrl = ApiTestSupport.MAILHOG_HTTP_URL + "/api/v2/messages";
            try { RestAssured.given().delete(mailhogUrl); } catch (Exception ignored) {}
            given().param("email", email).post("/api/v1/auth/email/send").then().statusCode(202);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
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
    static class FriendSteps {
        static void makeFriendDirectly(UserRepository uRepo, FriendMapRepository fRepo, Long uA, Long uB) {
            User A = uRepo.findById(uA).orElseThrow();
            User B = uRepo.findById(uB).orElseThrow();
            fRepo.save(FriendMap.builder().user(A).friend(B).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
            fRepo.save(FriendMap.builder().user(B).friend(A).requestedBy(A).friendStatus(FriendStatus.ACCEPTED).acceptedAt(LocalDateTime.now()).build());
        }
    }
}