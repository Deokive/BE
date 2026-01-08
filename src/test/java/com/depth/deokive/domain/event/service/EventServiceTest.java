package com.depth.deokive.domain.event.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.entity.Event;
import com.depth.deokive.domain.event.entity.EventHashtagMap;
import com.depth.deokive.domain.event.entity.SportRecord;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.event.repository.HashtagRepository;
import com.depth.deokive.domain.event.repository.SportRecordRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventService 통합 테스트")
class EventServiceTest extends IntegrationTestSupport {

    @Autowired EventService eventService;
    @Autowired ArchiveService archiveService;

    // Core Repositories
    @Autowired EventRepository eventRepository;
    @Autowired SportRecordRepository sportRecordRepository;
    @Autowired HashtagRepository hashtagRepository;
    @Autowired EventHashtagMapRepository eventHashtagMapRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired FriendMapRepository friendMapRepository;

    // Test Data
    private User userA; // Me
    private User userB; // Friend
    private User userC; // Stranger

    private Archive archiveAPublic;
    private Archive archiveARestricted;
    private Archive archiveAPrivate;
    private Archive archiveBPublic;

    @BeforeEach
    void setUp() {
        // 1. Users Setup
        userA = createTestUser("usera@test.com", "UserA");
        userB = createTestUser("userb@test.com", "UserB");
        userC = createTestUser("userc@test.com", "UserC");

        // 2. Friend Setup (A <-> B)
        friendMapRepository.save(FriendMap.builder().user(userA).friend(userB).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());
        friendMapRepository.save(FriendMap.builder().user(userB).friend(userA).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());

        // 3. Archives Setup
        setupMockUser(userA);
        archiveAPublic = createArchiveByService(userA, Visibility.PUBLIC);
        archiveARestricted = createArchiveByService(userA, Visibility.RESTRICTED);
        archiveAPrivate = createArchiveByService(userA, Visibility.PRIVATE);

        setupMockUser(userB);
        archiveBPublic = createArchiveByService(userB, Visibility.PUBLIC);

        SecurityContextHolder.clearContext();
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

    private Archive createArchiveByService(User owner, Visibility visibility) {
        setupMockUser(owner);
        UserPrincipal principal = UserPrincipal.from(owner);

        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);

        ArchiveDto.Response response = archiveService.createArchive(principal, req);
        SecurityContextHolder.clearContext();
        return archiveRepository.findById(response.getId()).orElseThrow();
    }

    // ========================================================================================
    // [Category 1]: Create
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Event")
    class Create {
        @Test
        @DisplayName("SCENE 1: 정상 케이스 (일반 이벤트, 해시태그 포함)")
        void createEvent_Normal() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Event 1")
                    .date(LocalDate.now())
                    .time(LocalTime.of(14, 30))
                    .hasTime(true)
                    .color("#FF5733")
                    .hashtags(List.of("tag1", "tag2"))
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(principal, archiveAPublic.getId(), request);

            // Then
            Event savedEvent = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(savedEvent.getTitle()).isEqualTo("Event 1");
            assertThat(savedEvent.isHasTime()).isTrue();
            assertThat(savedEvent.getDate().toLocalTime()).isEqualTo(LocalTime.of(14, 30));

            List<String> tags = eventHashtagMapRepository.findHashtagNamesByEventId(savedEvent.getId());
            assertThat(tags).containsExactlyInAnyOrder("tag1", "tag2");
        }

        @Test
        @DisplayName("SCENE (Additional): 윤년(Leap Year) 생성 확인 (2024-02-29)")
        void createEvent_LeapYear() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            // 2024년은 윤년입니다.
            LocalDate leapDate = LocalDate.of(2024, 2, 29);

            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Leap Year Event")
                    .date(leapDate)
                    .color("#FF5733")
                    .hasTime(false)
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(principal, archiveAPublic.getId(), request);

