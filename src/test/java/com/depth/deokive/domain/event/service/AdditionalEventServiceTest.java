package com.depth.deokive.domain.event.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.entity.Event;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Event Domain 추가 통합 테스트
 * - Custom Validation 검증
 * - Edge cases (경계값, 시간대)
 * - 여러 날짜에 걸친 이벤트
 * - endDate/endTime 관련 시나리오
 * - 부분 업데이트 시나리오
 *
 * 목표: 커버리지 100%에 근사
 */
@DisplayName("AdditionalEventServiceTest - Event Domain 추가 통합 테스트")
class AdditionalEventServiceTest extends IntegrationTestSupport {

    @Autowired EventService eventService;
    @Autowired EventRepository eventRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired EventHashtagMapRepository eventHashtagMapRepository;
    @Autowired Validator validator;

    private User owner;
    private Archive publicArchive;
    private UserPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        owner = createTestUser("owner@test.com", "Owner");
        setupMockUser(owner);
        publicArchive = createTestArchive(owner, Visibility.PUBLIC);
        ownerPrincipal = UserPrincipal.from(owner);
    }

    private User createTestUser(String email, String nickname) {
        User user = User.builder()
                .email(email)
                .username("user_" + UUID.randomUUID())
                .nickname(nickname)
                .password("password")
                .role(Role.USER)
                .userType(UserType.COMMON)
                .isEmailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private Archive createTestArchive(User owner, Visibility visibility) {
        Archive archive = Archive.builder()
                .user(owner)
                .title("Test Archive")
                .visibility(visibility)
                .build();
        return archiveRepository.save(archive);
    }

    // ========================================================================================
    // [Category 1]: Custom Validation - startDate/endDate
    // ========================================================================================
    @Nested
    @DisplayName("[Validation 1] startDate <= endDate 검증")
    class ValidateDateRange {

        @Test
        @DisplayName("SCENE 1: startDate > endDate일 때 Validation 에러")
        void createEvent_startDate_after_endDate() {
            // Given: 시작일이 종료일보다 미래
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Invalid Event")
                    .startDate(LocalDate.of(2024, 12, 31))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When: Validation 실행
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then: Validation 실패
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 날짜는 종료 날짜보다 이전이거나 같아야 합니다"));
        }

        @Test
        @DisplayName("SCENE 2: startDate == endDate일 때 정상 (같은 날 이벤트)")
        void createEvent_startDate_equals_endDate() {
            // Given: 같은 날짜
            LocalDate sameDate = LocalDate.of(2024, 12, 25);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Same Day Event")
                    .startDate(sameDate)
                    .endDate(sameDate)
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When: Validation 실행
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then: Validation 통과
            assertThat(violations).isEmpty();

            // Service 호출도 정상 동작
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
            assertThat(response.getStartDate()).isEqualTo(sameDate);
            assertThat(response.getEndDate()).isEqualTo(sameDate);
        }

        @Test
        @DisplayName("SCENE 3: startDate < endDate일 때 정상 (여러 날에 걸친 이벤트)")
        void createEvent_multiDay_event() {
            // Given: 3일간 진행되는 이벤트
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("3-Day Concert")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 27))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2024, 12, 25));
            assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2024, 12, 27));
        }

        @Test
        @DisplayName("SCENE 4: UpdateRequest에서 startDate > endDate일 때 Validation 에러")
        void updateEvent_invalid_dateRange() {
            // Given: 정상 이벤트 생성
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25));

            // When: 잘못된 날짜 범위로 수정 시도
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .startDate(LocalDate.of(2024, 12, 31))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .build();

            Set<ConstraintViolation<EventDto.UpdateRequest>> violations = validator.validate(updateRequest);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 날짜는 종료 날짜보다 이전이거나 같아야 합니다"));
        }

        @Test
        @DisplayName("SCENE 4-1: startDate는 있는데 endDate가 없는 경우 Validation 에러")
        void createEvent_startDate_without_endDate() {
            // Given: startDate만 있고 endDate 누락
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Invalid Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    // endDate 누락
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When: Validation 실행
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then: @NotNull 검증 실패
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("endDate") && 
                (v.getMessage().contains("종료 날짜") || v.getMessage().contains("필수") || v.getMessage().contains("null"))
            );
        }
    }

    // ========================================================================================
    // [Category 2]: Custom Validation - hasTime + startTime/endTime
    // ========================================================================================
    @Nested
    @DisplayName("[Validation 2] hasTime과 시간 필드 정합성 검증")
    class ValidateHasTimeConsistency {

        @Test
        @DisplayName("SCENE 5: hasTime=true인데 startTime만 있을 때 Validation 에러 (치명적)")
        void createEvent_hasTime_true_but_only_startTime() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Invalid Time Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .hasTime(true)
                    .startTime(LocalTime.of(10, 0))
                    // endTime 누락
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 시간과 종료 시간을 모두 입력해야 합니다"));
        }

        @Test
        @DisplayName("SCENE 6: hasTime=true인데 endTime만 있을 때 Validation 에러")
        void createEvent_hasTime_true_but_only_endTime() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Invalid Time Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .hasTime(true)
                    // startTime 누락
                    .endTime(LocalTime.of(18, 0))
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 시간과 종료 시간을 모두 입력해야 합니다"));
        }

        @Test
        @DisplayName("SCENE 7: hasTime=true이고 startTime, endTime 모두 있을 때 정상")
        void createEvent_hasTime_true_with_both_times() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Valid Time Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .hasTime(true)
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(18, 0))
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();

            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
            assertThat(response.isHasTime()).isTrue();
            assertThat(response.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(response.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("SCENE 8: hasTime=false인데 시간이 있어도 무시됨 (정상 동작)")
        void createEvent_hasTime_false_with_times_ignored() {
            // Given: hasTime=false인데 시간 정보 있음
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("No Time Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 25))
                    .hasTime(false)
                    .startTime(LocalTime.of(10, 0)) // 무시되어야 함
                    .endTime(LocalTime.of(18, 0))   // 무시되어야 함
                    .color("#FF5733")
                    .build();

            // When: Validation은 통과
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();

            // Service는 hasTime=false이므로 시간을 00:00으로 설정
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.isHasTime()).isFalse();
            assertThat(response.getStartTime()).isNull(); // DTO 변환 시 hasTime=false면 null
            assertThat(response.getEndTime()).isNull();

            // DB에는 MIDNIGHT로 저장됨
            Event saved = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(saved.getStartDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(saved.getEndDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 9: UpdateRequest에서 hasTime=true로 변경하는데 시간 중 하나만 있을 때")
        void updateEvent_hasTime_true_but_partial_time() {
            // Given: hasTime=false인 이벤트
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25));

            // When: hasTime=true로 변경하되 startTime만 제공
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .hasTime(true)
                    .startTime(LocalTime.of(10, 0))
                    // endTime 누락
                    .build();

            Set<ConstraintViolation<EventDto.UpdateRequest>> violations = validator.validate(updateRequest);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 시간과 종료 시간을 모두 입력해야 합니다"));
        }
    }

    // ========================================================================================
    // [Category 3]: Custom Validation - startTime <= endTime (같은 날일 때)
    // ========================================================================================
    @Nested
    @DisplayName("[Validation 3] 같은 날짜일 때 startTime <= endTime 검증")
    class ValidateTimeRangeOnSameDay {

        @Test
        @DisplayName("SCENE 10: 같은 날짜이고 startTime > endTime일 때 Validation 에러")
        void createEvent_sameDay_startTime_after_endTime() {
            // Given
            LocalDate sameDate = LocalDate.of(2024, 12, 25);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Invalid Time Order")
                    .startDate(sameDate)
                    .endDate(sameDate)
                    .hasTime(true)
                    .startTime(LocalTime.of(18, 0))
                    .endTime(LocalTime.of(10, 0)) // 시작 시간보다 이전
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 시간은 종료 시간보다 이전이거나 같아야 합니다"));
        }

        @Test
        @DisplayName("SCENE 11: 같은 날짜이고 startTime == endTime일 때 정상 (순간 이벤트)")
        void createEvent_sameDay_startTime_equals_endTime() {
            // Given: 순간 이벤트 (예: 사진 촬영 시각)
            LocalDate sameDate = LocalDate.of(2024, 12, 25);
            LocalTime sameTime = LocalTime.of(12, 0);

            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Instant Event")
                    .startDate(sameDate)
                    .endDate(sameDate)
                    .hasTime(true)
                    .startTime(sameTime)
                    .endTime(sameTime)
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();

            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
            assertThat(response.getStartTime()).isEqualTo(sameTime);
            assertThat(response.getEndTime()).isEqualTo(sameTime);
        }

        @Test
        @DisplayName("SCENE 12: 다른 날짜일 때는 시간 순서 검증 안 함")
        void createEvent_differentDays_time_order_not_validated() {
            // Given: 12월 25일 18시 시작 → 12월 26일 10시 종료 (정상)
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Overnight Event")
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 26))
                    .hasTime(true)
                    .startTime(LocalTime.of(18, 0))
                    .endTime(LocalTime.of(10, 0)) // 다른 날이므로 OK
                    .color("#FF5733")
                    .build();

            // When
            Set<ConstraintViolation<EventDto.CreateRequest>> violations = validator.validate(request);

            // Then: Validation 통과
            assertThat(violations).isEmpty();

            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("SCENE 13: UpdateRequest에서 같은 날로 변경하고 시간 역전 시 에러")
        void updateEvent_change_to_sameDay_with_invalid_time() {
            // Given: 여러 날에 걸친 이벤트
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 27));

            // When: 같은 날로 변경하되 시간 순서 잘못됨
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .startDate(LocalDate.of(2024, 12, 25))
                    .endDate(LocalDate.of(2024, 12, 25)) // 같은 날로 변경
                    .hasTime(true)
                    .startTime(LocalTime.of(18, 0))
                    .endTime(LocalTime.of(10, 0)) // 역전
                    .build();

            Set<ConstraintViolation<EventDto.UpdateRequest>> violations = validator.validate(updateRequest);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("시작 시간은 종료 시간보다 이전이거나 같아야 합니다"));
        }
    }

    // ========================================================================================
    // [Category 4]: Edge Cases - 시간대 경계값
    // ========================================================================================
    @Nested
    @DisplayName("[Edge Cases 1] 시간대 경계값 테스트")
    class EdgeCasesTimeZone {

        @Test
        @DisplayName("SCENE 14: 자정(00:00)부터 자정(00:00)까지 (24시간)")
        void createEvent_midnight_to_midnight() {
            // Given: 같은 날 00:00~00:00 (순간)
            LocalDate date = LocalDate.of(2024, 12, 25);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Midnight Event")
                    .startDate(date)
                    .endDate(date)
                    .hasTime(true)
                    .startTime(LocalTime.MIDNIGHT)
                    .endTime(LocalTime.MIDNIGHT)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(response.getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 15: 23:59:59까지 (당일 마지막)")
        void createEvent_until_endOfDay() {
            // Given
            LocalDate date = LocalDate.of(2024, 12, 25);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Until End of Day")
                    .startDate(date)
                    .endDate(date)
                    .hasTime(true)
                    .startTime(LocalTime.of(0, 0))
                    .endTime(LocalTime.of(23, 59, 59))
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getEndTime()).isEqualTo(LocalTime.of(23, 59, 59));
        }

        @Test
        @DisplayName("SCENE 16: 월 경계 넘는 이벤트 (1월 31일 → 2월 1일)")
        void createEvent_cross_month_boundary() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Cross Month Event")
                    .startDate(LocalDate.of(2024, 1, 31))
                    .endDate(LocalDate.of(2024, 2, 1))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate().getMonthValue()).isEqualTo(1);
            assertThat(response.getEndDate().getMonthValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("SCENE 17: 연도 경계 넘는 이벤트 (12월 31일 → 1월 1일)")
        void createEvent_cross_year_boundary() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("New Year Event")
                    .startDate(LocalDate.of(2024, 12, 31))
                    .endDate(LocalDate.of(2025, 1, 1))
                    .hasTime(true)
                    .startTime(LocalTime.of(23, 0))
                    .endTime(LocalTime.of(1, 0))
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate().getYear()).isEqualTo(2024);
            assertThat(response.getEndDate().getYear()).isEqualTo(2025);
        }

        @Test
        @DisplayName("SCENE 18: 윤년 2월 29일 이벤트")
        void createEvent_leapYear_feb29() {
            // Given: 2024년은 윤년
            LocalDate leapDay = LocalDate.of(2024, 2, 29);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Leap Day Event")
                    .startDate(leapDay)
                    .endDate(leapDay)
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate()).isEqualTo(leapDay);
            assertThat(response.getEndDate()).isEqualTo(leapDay);
        }
    }

    // ========================================================================================
    // [Category 5]: 여러 달에 걸친 이벤트 (Long-running Events)
    // ========================================================================================
    @Nested
    @DisplayName("[Edge Cases 2] 여러 달/주에 걸친 이벤트")
    class LongRunningEvents {

        @Test
        @DisplayName("SCENE 19: 1주일짜리 이벤트")
        void createEvent_oneWeek() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Week-long Festival")
                    .startDate(LocalDate.of(2024, 12, 1))
                    .endDate(LocalDate.of(2024, 12, 7))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2024, 12, 1));
            assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2024, 12, 7));
        }

        @Test
        @DisplayName("SCENE 20: 한 달짜리 이벤트")
        void createEvent_oneMonth() {
            // Given
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Month-long Exhibition")
                    .startDate(LocalDate.of(2024, 12, 1))
                    .endDate(LocalDate.of(2024, 12, 31))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    response.getStartDate(), response.getEndDate());
            assertThat(daysBetween).isEqualTo(30);
        }

        @Test
        @DisplayName("SCENE 21: 여러 달에 걸친 이벤트 (3개월)")
        void createEvent_threeMonths() {
            // Given: 12월 ~ 2월
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Winter Season")
                    .startDate(LocalDate.of(2024, 12, 1))
                    .endDate(LocalDate.of(2025, 2, 28))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            // Then
            assertThat(response.getStartDate().getMonthValue()).isEqualTo(12);
            assertThat(response.getEndDate().getMonthValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("SCENE 22: 날짜 범위가 여러 달에 걸친 이벤트 - 6월~8월 이벤트가 6, 7, 8월 조회에서 모두 나와야 함")
        void getMonthlyEvents_multiMonth_range_returns_all_months() {
            // Given: 6월 15일 ~ 8월 20일 이벤트 생성
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Multi-month Event")
                    .startDate(LocalDate.of(2024, 6, 15))
                    .endDate(LocalDate.of(2024, 8, 20))
                    .hasTime(false)
                    .color("#FF5733")
                    .build();
            eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);

            flushAndClear();

            // When: 6월 조회
            List<EventDto.Response> june = eventService.getMonthlyEvents(ownerPrincipal, publicArchive.getId(), 2024, 6);
            // When: 7월 조회
            List<EventDto.Response> july = eventService.getMonthlyEvents(ownerPrincipal, publicArchive.getId(), 2024, 7);
            // When: 8월 조회
            List<EventDto.Response> august = eventService.getMonthlyEvents(ownerPrincipal, publicArchive.getId(), 2024, 8);

            // Then: 모든 월에서 이벤트가 조회되어야 함 (수정된 Repository 로직 반영)
            assertThat(june).hasSize(1);
            assertThat(june.get(0).getStartDate()).isEqualTo(LocalDate.of(2024, 6, 15));
            
            assertThat(july).hasSize(1);
            assertThat(july.get(0).getStartDate()).isEqualTo(LocalDate.of(2024, 6, 15));
            
            assertThat(august).hasSize(1);
            assertThat(august.get(0).getStartDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        }
    }

    // ========================================================================================
    // [Category 6]: UpdateRequest 부분 업데이트 시나리오
    // ========================================================================================
    @Nested
    @DisplayName("[Update] 부분 업데이트 시나리오")
    class PartialUpdateScenarios {

        @Test
        @DisplayName("SCENE 23: 제목만 변경 (날짜/시간 유지)")
        void updateEvent_titleOnly() {
            // Given
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25));
            LocalDateTime originalStart = event.getStartDate();
            LocalDateTime originalEnd = event.getEndDate();

            // When: 제목만 변경
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .title("Updated Title")
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then: 날짜/시간은 그대로
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Updated Title");
            assertThat(updated.getStartDate()).isEqualTo(originalStart);
            assertThat(updated.getEndDate()).isEqualTo(originalEnd);
        }

        @Test
        @DisplayName("SCENE 24: startDate만 변경 (endDate 유지)")
        void updateEvent_startDateOnly() {
            // Given
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 27));

            // When: startDate만 변경
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .startDate(LocalDate.of(2024, 12, 24))
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.getStartDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 24));
            assertThat(updated.getEndDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 27)); // 유지
        }

        @Test
        @DisplayName("SCENE 25: endDate만 변경 (startDate 유지)")
        void updateEvent_endDateOnly() {
            // Given
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 27));

            // When: endDate만 변경
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .endDate(LocalDate.of(2024, 12, 31))
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.getStartDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 25)); // 유지
            assertThat(updated.getEndDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        }

        @Test
        @DisplayName("SCENE 26: hasTime=false → true 전환 시 시간 설정")
        void updateEvent_hasTime_false_to_true() {
            // Given: hasTime=false인 이벤트
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25));
            assertThat(event.isHasTime()).isFalse();

            // When: hasTime=true로 변경 + 시간 설정
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .hasTime(true)
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(18, 0))
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.isHasTime()).isTrue();
            assertThat(updated.getStartDate().toLocalTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(updated.getEndDate().toLocalTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("SCENE 27: hasTime=true → false 전환 시 시간이 MIDNIGHT로 변경")
        void updateEvent_hasTime_true_to_false() {
            // Given: hasTime=true이고 시간이 있는 이벤트
            Event event = createEventWithTime(
                    LocalDate.of(2024, 12, 25),
                    LocalDate.of(2024, 12, 25),
                    LocalTime.of(10, 0),
                    LocalTime.of(18, 0)
            );
            assertThat(event.isHasTime()).isTrue();

            // When: hasTime=false로 변경
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .hasTime(false)
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.isHasTime()).isFalse();
            assertThat(updated.getStartDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(updated.getEndDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 28: hasTime=true 상태에서 시간만 변경")
        void updateEvent_change_time_only() {
            // Given
            Event event = createEventWithTime(
                    LocalDate.of(2024, 12, 25),
                    LocalDate.of(2024, 12, 25),
                    LocalTime.of(10, 0),
                    LocalTime.of(18, 0)
            );

            // When: 시간만 변경
            EventDto.UpdateRequest updateRequest = EventDto.UpdateRequest.builder()
                    .startTime(LocalTime.of(14, 0))
                    .endTime(LocalTime.of(20, 0))
                    .build();
            eventService.updateEvent(ownerPrincipal, event.getId(), updateRequest);

            flushAndClear();

            // Then
            Event updated = eventRepository.findById(event.getId()).orElseThrow();
            assertThat(updated.getStartDate().toLocalTime()).isEqualTo(LocalTime.of(14, 0));
            assertThat(updated.getEndDate().toLocalTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(updated.getStartDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 25)); // 날짜 유지
        }
    }

    // ========================================================================================
    // [Category 7]: Business Rule - 하루 4개 제한
    // ========================================================================================
    @Nested
    @DisplayName("[Business Rule] 하루 4개 제한 검증")
    class BusinessRuleEventLimit {

        @Test
        @DisplayName("SCENE 28-1: 하루 4개 제한 - 5번째 생성 시도 시 예외 발생")
        void createEvent_exceeds_limit_throws_exception() {
            // Given: 같은 날짜에 4개 생성
            LocalDate date = LocalDate.of(2024, 12, 25);
            for (int i = 0; i < 4; i++) {
                createSimpleEvent(date, date);
            }

            // When: 5번째 시도
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("5th Event")
                    .startDate(date)
                    .endDate(date)
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // Then: RestException 발생
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> 
                eventService.createEvent(ownerPrincipal, publicArchive.getId(), request)
            )).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 28-2: 다른 날짜는 제한 안 받음")
        void createEvent_different_day_no_limit() {
            // Given: 12월 20일에 4개 생성
            LocalDate date1 = LocalDate.of(2024, 12, 20);
            for (int i = 0; i < 4; i++) {
                createSimpleEvent(date1, date1);
            }

            // When: 12월 21일에 생성
            LocalDate date2 = LocalDate.of(2024, 12, 21);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Different Day Event")
                    .startDate(date2)
                    .endDate(date2)
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // Then: 정상 생성
            EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
            assertThat(response).isNotNull();
            assertThat(response.getStartDate()).isEqualTo(date2);
        }
    }

    // ========================================================================================
    // [Category 8]: Response DTO 변환 검증
    // ========================================================================================
    @Nested
    @DisplayName("[Response] DTO 변환 검증")
    class ResponseDtoConversion {

        @Test
        @DisplayName("SCENE 29: hasTime=false일 때 Response의 startTime/endTime은 null")
        void response_hasTime_false_times_are_null() {
            // Given: hasTime=false
            Event event = createSimpleEvent(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25));

            // When
            EventDto.Response response = eventService.getEvent(ownerPrincipal, event.getId());

            // Then
            assertThat(response.isHasTime()).isFalse();
            assertThat(response.getStartTime()).isNull();
            assertThat(response.getEndTime()).isNull();
            assertThat(response.getStartDate()).isNotNull();
            assertThat(response.getEndDate()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 30: hasTime=true일 때 Response의 startTime/endTime은 not null")
        void response_hasTime_true_times_are_not_null() {
            // Given: hasTime=true
            Event event = createEventWithTime(
                    LocalDate.of(2024, 12, 25),
                    LocalDate.of(2024, 12, 25),
                    LocalTime.of(10, 0),
                    LocalTime.of(18, 0)
            );

            // When
            EventDto.Response response = eventService.getEvent(ownerPrincipal, event.getId());

            // Then
            assertThat(response.isHasTime()).isTrue();
            assertThat(response.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(response.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("SCENE 31: 여러 날짜 이벤트의 Response 검증")
        void response_multiDay_event() {
            // Given
            Event event = createEventWithTime(
                    LocalDate.of(2024, 12, 25),
                    LocalDate.of(2024, 12, 27),
                    LocalTime.of(18, 0),
                    LocalTime.of(10, 0)
            );

            // When
            EventDto.Response response = eventService.getEvent(ownerPrincipal, event.getId());

            // Then
            assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2024, 12, 25));
            assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2024, 12, 27));
            assertThat(response.getStartTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(response.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        }
    }

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    /**
     * hasTime=false인 간단한 이벤트 생성
     */
    private Event createSimpleEvent(LocalDate startDate, LocalDate endDate) {
        EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                .title("Simple Event")
                .startDate(startDate)
                .endDate(endDate)
                .hasTime(false)
                .color("#FF5733")
                .build();
        EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
        return eventRepository.findById(response.getId()).orElseThrow();
    }

    /**
     * hasTime=true이고 시간이 있는 이벤트 생성
     */
    private Event createEventWithTime(LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime) {
        EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                .title("Timed Event")
                .startDate(startDate)
                .endDate(endDate)
                .hasTime(true)
                .startTime(startTime)
                .endTime(endTime)
                .color("#FF5733")
                .build();
        EventDto.Response response = eventService.createEvent(ownerPrincipal, publicArchive.getId(), request);
        return eventRepository.findById(response.getId()).orElseThrow();
    }
}
