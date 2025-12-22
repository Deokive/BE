package com.depth.deokive.domain.event.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.entity.*;
import com.depth.deokive.domain.event.repository.*;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks private EventService eventService;

    @Mock private EventRepository eventRepository;
    @Mock private SportRecordRepository sportRecordRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private EventHashtagMapRepository eventHashtagMapRepository;
    @Mock private ArchiveRepository archiveRepository;

    // --- Fixture Helpers ---
    private UserPrincipal makePrincipal(Long userId) {
        return UserPrincipal.builder().userId(userId).role(Role.USER).build();
    }

    private User createUser(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Archive createArchive(Long id, User owner) {
        Archive archive = Archive.builder().user(owner).visibility(Visibility.PUBLIC).build();
        ReflectionTestUtils.setField(archive, "id", id);
        return archive;
    }

    private Event createEvent(Long id, Archive archive, boolean isSport) {
        Event event = Event.builder()
                .archive(archive)
                .title("Test Event")
                .date(LocalDateTime.now())
                .isSportType(isSport)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private EventDto.Request createRequest(boolean hasTime, boolean isSport) {
        EventDto.Request req = new EventDto.Request();
        req.setTitle("New Event");
        req.setDate(LocalDate.of(2025, 5, 5));
        req.setHasTime(hasTime);
        req.setColor("#000000");
        req.setIsSportType(isSport);
        if (hasTime) req.setTime(LocalTime.of(10, 0));
        if (isSport) {
            EventDto.SportRequest sport = new EventDto.SportRequest();
            sport.setTeam1("A");
            sport.setTeam2("B");
            sport.setScore1(1);
            sport.setScore2(2);
            req.setSportInfo(sport);
        }
        return req;
    }

    @Nested
    @DisplayName("📝 일정 생성 (Create)")
    class CreateTest {

        @Test
        @DisplayName("성공: 스포츠 기록과 태그가 포함된 완전한 일정을 생성한다.")
        void createEvent_Success_FullOption() {
            // given
            Long userId = 1L;
            Long archiveId = 100L;
            UserPrincipal principal = makePrincipal(userId);
            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            EventDto.Request request = createRequest(true, true);
            request.setHashtags(List.of("축구", "결승"));

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));
            given(eventRepository.save(any(Event.class))).willAnswer(inv -> {
                Event e = inv.getArgument(0);
                ReflectionTestUtils.setField(e, "id", 1L);
                return e;
            });

            given(sportRecordRepository.save(any(SportRecord.class))).willAnswer(inv -> inv.getArgument(0));

            // Hashtag Mocking (Find or Create)
            given(hashtagRepository.findByName(anyString())).willReturn(Optional.empty()); // 항상 새로 생성 가정
            given(hashtagRepository.save(any(Hashtag.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            EventDto.Response response = eventService.createEvent(principal, archiveId, request);

            // then
            assertThat(response.getTitle()).isEqualTo("New Event");
            assertThat(response.getSportInfo()).isNotNull();
            assertThat(response.getSportInfo().getTeam1()).isEqualTo("A");
            verify(sportRecordRepository).save(any(SportRecord.class)); // 스포츠 기록 저장 확인
            verify(eventHashtagMapRepository, times(2)).save(any(EventHashtagMap.class)); // 태그 2개 저장 확인
        }

        @Test
        @DisplayName("실패: 아카이브 주인이 아니면 생성할 수 없다.")
        void createEvent_Fail_Forbidden() {
            // given
            Long ownerId = 1L;
            Long intruderId = 2L;
            UserPrincipal intruder = makePrincipal(intruderId);
            User owner = createUser(ownerId);
            Archive archive = createArchive(100L, owner);
            EventDto.Request request = createRequest(false, false);

            given(archiveRepository.findById(100L)).willReturn(Optional.of(archive));

            // when & then
            assertThatThrownBy(() -> eventService.createEvent(intruder, 100L, request))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("📅 월별 조회 (Monthly View)")
    class MonthlyTest {

        @Test
        @DisplayName("성공: N+1 문제 없이 이벤트와 해시태그를 대량 조회한다.")
        void getMonthlyEvents_Success_Optimization() {
            // given
            Long userId = 1L;
            Long archiveId = 100L;
            UserPrincipal principal = makePrincipal(userId);
            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);

            // Event 2개 준비
            Event e1 = createEvent(10L, archive, false);
            Event e2 = createEvent(20L, archive, false);
            List<Event> events = List.of(e1, e2);

            // Hashtag Map 준비 (Bulk Fetch 결과 모킹)
            Hashtag tag1 = Hashtag.builder().name("TagA").build();
            Hashtag tag2 = Hashtag.builder().name("TagB").build();

            EventHashtagMap map1 = EventHashtagMap.builder().event(e1).hashtag(tag1).build(); // Event 1 -> TagA
            EventHashtagMap map2 = EventHashtagMap.builder().event(e2).hashtag(tag2).build(); // Event 2 -> TagB

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));
            given(eventRepository.findAllByArchiveAndDateRange(eq(archiveId), any(), any())).willReturn(events);

            // ⭐ 핵심: ID 리스트로 한 번에 조회하는지 검증
            given(eventHashtagMapRepository.findAllByEventIdIn(List.of(10L, 20L))).willReturn(List.of(map1, map2));

            // when
            List<EventDto.Response> responses = eventService.getMonthlyEvents(principal, archiveId, 2025, 5);

            // then
            assertThat(responses).hasSize(2);

            // 메모리 매핑 검증
            EventDto.Response res1 = responses.stream().filter(r -> r.getId().equals(10L)).findFirst().get();
            assertThat(res1.getHashtags()).containsExactly("TagA");

            EventDto.Response res2 = responses.stream().filter(r -> r.getId().equals(20L)).findFirst().get();
            assertThat(res2.getHashtags()).containsExactly("TagB");
        }
    }

    @Nested
    @DisplayName("🔄 일정 수정 (Update)")
    class UpdateTest {

        @Test
        @DisplayName("성공: 해시태그 수정 시 'Diff 방식'으로 동작하여 불필요한 삭제를 방지한다.")
        void updateEvent_Success_TagDiff() {
            // given
            // 상황: 기존 태그 [A, B] -> 요청 태그 [B, C]
            // 기대: A 삭제, C 추가, B 유지
            Long eventId = 10L;
            UserPrincipal principal = makePrincipal(1L);
            User user = createUser(1L);
            Archive archive = createArchive(100L, user);
            Event event = createEvent(eventId, archive, false);

            EventDto.Request request = createRequest(false, false);
            request.setHashtags(List.of("B", "C"));

            // 기존 태그 데이터 Mocking
            Hashtag tagA = Hashtag.builder().name("A").build();
            Hashtag tagB = Hashtag.builder().name("B").build();
            EventHashtagMap mapA = EventHashtagMap.builder().event(event).hashtag(tagA).build();
            EventHashtagMap mapB = EventHashtagMap.builder().event(event).hashtag(tagB).build();

            given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
            // 기존 매핑 조회
            given(eventHashtagMapRepository.findAllByEventId(eventId)).willReturn(List.of(mapA, mapB));

            // "C" 태그 생성 Mocking
            given(hashtagRepository.findByName("C")).willReturn(Optional.empty());
            given(hashtagRepository.save(any(Hashtag.class))).willAnswer(inv -> {
                Hashtag h = inv.getArgument(0);
                return Hashtag.builder().name(h.getName()).build();
            });

            // when
            eventService.updateEvent(principal, eventId, request);

            // then
            // 1. Delete 검증: "A"만 포함된 리스트가 삭제되어야 함
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<EventHashtagMap>> deleteCaptor = ArgumentCaptor.forClass(List.class);
            verify(eventHashtagMapRepository).deleteAll(deleteCaptor.capture());

            List<EventHashtagMap> deletedList = deleteCaptor.getValue();
            assertThat(deletedList).hasSize(1);
            assertThat(deletedList.get(0).getHashtag().getName()).isEqualTo("A");

            // 2. Insert 검증: "C"만 추가되어야 함
            ArgumentCaptor<EventHashtagMap> saveCaptor = ArgumentCaptor.forClass(EventHashtagMap.class);
            verify(eventHashtagMapRepository).save(saveCaptor.capture());
            assertThat(saveCaptor.getValue().getHashtag().getName()).isEqualTo("C");
        }

        @Test
        @DisplayName("성공: 스포츠 기능을 끄면(OFF) 기존 스포츠 기록이 삭제된다.")
        void updateEvent_Success_SportToggleOff() {
            // given
            Long eventId = 10L;
            UserPrincipal principal = makePrincipal(1L);
            Archive archive = createArchive(100L, createUser(1L));

            Event event = createEvent(eventId, archive, true);
            SportRecord record = SportRecord.builder().event(event).build();
            ReflectionTestUtils.setField(event, "sportRecord", record); // 양방향 매핑 강제 주입

            EventDto.Request request = createRequest(false, false); // 요청은 스포츠 OFF

            given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

            // when
            eventService.updateEvent(principal, eventId, request);

            // then
            verify(sportRecordRepository).deleteById(eventId);
        }
    }

    @Nested
    @DisplayName("🗑️ 일정 삭제 (Delete)")
    class DeleteTest {

        @Test
        @DisplayName("성공: 일정 삭제 시 태그 매핑과 스포츠 기록도 명시적으로 삭제한다.")
        void deleteEvent_Success() {
            // given
            Long eventId = 10L;
            UserPrincipal principal = makePrincipal(1L);
            Archive archive = createArchive(100L, createUser(1L));
            Event event = createEvent(eventId, archive, true); // 스포츠 타입

            given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

            // when
            eventService.deleteEvent(principal, eventId);

            // then
            verify(eventHashtagMapRepository).deleteByEventId(eventId); // 태그 삭제
            verify(sportRecordRepository).deleteById(eventId); // 스포츠 기록 삭제
            verify(eventRepository).delete(event); // 이벤트 삭제
        }
    }
}