            // Then
            Event savedEvent = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(savedEvent.getDate().toLocalDate()).isEqualTo(leapDate);
        }

        @Test
        @DisplayName("SCENE 2: 시간 없이 생성 (hasTime = false)")
        void createEvent_NoTime() {
            // Given
            setupMockUser(userA);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("No Time Event")
                    .date(LocalDate.now())
                    .hasTime(false)
                    .color("#FF5733")
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            // Then
            Event savedEvent = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(savedEvent.isHasTime()).isFalse();
            assertThat(savedEvent.getDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 3: 스포츠 타입 이벤트 생성")
        void createEvent_SportType() {
            // Given
            setupMockUser(userA);
            EventDto.SportRequest sportInfo = EventDto.SportRequest.builder()
                    .team1("Team A").team2("Team B").score1(1).score2(2).build();

            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Sport Event")
                    .date(LocalDate.now())
                    .color("#FF5733")
                    .isSportType(true)
                    .sportInfo(sportInfo)
                    .build();

            // When
            EventDto.Response response = eventService.createEvent(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            // Then
            Event savedEvent = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(savedEvent.isSportType()).isTrue();

            SportRecord record = sportRecordRepository.findById(savedEvent.getId()).orElseThrow();
            assertThat(record.getTeam1()).isEqualTo("Team A");
            assertThat(record.getEvent().getId()).isEqualTo(savedEvent.getId());
        }

        @Test
        @DisplayName("SCENE 4: 해시태그 없이 생성")
        void createEvent_NoHashtags() {
            setupMockUser(userA);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("No Tag Event").date(LocalDate.now()).color("#FF5733").hashtags(null).build();

            EventDto.Response response = eventService.createEvent(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            List<EventHashtagMap> maps = eventHashtagMapRepository.findAllByEventId(response.getId());
            assertThat(maps).isEmpty();
        }

        @Test
        @DisplayName("SCENE 5: 중복 해시태그 처리")
        void createEvent_DuplicateHashtags() {
            setupMockUser(userA);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Dup Tag Event").date(LocalDate.now()).color("#FF5733")
                    .hashtags(List.of("tag1", "tag1", "tag2")) // 중복
                    .build();

            EventDto.Response response = eventService.createEvent(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            List<String> tags = eventHashtagMapRepository.findHashtagNamesByEventId(response.getId());
            assertThat(tags).hasSize(2).containsExactlyInAnyOrder("tag1", "tag2");
        }

        @Test
        @DisplayName("SCENE 6: 존재하지 않는 Archive")
        void createEvent_ArchiveNotFound() {
            setupMockUser(userA);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Ghost").date(LocalDate.now()).color("#FF5733").build();

            assertThatThrownBy(() -> eventService.createEvent(UserPrincipal.from(userA), 99999L, request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 7: 타인 Archive에 생성 시도")
        void createEvent_Forbidden() {
            setupMockUser(userA);
            EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                    .title("Hacked").date(LocalDate.now()).color("#FF5733").build();

            assertThatThrownBy(() -> eventService.createEvent(UserPrincipal.from(userA), archiveBPublic.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 2]: Read
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Event")
    class Read {
        private Event normalEvent;
        private Event sportEvent;
        private Event restrictedEvent;
        private Event privateEvent;

        @BeforeEach
        void initEvents() {
            // Public Archive
            normalEvent = createEventByService(userA, archiveAPublic.getId(), false, true, List.of("tag1", "tag2", "tag3"));
            sportEvent = createEventByService(userA, archiveAPublic.getId(), true, true, List.of("sport"));

            // Restricted: Normal(HasTime=False)
            restrictedEvent = createEventByService(userA, archiveARestricted.getId(), false, false, null);

            // Private: Normal(HasTime=False)
            privateEvent = createEventByService(userA, archiveAPrivate.getId(), false, false, null);
        }

        @Test
        @DisplayName("SCENE 8~11: PUBLIC Archive + 일반 Event")
        void getEvent_Public_Normal() {
            // 8: Owner
            EventDto.Response resOwner = eventService.getEvent(UserPrincipal.from(userA), normalEvent.getId());
            assertThat(resOwner.getId()).isEqualTo(normalEvent.getId());
            assertThat(resOwner.getHashtags()).hasSize(3);

            // 9: Stranger
            assertThat(eventService.getEvent(UserPrincipal.from(userC), normalEvent.getId())).isNotNull();
            // 10: Friend
            assertThat(eventService.getEvent(UserPrincipal.from(userB), normalEvent.getId())).isNotNull();
            // 11: Anonymous
            assertThat(eventService.getEvent(null, normalEvent.getId())).isNotNull();
        }

        @Test
        @DisplayName("SCENE 12~15: PUBLIC Archive + 스포츠 Event")
        void getEvent_Public_Sport() {
            // 12: Owner
            EventDto.Response resOwner = eventService.getEvent(UserPrincipal.from(userA), sportEvent.getId());
            assertThat(resOwner.isSportType()).isTrue();
            assertThat(resOwner.getSportInfo()).isNotNull();

            // 13~15: Others
            assertThat(eventService.getEvent(UserPrincipal.from(userC), sportEvent.getId()).getSportInfo()).isNotNull();
            assertThat(eventService.getEvent(UserPrincipal.from(userB), sportEvent.getId()).getSportInfo()).isNotNull();
            assertThat(eventService.getEvent(null, sportEvent.getId()).getSportInfo()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 16~19: RESTRICTED Archive")
        void getEvent_Restricted() {
            // 16, 17: Owner & Friend OK
            assertThat(eventService.getEvent(UserPrincipal.from(userA), restrictedEvent.getId())).isNotNull();
            assertThat(eventService.getEvent(UserPrincipal.from(userB), restrictedEvent.getId())).isNotNull();

            // 18, 19: Stranger & Anonymous Fail
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userC), restrictedEvent.getId())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getEvent(null, restrictedEvent.getId())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 20~23: RESTRICTED + Sport (권한 동일)")
        void getEvent_Restricted_Sport() {
            Event rSportEvent = createEventByService(userA, archiveARestricted.getId(), true, true, null);

            assertThat(eventService.getEvent(UserPrincipal.from(userA), rSportEvent.getId())).isNotNull();
            assertThat(eventService.getEvent(UserPrincipal.from(userB), rSportEvent.getId())).isNotNull();
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userC), rSportEvent.getId())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getEvent(null, rSportEvent.getId())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 24~27: PRIVATE Archive + 일반 Event")
        void getEvent_Private() {
            // 24: Owner OK
            assertThat(eventService.getEvent(UserPrincipal.from(userA), privateEvent.getId())).isNotNull();

            // 25~27: Others Fail
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userC), privateEvent.getId())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userB), privateEvent.getId())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getEvent(null, privateEvent.getId())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 28~31: PRIVATE Archive + 스포츠 Event")
        void getEvent_Private_Sport() {
            // Given: Private Archive에 Sport Event 생성
            Event pSportEvent = createEventByService(userA, archiveAPrivate.getId(), true, true, null);

            // 28: Owner OK
            assertThat(eventService.getEvent(UserPrincipal.from(userA), pSportEvent.getId())).isNotNull();
            assertThat(eventService.getEvent(UserPrincipal.from(userA), pSportEvent.getId()).getSportInfo()).isNotNull();

            // 29~31: Others Fail (Stranger, Friend, Anonymous)
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userC), pSportEvent.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userB), pSportEvent.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
            assertThatThrownBy(() -> eventService.getEvent(null, pSportEvent.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 32~33: hasTime 값 검증")
        void getEvent_HasTime() {
            // 32: hasTime=true
            EventDto.Response resTime = eventService.getEvent(UserPrincipal.from(userA), normalEvent.getId());
            assertThat(resTime.isHasTime()).isTrue();
            assertThat(resTime.getTime()).isNotNull();

            // 33: hasTime=false
            EventDto.Response resNoTime = eventService.getEvent(UserPrincipal.from(userA), restrictedEvent.getId());
            assertThat(resNoTime.isHasTime()).isFalse();
            // Service DTO 변환 로직: hasTime이 false면 time은 null 또는 00:00 (여기선 null로 가정)
            assertThat(resNoTime.getTime()).isNull();
        }

        @Test
        @DisplayName("SCENE 34: 존재하지 않는 Event")
        void getEvent_NotFound() {
            assertThatThrownBy(() -> eventService.getEvent(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 3]: Update
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Update Event")
    class Update {
        private Event event;

        @BeforeEach
        void init() {
            event = createEventByService(userA, archiveAPublic.getId(), false, true, List.of("old1", "old2"));
        }

        @Test
        @DisplayName("SCENE 35~36: 정상 수정")
        void updateEvent_Normal() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // Given: 초기 상태 확인 (hasTime=true, time=12:00)
            Event original = eventRepository.findById(event.getId()).get();
            assertThat(original.isHasTime()).isTrue();

            // 35: Full Update
            LocalDate newDate = LocalDate.of(2025, 12, 25);
            LocalTime newTime = LocalTime.of(10, 0);
            EventDto.UpdateRequest req1 = EventDto.UpdateRequest.builder()
                    .title("New Title")
                    .date(newDate)
                    .time(newTime)
                    .hasTime(true)
                    .build();
            eventService.updateEvent(principal, event.getId(), req1);
            flushAndClear();
            
            Event updated = eventRepository.findById(event.getId()).get();
            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getDate().toLocalDate()).isEqualTo(newDate);
            assertThat(updated.isHasTime()).isTrue();
            assertThat(updated.getDate().toLocalTime()).isEqualTo(newTime);

            // 36: Date Only (Time Keep) - hasTime이 true일 때 시간 유지
            EventDto.UpdateRequest req2 = EventDto.UpdateRequest.builder()
                    .date(LocalDate.of(2026, 1, 1))
                    .build();
            eventService.updateEvent(principal, event.getId(), req2);
            flushAndClear();
            
            updated = eventRepository.findById(event.getId()).get();
            assertThat(updated.getDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(updated.isHasTime()).isTrue(); // hasTime 상태 유지
            assertThat(updated.getDate().toLocalTime()).isEqualTo(newTime); // 시간 유지
        }

        @Test
        @DisplayName("SCENE 37~38: 해시태그 수정")
        void updateEvent_Hashtags() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // 37: Replace
            EventDto.UpdateRequest req1 = EventDto.UpdateRequest.builder().hashtags(List.of("new1")).build();
            eventService.updateEvent(principal, event.getId(), req1);
            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId(event.getId())).containsExactly("new1");

            // 38: Keep (null)
            EventDto.UpdateRequest req2 = EventDto.UpdateRequest.builder().hashtags(null).build();
            eventService.updateEvent(principal, event.getId(), req2);
            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId(event.getId())).containsExactly("new1");
        }

        @Test
        @DisplayName("SCENE 39~41: 스포츠 타입 변경")
        void updateEvent_SportType() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // 39: OFF -> ON
            EventDto.UpdateRequest req1 = EventDto.UpdateRequest.builder()
                    .isSportType(true)
                    .sportInfo(new EventDto.SportRequest("A", "B", 1, 0))
                    .build();
            eventService.updateEvent(principal, event.getId(), req1);
            assertThat(eventRepository.findById(event.getId()).get().isSportType()).isTrue();
            assertThat(sportRecordRepository.existsById(event.getId())).isTrue();

            // 41: Update Info
            EventDto.UpdateRequest req2 = EventDto.UpdateRequest.builder()
                    .sportInfo(new EventDto.SportRequest("A", "B", 5, 5))
                    .build();
            eventService.updateEvent(principal, event.getId(), req2);
            assertThat(sportRecordRepository.findById(event.getId()).get().getScore1()).isEqualTo(5);

            // 40: ON -> OFF
            EventDto.UpdateRequest req3 = EventDto.UpdateRequest.builder().isSportType(false).build();
            eventService.updateEvent(principal, event.getId(), req3);

            flushAndClear(); // [중요] DB 반영
            assertThat(sportRecordRepository.existsById(event.getId())).isFalse();
        }

        @Test
        @DisplayName("SCENE 42~43: hasTime 변경")
        void updateEvent_HasTime() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // Given: 초기 상태 (hasTime=true, time=12:00)
            Event original = eventRepository.findById(event.getId()).get();
            assertThat(original.isHasTime()).isTrue();

            // 43: True -> False (시간이 MIDNIGHT로 설정됨)
            EventDto.UpdateRequest req2 = EventDto.UpdateRequest.builder().hasTime(false).build();
            eventService.updateEvent(principal, event.getId(), req2);
            flushAndClear();
            
            Event updated = eventRepository.findById(event.getId()).get();
            assertThat(updated.isHasTime()).isFalse();
            assertThat(updated.getDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);

            // 42: False -> True (시간을 명시적으로 설정해야 함)
            LocalTime newTime = LocalTime.of(9, 0);
            EventDto.UpdateRequest req1 = EventDto.UpdateRequest.builder()
                    .hasTime(true)
                    .time(newTime)
                    .build();
            eventService.updateEvent(principal, event.getId(), req1);
            flushAndClear();
            
            Event updated2 = eventRepository.findById(event.getId()).get();
            assertThat(updated2.isHasTime()).isTrue();
            assertThat(updated2.getDate().toLocalTime()).isEqualTo(newTime);
            
            // hasTime=false일 때 time을 설정하지 않으면 MIDNIGHT 유지
            EventDto.UpdateRequest req3 = EventDto.UpdateRequest.builder()
                    .hasTime(false)
                    .build();
            eventService.updateEvent(principal, event.getId(), req3);
            flushAndClear();
            
            Event updated3 = eventRepository.findById(event.getId()).get();
            assertThat(updated3.isHasTime()).isFalse();
            assertThat(updated3.getDate().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("SCENE 44~45: 예외 케이스")
        void updateEvent_Exceptions() {
            EventDto.UpdateRequest req = new EventDto.UpdateRequest();
            req.setTitle("Hacked");

            // 44: Forbidden
            assertThatThrownBy(() -> eventService.updateEvent(UserPrincipal.from(userC), event.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 45: Not Found
            assertThatThrownBy(() -> eventService.updateEvent(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 4]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Delete Event")
    class Delete {
        @Test
        @DisplayName("SCENE 46: 정상 삭제 (일반 이벤트)")
        void deleteEvent_Normal() {
            // Given
            Event event = createEventByService(userA, archiveAPublic.getId(), false, true, List.of("tag1"));
            Long eventId = event.getId();

            flushAndClear(); // [추가] 생성 후 클리어

            // When
            eventService.deleteEvent(UserPrincipal.from(userA), eventId);
            flushAndClear(); // [추가] 삭제 후 클리어

            // Then
            assertThat(eventRepository.existsById(eventId)).isFalse();
            assertThat(eventHashtagMapRepository.findHashtagNamesByEventId(eventId)).isEmpty();
            assertThat(hashtagRepository.findByName("tag1")).isPresent(); // 태그 엔티티는 유지
        }

        @Test
        @DisplayName("SCENE 47: 정상 삭제 (스포츠 이벤트)")
        void deleteEvent_Sport() {
            // Given
            Event event = createEventByService(userA, archiveAPublic.getId(), true, true, null);
            Long eventId = event.getId();

            flushAndClear(); // [추가] 생성 후 클리어

            // When
            eventService.deleteEvent(UserPrincipal.from(userA), eventId);
            flushAndClear(); // [추가] 삭제 후 클리어

            // Then
            assertThat(eventRepository.existsById(eventId)).isFalse();
            assertThat(sportRecordRepository.existsById(eventId)).isFalse();
        }

        @Test
        @DisplayName("SCENE 48~49: 예외 케이스")
        void deleteEvent_Exceptions() {
            Event event = createEventByService(userA, archiveAPublic.getId(), false, false, null);

            // 48: Forbidden
            assertThatThrownBy(() -> eventService.deleteEvent(UserPrincipal.from(userC), event.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 49: Not Found
            assertThatThrownBy(() -> eventService.deleteEvent(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 5]: Read-Pagination (Monthly)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Monthly Events")
    class Monthly {
        private final int YEAR = 2024;
        private final int MONTH = 5;

        @BeforeEach
        void setUpMonthlyData() {
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            for (int i = 1; i <= 10; i++) {
                boolean isSport = (i % 2 == 0);
                boolean hasTime = (i % 2 != 0);

                EventDto.SportRequest sportInfo = isSport ? new EventDto.SportRequest("A", "B", 1, 1) : null;
                EventDto.CreateRequest req = EventDto.CreateRequest.builder()
                        .title("Event " + i)
                        .date(LocalDate.of(YEAR, MONTH, i))
                        .hasTime(hasTime)
                        .time(hasTime ? LocalTime.of(12, 0) : null)
                        .color("#000000")
                        .isSportType(isSport)
                        .sportInfo(sportInfo)
                        .hashtags(List.of("tag"))
                        .build();
                eventService.createEvent(principal, archiveAPublic.getId(), req);
            }
            SecurityContextHolder.clearContext();

            // [중요] DB 반영 및 캐시 초기화 (Fetch Join 테스트 및 연관관계 로딩 보장)
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 50~53: PUBLIC Archive")
        void getMonthly_Public() {
            // 50: Owner
            List<EventDto.Response> resOwner = eventService.getMonthlyEvents(UserPrincipal.from(userA), archiveAPublic.getId(), YEAR, MONTH);
            assertThat(resOwner).hasSize(10);

            // 51: Stranger
            List<EventDto.Response> resStranger = eventService.getMonthlyEvents(UserPrincipal.from(userC), archiveAPublic.getId(), YEAR, MONTH);
            assertThat(resStranger).hasSize(10);

            // 52: Friend
            List<EventDto.Response> resFriend = eventService.getMonthlyEvents(UserPrincipal.from(userB), archiveAPublic.getId(), YEAR, MONTH);
            assertThat(resFriend).hasSize(10);

            // 53: Anonymous
            List<EventDto.Response> resAnon = eventService.getMonthlyEvents(null, archiveAPublic.getId(), YEAR, MONTH);
            assertThat(resAnon).hasSize(10);
        }

        @Test
        @DisplayName("SCENE 54~57: RESTRICTED Archive")
        void getMonthly_Restricted() {
            // 54: Owner -> OK
            assertThat(eventService.getMonthlyEvents(UserPrincipal.from(userA), archiveARestricted.getId(), YEAR, MONTH)).isEmpty();

            // 55: Friend -> OK
            assertThat(eventService.getMonthlyEvents(UserPrincipal.from(userB), archiveARestricted.getId(), YEAR, MONTH)).isEmpty();

            // 56: Stranger -> Fail
            assertThatThrownBy(() -> eventService.getMonthlyEvents(UserPrincipal.from(userC), archiveARestricted.getId(), YEAR, MONTH))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 57: Anonymous -> Fail
            assertThatThrownBy(() -> eventService.getMonthlyEvents(null, archiveARestricted.getId(), YEAR, MONTH))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 58~61: PRIVATE Archive")
        void getMonthly_Private() {
            // 58: Owner -> OK
            assertThat(eventService.getMonthlyEvents(UserPrincipal.from(userA), archiveAPrivate.getId(), YEAR, MONTH)).isEmpty();

            // 59~61: Others -> Fail
            assertThatThrownBy(() -> eventService.getMonthlyEvents(UserPrincipal.from(userB), archiveAPrivate.getId(), YEAR, MONTH)).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getMonthlyEvents(UserPrincipal.from(userC), archiveAPrivate.getId(), YEAR, MONTH)).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> eventService.getMonthlyEvents(null, archiveAPrivate.getId(), YEAR, MONTH)).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 62~63: 빈 결과, 존재하지 않는 아카이브")
        void getMonthly_Edge() {
            assertThat(eventService.getMonthlyEvents(UserPrincipal.from(userA), archiveAPublic.getId(), YEAR, MONTH + 1)).isEmpty();
            assertThatThrownBy(() -> eventService.getMonthlyEvents(UserPrincipal.from(userA), 99999L, YEAR, MONTH))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 64~67: 월 경계 처리")
        void getMonthly_Boundary() {
            // Setup: 4월 30일, 5월 1일, 5월 31일, 6월 1일 데이터 생성
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            // setUpMonthlyData와 별개로 추가
            createEventDirectly(principal, archiveAPublic.getId(), LocalDate.of(YEAR, 4, 30));
            createEventDirectly(principal, archiveAPublic.getId(), LocalDate.of(YEAR, 5, 1));
            createEventDirectly(principal, archiveAPublic.getId(), LocalDate.of(YEAR, 5, 31));
            createEventDirectly(principal, archiveAPublic.getId(), LocalDate.of(YEAR, 6, 1));

            flushAndClear(); // [중요] DB 반영

            // When: 5월 조회
            List<EventDto.Response> results = eventService.getMonthlyEvents(principal, archiveAPublic.getId(), YEAR, 5);

            // Then
            // 5월 1일 ~ 5월 10일(setupData: 10개) + 5월 1일(boundary: 1개) + 5월 31일(boundary: 1개)
            // 총 12개여야 함
            assertThat(results).hasSize(12);
            
            // 모든 결과가 5월인지 검증
            assertThat(results).extracting(EventDto.Response::getDate)
                    .allMatch(d -> d.getMonthValue() == 5);
            
            // 4월 30일은 제외되는지 검증
            assertThat(results).extracting(EventDto.Response::getDate)
                    .noneMatch(d -> d.getMonthValue() == 4);
            
            // 6월 1일은 제외되는지 검증
            assertThat(results).extracting(EventDto.Response::getDate)
                    .noneMatch(d -> d.getMonthValue() == 6);
            
            // 5월 1일과 5월 31일이 포함되는지 검증
            List<LocalDate> resultDates = results.stream()
                    .map(EventDto.Response::getDate)
                    .toList();
            assertThat(resultDates).contains(LocalDate.of(YEAR, 5, 1));
            assertThat(resultDates).contains(LocalDate.of(YEAR, 5, 31));
        }

        @Test
        @DisplayName("SCENE 68: 스포츠 타입 이벤트 포함 확인")
        void getMonthly_SportType() {
            // Given: Event 1 (홀수: 일반), Event 2 (짝수: 스포츠)
            List<EventDto.Response> results = eventService.getMonthlyEvents(
                    UserPrincipal.from(userA), archiveAPublic.getId(), YEAR, MONTH);

            assertThat(results).hasSize(10);

            // 1. 일반 이벤트 검증
            EventDto.Response normalEvent = results.stream().filter(e -> e.getTitle().equals("Event 1")).findFirst().orElseThrow();
            assertThat(normalEvent.isSportType()).isFalse();
            assertThat(normalEvent.getSportInfo()).isNull();

            // 2. 스포츠 이벤트 검증
            EventDto.Response sportEvent = results.stream().filter(e -> e.getTitle().equals("Event 2")).findFirst().orElseThrow();
            assertThat(sportEvent.isSportType()).isTrue();
            assertThat(sportEvent.getSportInfo()).isNotNull();
            assertThat(sportEvent.getSportInfo().getTeam1()).isEqualTo("A");
        }

        @Test
        @DisplayName("SCENE 69: hasTime 케이스 포함 확인")
        void getMonthly_HasTime() {
            // Given: Event 1 (홀수: hasTime=true), Event 2 (짝수: hasTime=false)
            List<EventDto.Response> results = eventService.getMonthlyEvents(
                    UserPrincipal.from(userA), archiveAPublic.getId(), YEAR, MONTH);

            // 1. hasTime = True
            EventDto.Response hasTimeEvent = results.stream().filter(e -> e.getTitle().equals("Event 1")).findFirst().orElseThrow();
            assertThat(hasTimeEvent.isHasTime()).isTrue();
            assertThat(hasTimeEvent.getTime()).isEqualTo(LocalTime.of(12, 0));

            // 2. hasTime = False
            EventDto.Response noTimeEvent = results.stream().filter(e -> e.getTitle().equals("Event 2")).findFirst().orElseThrow();
            assertThat(noTimeEvent.isHasTime()).isFalse();
            assertThat(noTimeEvent.getTime()).isNull();
        }
    }

    // ========================================================================================
    // [Category Limit 1]: Create Limit (SCENE Limit-1 ~ Limit-3)
    // ========================================================================================
    @Nested
    @DisplayName("[Category Limit 1] 이벤트 생성 제한 (Max 4)")
    class CreateLimit {

        @Test
        @DisplayName("SCENE Limit-1: 하루 4개까지 정상 등록")
        void create_BoundarySuccess() {
            // Given
            LocalDate date = LocalDate.of(2024, 12, 25);
            UserPrincipal principal = UserPrincipal.from(userA);

            // When: 4개 생성
            for(int i=0; i<4; i++) {
                createEventDirectly(principal, archiveAPublic.getId(), date);
            }

            // Then
            long count = eventRepository.countByArchiveIdAndDate(
                    archiveAPublic.getId(),
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay()
            );
            assertThat(count).isEqualTo(4);
        }

        @Test
        @DisplayName("SCENE Limit-2: 5번째 등록 시 예외 발생 (EVENT_LIMIT_EXCEEDED)")
        void create_LimitExceeded() {
            // Given: 4개 미리 생성
            LocalDate date = LocalDate.of(2024, 12, 25);
            UserPrincipal principal = UserPrincipal.from(userA);
            for(int i=0; i<4; i++) createEventDirectly(principal, archiveAPublic.getId(), date);

            // When & Then: 5번째 시도
            EventDto.CreateRequest req = EventDto.CreateRequest.builder()
                    .title("5th").date(date).color("#000").build();

            assertThatThrownBy(() -> eventService.createEvent(principal, archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("SCENE Limit-3: 다른 날짜에는 영향 없음")
        void create_DifferentDay() {
            // Given: 25일에 4개 생성
            LocalDate date1 = LocalDate.of(2024, 12, 25);
            LocalDate date2 = LocalDate.of(2024, 12, 26);
            UserPrincipal principal = UserPrincipal.from(userA);

            for(int i=0; i<4; i++) createEventDirectly(principal, archiveAPublic.getId(), date1);

            // When: 26일에 생성 -> 성공해야 함
            EventDto.CreateRequest req = EventDto.CreateRequest.builder()
                    .title("Other Day").date(date2).color("#000").build();

            EventDto.Response res = eventService.createEvent(principal, archiveAPublic.getId(), req);
            assertThat(res.getId()).isNotNull();
        }
    }

    // ========================================================================================
    // [Category Limit 2]: Update Limit (SCENE Limit-4 ~ Limit-6)
    // ========================================================================================
    @Nested
    @DisplayName("[Category Limit 2] 이벤트 수정 제한")
    class UpdateLimit {
        @Test
        @DisplayName("SCENE Limit-4: 날짜 변경 시, 대상 날짜가 꽉 차있으면 예외 발생")
        void update_DateConflict() {
            // Given
            LocalDate fullDate = LocalDate.of(2024, 1, 1);
            LocalDate sourceDate = LocalDate.of(2024, 1, 2);
            UserPrincipal principal = UserPrincipal.from(userA);

            // 1일: 4개 (Full)
            for(int i=0; i<4; i++) createEventDirectly(principal, archiveAPublic.getId(), fullDate);

            // 2일: 1개 (Source - 이동시킬 대상)
            // createEventDirectly는 void이므로, 생성 후 조회 필요
            createEventDirectly(principal, archiveAPublic.getId(), sourceDate);
            Event source = eventRepository.findAllByArchiveAndDateRange(
                    archiveAPublic.getId(), sourceDate.atStartOfDay(), sourceDate.plusDays(1).atStartOfDay()
            ).get(0);

            flushAndClear();

            // When: sourceEvent의 날짜를 fullDate로 변경 시도
            EventDto.UpdateRequest req = EventDto.UpdateRequest.builder()
                    .date(fullDate)
                    .build();

            // Then: 예외 발생
            assertThatThrownBy(() -> eventService.updateEvent(principal, source.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("SCENE Limit-5: 같은 날짜 내에서 시간/내용 변경은 제한 체크 패스")
        void update_SameDate_NoCheck() {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 1);
            UserPrincipal principal = UserPrincipal.from(userA);

            // 4개 채움
            for(int i=0; i<4; i++) createEventDirectly(principal, archiveAPublic.getId(), date);

            // 4번째 이벤트 가져오기
            List<Event> events = eventRepository.findAllByArchiveAndDateRange(
                    archiveAPublic.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            Event target = events.get(3);

            // When: 4번째 이벤트의 제목만 변경 (날짜 동일)
            EventDto.UpdateRequest req = EventDto.UpdateRequest.builder().date(date).title("Updated").build();

            // Then: 성공 (Limit Exception 안 남)
            EventDto.Response res = eventService.updateEvent(principal, target.getId(), req);
            assertThat(res.getTitle()).isEqualTo("Updated");
            assertThat(res.getDate()).isEqualTo(date);
        }

        @Test
        @DisplayName("SCENE Limit-6: 날짜 변경 시, 대상 날짜에 여유가 있으면 성공")
        void update_DateMove_Success() {
            // Given
            LocalDate sourceDate = LocalDate.of(2024, 1, 1);
            LocalDate targetDate = LocalDate.of(2024, 1, 2); // 0개
            UserPrincipal principal = UserPrincipal.from(userA);

            createEventDirectly(principal, archiveAPublic.getId(), sourceDate);
            Event event = eventRepository.findAllByArchiveAndDateRange(
                    archiveAPublic.getId(), sourceDate.atStartOfDay(), sourceDate.plusDays(1).atStartOfDay()
            ).get(0);

            // When
            EventDto.UpdateRequest req = EventDto.UpdateRequest.builder().date(targetDate).build();
            EventDto.Response res = eventService.updateEvent(principal, event.getId(), req);

            // Then
            assertThat(res.getDate()).isEqualTo(targetDate);
        }
    }

    // ========================================================================================
    // [Category Query]: Query Consistency (SCENE Query-1)
    // ========================================================================================
    @Nested
    @DisplayName("[Category Query] 쿼리 정합성 (< vs <=)")
    class QueryConsistency {

        @Test
        @DisplayName("SCENE Query-1: 다음날 00:00 데이터가 현재 날짜 개수에 포함되지 않는지 확인")
        void check_NextDayMidnight() {
            // Given
            LocalDate may5 = LocalDate.of(2024, 5, 5);
            UserPrincipal principal = UserPrincipal.from(userA);

            // 1. 5월 5일 23:59:00 생성 (포함되어야 함)
            // -> 여기서는 직접 DTO 만들어서 호출
            EventDto.CreateRequest req1 = EventDto.CreateRequest.builder()
                    .title("Late").date(may5).hasTime(true).time(LocalTime.of(23, 59)).color("#000").build();
            eventService.createEvent(principal, archiveAPublic.getId(), req1);

            // 2. 5월 6일 00:00:00 생성 (제외되어야 함)
            EventDto.CreateRequest req2 = EventDto.CreateRequest.builder()
                    .title("Midnight").date(may5.plusDays(1)).hasTime(true).time(LocalTime.MIDNIGHT).color("#000").build();
            eventService.createEvent(principal, archiveAPublic.getId(), req2);

            flushAndClear();

            // Service 로직: start = 5/5 00:00, end = 5/6 00:00

            LocalDateTime start = may5.atStartOfDay();
            LocalDateTime end = start.plusDays(1); // 5/6 00:00

            long count = eventRepository.countByArchiveIdAndDate(archiveAPublic.getId(), start, end);

            // Then: 5월 5일 데이터만 카운트되어야 하므로 1개
            assertThat(count).isEqualTo(1);
        }
    }

    // --- Helpers ---
    private Event createEventByService(User owner, Long archiveId, boolean isSport, boolean hasTime, List<String> hashtags) {
        setupMockUser(owner);
        UserPrincipal principal = UserPrincipal.from(owner);

        EventDto.SportRequest sportInfo = isSport ? new EventDto.SportRequest("A", "B", 1, 2) : null;

        EventDto.CreateRequest request = EventDto.CreateRequest.builder()
                .title("Test Event")
                .date(LocalDate.now())
                .time(hasTime ? LocalTime.of(12, 0) : null) // hasTime True면 시간 설정, 아니면 null
                .hasTime(hasTime)
                .color("#FF5733")
                .isSportType(isSport)
                .sportInfo(sportInfo)
                .hashtags(hashtags)
                .build();

        EventDto.Response response = eventService.createEvent(principal, archiveId, request);
        SecurityContextHolder.clearContext();
        return eventRepository.findById(response.getId()).orElseThrow();
    }

    private void createEventDirectly(UserPrincipal principal, Long archiveId, LocalDate date) {
        EventDto.CreateRequest req = EventDto.CreateRequest.builder()
                .title("Boundary")
                .date(date)
                .color("#000")
                .build();
        eventService.createEvent(principal, archiveId, req);
    }
}