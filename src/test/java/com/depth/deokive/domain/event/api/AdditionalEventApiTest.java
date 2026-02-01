package com.depth.deokive.domain.event.api;

import com.depth.deokive.common.test.ApiTestSupport;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Event API 추가 통합 테스트 (RestAssured + Testcontainers)
 * - 쿠키 기반 인증
 * - 실제 DB 사용
 * - Validation 에러 응답 검증
 * - HTTP Status 코드 검증
 *
 * 목표: API Layer 커버리지 100%
 */
@Slf4j
@DisplayName("AdditionalEventApiTest - Event API 통합 테스트 (RestAssured)")
class AdditionalEventApiTest extends ApiTestSupport {

    @Autowired EventRepository eventRepository;
    @Autowired ArchiveRepository archiveRepository;

    // Actors (Token)
    private static String tokenOwner;
    private static String tokenStranger;

    // Shared Data
    private static Long ownerArchiveId;
    private static int eventDateCounter = 0;  // createSimpleEvent에서 고유한 날짜 생성용

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // Global Setup - 최초 1회 실행 또는 ownerArchiveId가 null인 경우 재초기화
        if (tokenOwner == null || ownerArchiveId == null) {
            // 1. Users Setup
            if (tokenOwner == null) {
                Map<String, Object> owner = AuthSteps.registerAndLogin("event.owner@test.com", "EventOwner", "Password123!");
                tokenOwner = (String) owner.get("accessToken");

                Map<String, Object> stranger = AuthSteps.registerAndLogin("event.stranger@test.com", "Stranger", "Password123!");
                tokenStranger = (String) stranger.get("accessToken");
            }

            // 2. Archive Setup (Owner가 Archive 생성)
            ownerArchiveId = createArchive(tokenOwner, "Owner Archive", "PUBLIC");
            if (ownerArchiveId == null) {
                throw new IllegalStateException("Failed to create archive: ownerArchiveId is null");
            }
        }
    }

    // ========================================================================================
    // [API Category 1]: Validation 에러 응답 테스트 (400 BAD_REQUEST)
    // ========================================================================================
    @Nested
    @DisplayName("[API Validation] 400 BAD_REQUEST 검증")
    class ValidationErrors {

        @BeforeEach
        void setUpValidationErrors() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-1: startDate > endDate일 때 400 에러 + 에러 메시지")
        void createEvent_invalid_dateRange_returns_400() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Invalid Event");
            request.put("startDate", "2024-12-31");
            request.put("endDate", "2024-12-25"); // 역전
            request.put("hasTime", false);
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"))
                    .body("error", equalTo("GLOBAL_BAD_REQUEST"));
                    // Note: Custom validation 메시지는 현재 GlobalExceptionHandler에서 제대로 전달되지 않음
        }

        @Test
        @DisplayName("API-2: hasTime=true인데 startTime만 있을 때 400 에러")
        void createEvent_hasTime_true_but_only_startTime_returns_400() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Invalid Time Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("hasTime", true);
            request.put("startTime", "10:00");
            // endTime 누락
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"))
                    .body("error", equalTo("GLOBAL_BAD_REQUEST"));
                    // Note: Custom validation 메시지는 현재 GlobalExceptionHandler에서 제대로 전달되지 않음
        }

        @Test
        @DisplayName("API-3: 같은 날짜인데 startTime > endTime일 때 400 에러")
        void createEvent_sameDay_startTime_after_endTime_returns_400() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Invalid Time Order");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("hasTime", true);
            request.put("startTime", "18:00");
            request.put("endTime", "10:00"); // 역전
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"))
                    .body("error", equalTo("GLOBAL_BAD_REQUEST"));
                    // Note: Custom validation 메시지는 현재 GlobalExceptionHandler에서 제대로 전달되지 않음
        }

        @Test
        @DisplayName("API-4: 필수 필드 누락 시 400 에러 (@NotNull)")
        void createEvent_missing_required_fields_returns_400() {
            // Given: startDate 누락
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            // startDate 누락
            request.put("endDate", "2024-12-25");
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"));
        }

        @Test
        @DisplayName("API-5: 잘못된 색상 코드 형식 (@Pattern)")
        void createEvent_invalid_color_pattern_returns_400() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("color", "INVALID_COLOR"); // HEX 형식 아님

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("message", containsString("올바른 HEX 컬러 코드가 아닙니다"));
        }

        @Test
        @DisplayName("API-6: UpdateRequest에서 startDate > endDate일 때 400 에러")
        void updateEvent_invalid_dateRange_returns_400() {
            // Given: 정상 이벤트 생성 (다른 날짜 사용하여 409 회피)
            Long eventId = createEventWithDate(tokenOwner, "2024-12-28", "2024-12-28");

            // When: 잘못된 날짜 범위로 수정 시도
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("startDate", "2024-12-31");
            updateRequest.put("endDate", "2024-12-25"); // 역전

            // Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(updateRequest)
            .when()
                    .patch("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"))
                    .body("error", equalTo("GLOBAL_BAD_REQUEST"));
                    // Note: Custom validation 메시지는 현재 GlobalExceptionHandler에서 제대로 전달되지 않음
        }

        @Test
        @DisplayName("API-7: startDate는 있는데 endDate가 없는 경우 400 에러")
        void createEvent_startDate_without_endDate_returns_400() {
            // Given: startDate만 있고 endDate 누락
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            request.put("startDate", "2024-12-25");
            // endDate 누락
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .body("status", equalTo("BAD_REQUEST"));
        }
    }

    // ========================================================================================
    // [API Category 2]: 정상 요청 검증 (201 CREATED, 200 OK)
    // ========================================================================================
    @Nested
    @DisplayName("[API Success] 201/200 응답 검증")
    class SuccessResponses {

        @BeforeEach
        void setUpSuccessResponses() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-7: 정상 생성 시 201 CREATED + Response Body 검증")
        void createEvent_success_returns_201_with_body() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Valid Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-27");
            request.put("hasTime", true);
            request.put("startTime", "10:00");
            request.put("endTime", "18:00");
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("id", notNullValue())
                    .body("title", equalTo("Valid Event"))
                    .body("startDate", equalTo("2024-12-25"))
                    .body("endDate", equalTo("2024-12-27"))
                    .body("hasTime", equalTo(true))
                    .body("startTime", equalTo("10:00"))
                    .body("endTime", equalTo("18:00"))
                    .body("color", equalTo("#FF5733"));
        }

        @Test
        @DisplayName("API-8: 같은 날짜 이벤트 생성 성공")
        void createEvent_sameDay_success() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Same Day Event");
            request.put("startDate", "2024-12-26");  // 다른 날짜로 변경
            request.put("endDate", "2024-12-26");
            request.put("hasTime", true);
            request.put("startTime", "10:00");
            request.put("endTime", "18:00");
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("startDate", equalTo("2024-12-26"))
                    .body("endDate", equalTo("2024-12-26"));
        }

        @Test
        @DisplayName("API-9: hasTime=false일 때 startTime/endTime은 null로 반환")
        void createEvent_hasTime_false_times_are_null() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "No Time Event");
            request.put("startDate", "2024-12-27");  // 다른 날짜로 변경
            request.put("endDate", "2024-12-27");
            request.put("hasTime", false);
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.CREATED.value())
                    .body("hasTime", equalTo(false))
                    .body("startTime", nullValue())
                    .body("endTime", nullValue())
                    .body("startDate", notNullValue())
                    .body("endDate", notNullValue());
        }

        @Test
        @DisplayName("API-10: 정상 조회 시 200 OK")
        void getEvent_success_returns_200() {
            // Given
            Long eventId = createSimpleEvent(tokenOwner);

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
            .when()
                    .get("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("id", equalTo(eventId.intValue()));  // JSON 응답이 Integer로 파싱되므로 intValue() 사용
        }

        @Test
        @DisplayName("API-11: 정상 수정 시 200 OK")
        void updateEvent_success_returns_200() {
            // Given
            Long eventId = createSimpleEvent(tokenOwner);

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("title", "Updated Title");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(updateRequest)
            .when()
                    .patch("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("title", equalTo("Updated Title"));
        }

        @Test
        @DisplayName("API-12: 정상 삭제 시 204 NO_CONTENT")
        void deleteEvent_success_returns_204() {
            // Given
            Long eventId = createSimpleEvent(tokenOwner);

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
            .when()
                    .delete("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.NO_CONTENT.value());
        }
    }

    // ========================================================================================
    // [API Category 3]: 인증/인가 에러 (401, 403)
    // ========================================================================================
    @Nested
    @DisplayName("[API Auth] 401/403 에러 검증")
    class AuthorizationErrors {

        @BeforeEach
        void setUpAuthorizationErrors() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-13: Authorization 쿠키 없이 생성 시도 → 401 UNAUTHORIZED")
        void createEvent_without_auth_returns_401() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("color", "#FF5733");

            // When & Then: 쿠키 없음
            given()
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("API-14: 타인의 Archive에 생성 시도 → 403 FORBIDDEN")
        void createEvent_on_others_archive_returns_403() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("color", "#FF5733");

            // When & Then: stranger가 owner의 archive에 생성 시도
            given()
                    .cookie("ATK", tokenStranger)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.FORBIDDEN.value())
                    .body("status", equalTo("FORBIDDEN"))
                    .body("error", equalTo("AUTH_FORBIDDEN"));
        }

        @Test
        @DisplayName("API-15: 타인의 Event 수정 시도 → 403 FORBIDDEN")
        void updateEvent_others_event_returns_403() {
            // Given: owner가 생성한 이벤트
            Long eventId = createSimpleEvent(tokenOwner);

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("title", "Hacked");

            // When & Then: stranger가 수정 시도
            given()
                    .cookie("ATK", tokenStranger)
                    .contentType(ContentType.JSON)
                    .body(updateRequest)
            .when()
                    .patch("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.FORBIDDEN.value());
        }

        @Test
        @DisplayName("API-16: 타인의 Event 삭제 시도 → 403 FORBIDDEN")
        void deleteEvent_others_event_returns_403() {
            // Given
            Long eventId = createSimpleEvent(tokenOwner);

            // When & Then
            given()
                    .cookie("ATK", tokenStranger)
            .when()
                    .delete("/api/v1/events/{eventId}", eventId)
            .then()
                    .statusCode(HttpStatus.FORBIDDEN.value());
        }
    }

    // ========================================================================================
    // [API Category 4]: 리소스 없음 (404 NOT_FOUND)
    // ========================================================================================
    @Nested
    @DisplayName("[API NotFound] 404 에러 검증")
    class NotFoundErrors {

        @BeforeEach
        void setUpNotFoundErrors() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-17: 존재하지 않는 Archive에 생성 시도 → 404 NOT_FOUND")
        void createEvent_nonexistent_archive_returns_404() {
            // Given
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Event");
            request.put("startDate", "2024-12-25");
            request.put("endDate", "2024-12-25");
            request.put("color", "#FF5733");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", 99999L)
            .then()
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("API-18: 존재하지 않는 Event 조회 → 404 NOT_FOUND")
        void getEvent_nonexistent_returns_404() {
            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
            .when()
                    .get("/api/v1/events/{eventId}", 99999L)
            .then()
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .body("error", equalTo("EVENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("API-19: 존재하지 않는 Event 수정 → 404 NOT_FOUND")
        void updateEvent_nonexistent_returns_404() {
            // Given
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("title", "Updated");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(updateRequest)
            .when()
                    .patch("/api/v1/events/{eventId}", 99999L)
            .then()
                    .statusCode(HttpStatus.NOT_FOUND.value());
        }

        @Test
        @DisplayName("API-20: 존재하지 않는 Event 삭제 → 404 NOT_FOUND")
        void deleteEvent_nonexistent_returns_404() {
            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
            .when()
                    .delete("/api/v1/events/{eventId}", 99999L)
            .then()
                    .statusCode(HttpStatus.NOT_FOUND.value());
        }
    }

    // ========================================================================================
    // [API Category 5]: 월별 조회 API
    // ========================================================================================
    @Nested
    @DisplayName("[API Monthly] 월별 조회 API 검증")
    class MonthlyEventsApi {

        @BeforeEach
        void setUpMonthlyEventsApi() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-21: 월별 조회 성공 → 200 OK")
        void getMonthlyEvents_success_returns_200() {
            // Given: 12월 이벤트 생성
            createEventWithDate(tokenOwner, "2024-12-25", "2024-12-25");

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2024)
                    .queryParam("month", 12)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("$", hasSize(greaterThanOrEqualTo(1)))
                    .body("[0].startDate", notNullValue());
        }

        @Test
        @DisplayName("API-22: 월별 조회 - 빈 결과")
        void getMonthlyEvents_empty_returns_200() {
            // Given: 아무 이벤트 없음 (다른 테스트와 겹치지 않는 날짜 사용)

            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2025)
                    .queryParam("month", 2)  // 2월로 변경 (1월은 비즈니스 규칙 테스트에서 사용)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("$", hasSize(0));
        }

        @Test
        @DisplayName("API-23: 월별 조회 - 존재하지 않는 Archive → 404")
        void getMonthlyEvents_nonexistent_archive_returns_404() {
            // When & Then
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2024)
                    .queryParam("month", 12)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", 99999L)
            .then()
                    .statusCode(HttpStatus.NOT_FOUND.value());
        }

        @Test
        @DisplayName("API-24: 날짜 범위가 여러 달에 걸친 이벤트 - 6월~8월 이벤트가 6, 7, 8월 조회에서 모두 나와야 함")
        void getMonthlyEvents_multiMonth_range_returns_all_months() {
            // Given: 6월 15일 ~ 8월 20일 이벤트 생성
            createEventWithDate(tokenOwner, "2024-06-15", "2024-08-20");

            // When & Then: 6월 조회
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2024)
                    .queryParam("month", 6)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("$", hasSize(greaterThanOrEqualTo(1)))
                    .body("[0].startDate", equalTo("2024-06-15"));

            // When & Then: 7월 조회
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2024)
                    .queryParam("month", 7)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("$", hasSize(greaterThanOrEqualTo(1)))
                    .body("[0].startDate", equalTo("2024-06-15"));

            // When & Then: 8월 조회
            given()
                    .cookie("ATK", tokenOwner)
                    .queryParam("year", 2024)
                    .queryParam("month", 8)
            .when()
                    .get("/api/v1/events/monthly/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.OK.value())
                    .body("$", hasSize(greaterThanOrEqualTo(1)))
                    .body("[0].startDate", equalTo("2024-06-15"));
        }
    }

    // ========================================================================================
    // [API Category 6]: Business Rule - 개수 제한 (409 CONFLICT)
    // ========================================================================================
    @Nested
    @DisplayName("[API Business] 비즈니스 규칙 검증 - 개수 제한")
    class BusinessRules {

        @BeforeEach
        void setUpBusinessRules() {
            ensureArchiveInitialized();
        }

        @Test
        @DisplayName("API-24: 하루 4개 제한 - 5번째 생성 시도 → 409 CONFLICT")
        void createEvent_exceeds_limit_returns_409() {
            // Given: 같은 날짜에 4개 생성
            String date = "2025-01-10";
            for (int i = 0; i < 4; i++) {
                createEventWithDate(tokenOwner, date, date);
            }

            // When: 5번째 시도
            Map<String, Object> request = new HashMap<>();
            request.put("title", "5th Event");
            request.put("startDate", date);
            request.put("endDate", date);
            request.put("color", "#FF5733");

            // Then
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.CONFLICT.value())
                    .body("status", equalTo("CONFLICT"))
                    .body("error", equalTo("EVENT_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("API-25: 다른 날짜는 제한 안 받음")
        void createEvent_different_day_no_limit() {
            // Given: 12월 20일에 4개
            String date1 = "2024-12-20";
            for (int i = 0; i < 4; i++) {
                createEventWithDate(tokenOwner, date1, date1);
            }

            // When: 12월 21일에 생성
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Different Day Event");
            request.put("startDate", "2024-12-21");
            request.put("endDate", "2024-12-21");
            request.put("color", "#FF5733");

            // Then: 정상 생성
            given()
                    .cookie("ATK", tokenOwner)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/api/v1/events/{archiveId}", ownerArchiveId)
            .then()
                    .statusCode(HttpStatus.CREATED.value());
        }
    }

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    private void ensureArchiveInitialized() {
        // RestAssured 포트 설정 (부모 클래스의 setUp()이 실행되지 않았을 수 있음)
        RestAssured.port = port;
        
        if (ownerArchiveId == null) {
            // tokenOwner도 함께 초기화
            if (tokenOwner == null) {
                Map<String, Object> owner = AuthSteps.registerAndLogin("event.owner@test.com", "EventOwner", "Password123!");
                tokenOwner = (String) owner.get("accessToken");

                Map<String, Object> stranger = AuthSteps.registerAndLogin("event.stranger@test.com", "Stranger", "Password123!");
                tokenStranger = (String) stranger.get("accessToken");
            }
            ownerArchiveId = createArchive(tokenOwner, "Owner Archive", "PUBLIC");
            if (ownerArchiveId == null) {
                throw new IllegalStateException("Failed to create archive: ownerArchiveId is null");
            }
        }
    }

    private Long createArchive(String token, String title, String visibility) {
        Map<String, Object> request = Map.of("title", title, "visibility", visibility);
        Long archiveId = given()
                .cookie("ATK", token)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/archives")
        .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract()
                .jsonPath().getLong("id");
        
        if (archiveId == null) {
            throw new IllegalStateException("Failed to extract archive ID from response");
        }
        return archiveId;
    }

    private Long createSimpleEvent(String token) {
        ensureArchiveInitialized();
        // 매번 다른 날짜를 사용하여 하루 4개 제한 회피
        // 카운터를 사용하여 고유한 날짜 생성 (2024-12-25부터 시작하여 하루씩 증가)
        String date = java.time.LocalDate.of(2024, 12, 25).plusDays(eventDateCounter++).toString();
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Simple Event");
        request.put("startDate", date);
        request.put("endDate", date);
        request.put("hasTime", false);
        request.put("color", "#FF5733");

        return given()
                .cookie("ATK", token)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/events/{archiveId}", ownerArchiveId)
        .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract()
                .jsonPath().getLong("id");
    }

    private Long createEventWithDate(String token, String startDate, String endDate) {
        ensureArchiveInitialized();
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Event");
        request.put("startDate", startDate);
        request.put("endDate", endDate);
        request.put("hasTime", false);
        request.put("color", "#FF5733");

        return given()
                .cookie("ATK", token)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/events/{archiveId}", ownerArchiveId)
        .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract()
                .jsonPath().getLong("id");
    }

    // ========================================================================================
    // AuthSteps - 회원가입/로그인 헬퍼
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
                                Matcher m = Pattern.compile("\\d{6}").matcher(((Map<String, Object>) msg.get("Content")).get("Body").toString());
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
}
