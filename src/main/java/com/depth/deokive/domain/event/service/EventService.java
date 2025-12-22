package com.depth.deokive.domain.event.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SportRecordRepository sportRecordRepository;
    private final HashtagRepository hashtagRepository;
    private final EventHashtagMapRepository eventHashtagMapRepository;
    private final ArchiveRepository archiveRepository;

    @Transactional
    public EventDto.Response createEvent(UserPrincipal user, Long archiveId, EventDto.Request request) {
        // SEQ 1. 아카이브 조회
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new RestException(ErrorCode.ARCHIVE_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateOwner(archive, user);

        // SEQ 3. 날짜/시간 병합 로직
        LocalDateTime recordAt = mergeDateTime(request);

        // SEQ 4. Event 저장
        Event event = request.toEntity(archive, recordAt);
        eventRepository.save(event);

        // SEQ 5. 스포츠 기록 저장 (필요 시)
        SportRecord sportRecord = null;
        if (request.getIsSportType() && request.getSportInfo() != null) {
            sportRecord = saveSportRecord(event, request.getSportInfo());
        }

        // SEQ 6. 해시태그 저장
        updateHashtags(event, request.getHashtags());

        return EventDto.Response.of(event, sportRecord, request.getHashtags());
    }

    @Transactional(readOnly = true)
    public EventDto.Response getEvent(UserPrincipal user, Long eventId) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. Archive 접근 권한에 따른 읽기 범위 설정
        validateReadPermission(event, user);

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
    public EventDto.Response updateEvent(UserPrincipal user, Long eventId, EventDto.Request request) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateOwner(event.getArchive(), user);

        // SEQ 3. 업데이트
        LocalDateTime recordAt = mergeDateTime(request);
        event.update(request, recordAt); // Dirty Checking

        // SEQ 4. 스포츠 기록 처리
        SportRecord sportRecord = handleSportRecordUpdate(event, request);

        // SEQ 5. 해시태그 업데이트 (기존 삭제 후 재등록 방식) // TODO: 왜 그렇게 하지?
        eventHashtagMapRepository.deleteByEventId(eventId);
        updateHashtags(event, request.getHashtags());

        return EventDto.Response.of(event, sportRecord, request.getHashtags());
    }

    @Transactional
    public void deleteEvent(UserPrincipal user, Long eventId) {
        // SEQ 1. Event 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RestException(ErrorCode.EVENT_NOT_FOUND));

        // SEQ 2. 소유권 검증
        validateOwner(event.getArchive(), user);

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
        validateArchiveReadPermission(archive, user);

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
        List<EventHashtagMap> hashtagMaps = eventHashtagMapRepository.findAllByEventIdIn(eventIds);

        // 4-3. 메모리에서 EventId 별로 해시태그 이름 리스트 그룹핑 (Map<EventId, List<TagName>>)
        Map<Long, List<String>> hashtagsMap = hashtagMaps.stream()
                .collect(Collectors.groupingBy(
                        map -> map.getEvent().getId(),
                        Collectors.mapping(map -> map.getHashtag().getName(), Collectors.toList())
                ));

        // SEQ 5. DTO 변환 및 반환
        return events.stream()
                .map(event -> EventDto.Response.of(
                        event,
                        event.getSportRecord(), // Fetch Join 되었으므로 즉시 접근 가능
                        hashtagsMap.getOrDefault(event.getId(), Collections.emptyList()) // Map에서 O(1) 조회
                ))
                .toList();
    }

    // --- Helper Methods ---

    private LocalDateTime mergeDateTime(EventDto.Request request) {
        LocalTime time = (request.getHasTime() && request.getTime() != null)
                ? request.getTime()
                : LocalTime.of(0, 0);
        return LocalDateTime.of(request.getDate(), time);
    }

    private SportRecord saveSportRecord(Event event, EventDto.SportRequest info) {
        SportRecord record = SportRecord.builder()
                .event(event)
                .team1(info.getTeam1())
                .team2(info.getTeam2())
                .score1(info.getScore1())
                .score2(info.getScore2())
                .build();
        return sportRecordRepository.save(record);
    }

    private SportRecord handleSportRecordUpdate(Event event, EventDto.Request request) {
        // Case 1: 스포츠 타입 ON
        if (request.getIsSportType()) {
            EventDto.SportRequest info = request.getSportInfo();

            SportRecord existingRecord = event.getSportRecord();

            if (existingRecord != null) {
                existingRecord.update(request.getSportInfo());
                return existingRecord;
            } else {
                return saveSportRecord(event, info);
            }
        }
        // Case 2: 스포츠 타입 OFF
        else {
            if (event.getSportRecord() != null) {
                sportRecordRepository.deleteById(event.getId());
            }
            return null;
        }
    }

    private void updateHashtags(Event event, List<String> newTagNames) {
        // SEQ 1. null 처리 (빈 리스트로 간주)
        List<String> requestTags = (newTagNames == null) ? List.of() : newTagNames;

        // SEQ 2. 현재 DB에 저장된 태그 매핑 조회
        List<EventHashtagMap> currentMaps = eventHashtagMapRepository.findAllByEventId(event.getId());

        // SEQ 3. 현재 태그 이름 추출
        List<String> currentTagNames = currentMaps.stream()
                .map(map -> map.getHashtag().getName())
                .toList();

        // SEQ 4. 삭제할 태그 찾기 (기존엔 있는데, 요청엔 없는 것)
        List<EventHashtagMap> toDelete = currentMaps.stream()
                .filter(map -> !requestTags.contains(map.getHashtag().getName()))
                .toList();

        // SEQ 5. 삭제할 태그만 타겟팅해서 삭제
        eventHashtagMapRepository.deleteAll(toDelete);

        // SEQ 6. 추가할 태그 찾기 (기존엔 없는데, 요청엔 있는 것)
        List<String> toAdd = requestTags.stream()
                .filter(name -> !currentTagNames.contains(name))
                .toList();

        // SEQ 7. 추가할 타겟 태그들 추가
        for (String name : toAdd) {
            Hashtag hashtag = hashtagRepository.findByName(name)
                    .orElseGet(() -> hashtagRepository.save(Hashtag.builder().name(name).build()));

            EventHashtagMap map = EventHashtagMap.builder()
                    .event(event)
                    .hashtag(hashtag)
                    .build();

            eventHashtagMapRepository.save(map);
        }
    }

    private void validateOwner(Archive archive, UserPrincipal user) {
        if (!archive.getUser().getId().equals(user.getUserId())) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateReadPermission(Event event, UserPrincipal userPrincipal) {
        Long ownerId = event.getArchive().getUser().getId();
        Long viewerId = userPrincipal != null ? userPrincipal.getUserId() : null;
        Visibility visibility = event.getArchive().getVisibility();

        if (ownerId.equals(viewerId)) return;
        // TODO: Visibility.RESTRICTED일 경우 친구 관계 검증 : 아직 친구관계 설정 도메인이 구현안되어있으니 TODO로 남김
        if (visibility != Visibility.PUBLIC) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // validateReadPermission은 단건 조회용(Event 기준)이라, 아카이브 기준 검증 메서드 분리
    private void validateArchiveReadPermission(Archive archive, UserPrincipal userPrincipal) {
        Long ownerId = archive.getUser().getId();
        Long viewerId = userPrincipal != null ? userPrincipal.getUserId() : null;

        if (ownerId.equals(viewerId)) return;

        if (archive.getVisibility() != Visibility.PUBLIC) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }
}