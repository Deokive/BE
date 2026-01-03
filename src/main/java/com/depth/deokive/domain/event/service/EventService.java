package com.depth.deokive.domain.event.service;

import com.depth.deokive.common.service.ArchiveGuard;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.entity.*;
import com.depth.deokive.domain.event.repository.*;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final ArchiveGuard archiveGuard;
    private final EventRepository eventRepository;
    private final SportRecordRepository sportRecordRepository;
    private final HashtagRepository hashtagRepository;
    private final EventHashtagMapRepository eventHashtagMapRepository;
    private final ArchiveRepository archiveRepository;

    @Transactional
    public EventDto.Response createEvent(UserPrincipal user, Long archiveId, EventDto.CreateRequest request) {
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 검증
        archiveGuard.checkOwner(archive.getUser().getId(), user);

        // SEQ 3. 날짜/시간 병합 로직
        LocalDateTime recordAt = getRecordAt(request);

        // SEQ 4. Event 저장
        Event event = request.toEntity(archive, recordAt);
        eventRepository.save(event);

        // SEQ 5. 스포츠 기록 저장 (필요 시)
        SportRecord sportRecord = null;
        if (request.getIsSportType() && request.getSportInfo() != null) {
            sportRecord = request.getSportInfo().toEntity(event);
            sportRecordRepository.save(sportRecord);
        }

        // SEQ 6. 해시태그 저장
        saveHashtags(event, request.getHashtags());

        return EventDto.Response.of(event, sportRecord, request.getHashtags());
    }

    @Transactional(readOnly = true)
    public EventDto.Response getEvent(UserPrincipal user, Long eventId) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. Archive 접근 권한에 따른 읽기 범위 설정
        archiveGuard.checkArchiveReadPermission(event.getArchive(), user);

        // SEQ 3. 스포츠 타입 On 이면 스포츠 정보 로드
        SportRecord sportRecord = null;
        if (event.isSportType()) {
            sportRecord = sportRecordRepository.findById(eventId).orElse(null);
        }

        // SEQ 4. 해시 태그 로드
        List<String> hashtags = eventHashtagMapRepository.findHashtagNamesByEventId(eventId);

        return EventDto.Response.of(event, sportRecord, hashtags);
    }

    @Transactional
    public EventDto.Response updateEvent(UserPrincipal user, Long eventId, EventDto.UpdateRequest request) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. 소유권 검증
        archiveGuard.checkOwner(event.getArchive().getUser().getId(), user);

        // SEQ 3. 업데이트
        event.update(request, getRecordAt(request, event)); // Dirty Checking

        // SEQ 4. 스포츠 기록 처리
        SportRecord sportRecord = handleSportRecordUpdate(event, request);

        // SEQ 5. 해시태그 업데이트 (기존 삭제 후 재등록 방식)
        if (request.getHashtags() != null) {
            eventHashtagMapRepository.deleteByEventId(eventId);
            saveHashtags(event, request.getHashtags());
        }

        // SEQ 6. 현재 해시태그 조회 (업데이트 안하는 경우 고려)
        List<String> currentHashtags = (request.getHashtags() != null)
                ? request.getHashtags()
                : eventHashtagMapRepository.findHashtagNamesByEventId(eventId);

        return EventDto.Response.of(event, sportRecord, currentHashtags);
    }

    @Transactional
    public void deleteEvent(UserPrincipal user, Long eventId) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. 소유권 검증
        archiveGuard.checkOwner(event.getArchive().getUser().getId(), user);

        // SEQ 3. Event 삭제
        eventHashtagMapRepository.deleteByEventId(eventId); // 1:N에 대해서 명시적으로 연관 데이터 삭제 패턴 권장하는 중
        if (event.isSportType()) {
            sportRecordRepository.deleteById(eventId);
        }

        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventDto.Response> getMonthlyEvents(UserPrincipal user, Long archiveId, int year, int month) {
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 아카이브 자체의 접근 권한 확인 (주인인지, 공개인지 등)
        archiveGuard.checkArchiveReadPermission(archive, user);

        // SEQ 3. 날짜 범위 계산 (해당 월의 1일 00:00 ~ 말일 23:59)
        LocalDateTime startDateTime = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endDateTime = startDateTime.plusMonths(1).minusNanos(1);

        // SEQ 4. 이벤트 조회
        List<Event> events = eventRepository.findAllByArchiveAndDateRange(archiveId, startDateTime, endDateTime);

        if (events.isEmpty()) { return Collections.emptyList(); }

        // SEQ 4. 해시태그 최적화 조회 (N+1 방지)
        // 4-1. 조회된 이벤트들의 ID 리스트 추출
        List<Long> eventIds = events.stream().map(Event::getId).toList();

        // 4-2. 해당 이벤트들에 속한 모든 해시태그 매핑을 한 번에 조회 (bulk)
        Map<Long, List<String>> hashtagMap = getHashtagMap(eventIds);

        // SEQ 5. DTO 변환 및 반환
        return events.stream()
                .map(event -> EventDto.Response.of(
                        event,
                        event.getSportRecord(), // Fetch Join 되었으므로 즉시 접근 가능
                        hashtagMap.getOrDefault(event.getId(), Collections.emptyList()) // Map에서 O(1) 조회
                ))
                .toList();
    }

    // --- Helper Methods ---

    private LocalDateTime getRecordAt(EventDto.CreateRequest r) {
        LocalTime time = (Boolean.TRUE.equals(r.getHasTime()) && r.getTime() != null) ? r.getTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(r.getDate(), time);
    }

    private LocalDateTime getRecordAt(EventDto.UpdateRequest r, Event event) {
        LocalDate date = r.getDate() != null ? r.getDate() : event.getDate().toLocalDate();
        LocalTime time;

        boolean effectiveHasTime = r.getHasTime() != null ? r.getHasTime() : event.isHasTime();

        if (effectiveHasTime) {
            if (r.getTime() != null) time = r.getTime();
            else if (event.isHasTime()) time = event.getDate().toLocalTime();
            else time = LocalTime.of(0, 0);
        } else {
            time = LocalTime.of(0, 0);
        }

        return LocalDateTime.of(date, time);
    }

    private SportRecord handleSportRecordUpdate(Event event, EventDto.UpdateRequest request) {

        boolean shouldBeSportType = (request.getIsSportType() != null)
                ? request.getIsSportType() // 변경 대상
                : event.isSportType(); // 기존

        // Case 1: 스포츠 타입 ON
        if (shouldBeSportType) {
            if(request.getSportInfo() != null){
                SportRecord existingRecord = event.getSportRecord();
                if(existingRecord != null) {
                    existingRecord.update(request.getSportInfo());
                    return existingRecord;
                } else {
                    SportRecord newRecord = request.getSportInfo().toEntity(event);
                    event.registerSportRecord(newRecord); // 양방향 정합성
                    return sportRecordRepository.save(newRecord);
                }
            }
            return event.getSportRecord(); // 정보 업데이트 요청이 없던 경우
        }
        // Case 2: 스포츠 타입 OFF
        else {
            if (event.getSportRecord() != null) {
                event.deleteSportRecord();
            }
            return null;
        }
    }

    private void saveHashtags(Event event, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;

        // 중복 제거 (Set) 처리
        List<String> uniqueNames = tagNames.stream().distinct().toList();

        for (String name : uniqueNames) {
            // 태그가 존재하면 찾고, 없으면 생성 (Find or Create)
            Hashtag hashtag = hashtagRepository.findByName(name)
                    .orElseGet(() -> hashtagRepository.save(Hashtag.builder().name(name).build()));

            // 매핑 테이블 저장
            EventHashtagMap map = EventHashtagMap.builder()
                    .event(event)
                    .hashtag(hashtag)
                    .build();
            eventHashtagMapRepository.save(map);
        }
    }

    private Map<Long, List<String>> getHashtagMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Collections.emptyMap();
        List<EventHashtagMap> maps = eventHashtagMapRepository.findAllByEventIdIn(eventIds);

        return maps.stream()
                .collect(Collectors.groupingBy(
                        map -> map.getEvent().getId(),
                        Collectors.mapping(map -> map.getHashtag().getName(), Collectors.toList())
                ));
    }
}