package com.depth.deokive.common.api.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Event 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis, MailHog
 * 2. 공통 유틸 (AuthSteps, ArchiveSteps, FriendSteps):
 * - 회원가입/로그인, 아카이브 생성, 친구 맺기 등의 선행 작업 수행
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Me): 이벤트 주인
 * - UserB (Friend): UserA의 친구
 * - UserC (Stranger): 타인
 * - Anonymous: 비회원
 *
 * ■ 주요 검증 포인트
 * - hasTime 토글에 따른 시간 데이터 처리 검증
 * - isSportType 토글에 따른 SportRecord 생성/삭제 생명주기 검증
 * - 해시태그(Hashtag) 등록, 중복 제거, 수정 시 교체 로직 검증
 * - 월별 조회(Monthly) 시 날짜 범위 필터링 정확도
 */
@DisplayName("Event API 통합 테스트 시나리오")
class EventApiTest {

    /**
     * [Setup]
     * 1. AuthSteps: UserA, UserB, UserC 토큰 확보
     * 2. FriendSteps: UserA <-> UserB 친구 설정
     * 3. ArchiveSteps: UserA의 Public, Restricted, Private 아카이브 생성 -> ID 확보
     */

    // ========================================================================================
    // [Category 1]. Create Event (POST /api/v1/events/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 이벤트 생성")
    class CreateEvent {

        /** SCENE 1. 정상 생성 - 일반 이벤트 (시간 포함, 해시태그 포함) */
        // Given: UserA 토큰, Public Archive ID
        // Given: Request Body (title="콘서트", date=2024-01-01, hasTime=true, time=14:30, color="#FF5733", hashtags=["concert", "live"])
        // When: POST /api/v1/events/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.id가 null이 아니고 유효한 Long 값인지 확인
        // Then: **응답 Body 검증** - response.title == "콘서트" 확인
        // Then: **응답 Body 검증** - response.date == 2024-01-01 확인
        // Then: **응답 Body 검증** - response.hasTime == true 확인
        // Then: **응답 Body 검증** - response.time == 14:30 확인
        // Then: **응답 Body 검증** - response.color == "#FF5733" 확인
        // Then: **응답 Body 검증** - response.isSportType == false 확인
        // Then: **응답 Body 검증** - response.sportInfo == null 확인
        // Then: **응답 Body 검증** - response.hashtags 리스트가 2개인지 확인
        // Then: **응답 Body 검증** - response.hashtags.contains("concert") 확인
        // Then: **응답 Body 검증** - response.hashtags.contains("live") 확인
        // Then: **DB 검증** - eventRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 title == "콘서트" 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 hasTime == true 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 date.toLocalTime() == 14:30 확인
        // Then: **DB 검증** - eventHashtagMapRepository.findAllByEventId(response.getId())가 2개인지 확인
        // Then: **DB 검증** - hashtagRepository.findByName("concert").isPresent() == true 확인
        // Then: **DB 검증** - hashtagRepository.findByName("live").isPresent() == true 확인

        /** SCENE 2. 정상 생성 - 시간 없는 이벤트 (All Day) */
        // Given: UserA 토큰, Public Archive ID
        // Given: hasTime=false, time=null (혹은 값 있어도 무시되어야 함)
        // When: POST /api/v1/events/{archiveId}
        // Then: 201 Created
        // Then: 응답 데이터에 hasTime=false 확인
        // Then: 응답의 date 필드의 시간 부분이 00:00:00인지 확인 (또는 무시됨)
        // Then: DB에서 Event 엔티티의 hasTime=false, date의 시간 부분이 00:00:00인지 확인

        /** SCENE 3. 정상 생성 - 스포츠 이벤트 (SportRecord 포함) */
        // Given: UserA 토큰, Public Archive ID
        // Given: Request Body (title="야구 경기", isSportType=true, sportInfo={team1: "A", team2: "B", score1: 1, score2: 0})
        // When: POST /api/v1/events/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.isSportType == true 확인
        // Then: **응답 Body 검증** - response.sportInfo != null 확인
        // Then: **응답 Body 검증** - response.sportInfo.team1 == "A" 확인
        // Then: **응답 Body 검증** - response.sportInfo.team2 == "B" 확인
        // Then: **응답 Body 검증** - response.sportInfo.score1 == 1 확인
        // Then: **응답 Body 검증** - response.sportInfo.score2 == 0 확인
        // Then: **DB 검증** - eventRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 isSportType == true 확인
        // Then: **DB 검증** - sportRecordRepository.existsById(response.getId()) == true 확인
        // Then: **DB 검증** - sportRecordRepository.findById(response.getId()).get().team1 == "A" 확인
        // Then: **DB 검증** - sportRecordRepository.findById(response.getId()).get().team2 == "B" 확인
        // Then: **DB 검증** - sportRecordRepository.findById(response.getId()).get().score1 == 1 확인
        // Then: **DB 검증** - sportRecordRepository.findById(response.getId()).get().score2 == 0 확인

        /** SCENE 4. 정상 생성 - 중복 해시태그 처리 */
        // Given: UserA 토큰, hashtags=["tag1", "tag1", "tag2"]
        // When: POST /api/v1/events/{archiveId}
        // Then: 201 Created
        // Then: 응답 내 hashtags가 ["tag1", "tag2"]로 중복 제거되었는지 확인
        // Then: DB에서 Hashtag 엔티티가 2개만 생성되었는지 확인 (중복 제거)
        // Then: DB에서 EventHashtagMap이 2개만 생성되었는지 확인

        /** SCENE 5. 예외 - 필수값 누락 (제목 없음) */
        // Given: title=""
        // When: POST /api/v1/events/{archiveId}
        // Then: 400 Bad Request

        /** SCENE 6. 예외 - 타인의 아카이브에 생성 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // When: POST /api/v1/events/{archiveId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN)

        /** SCENE 7. 예외 - 존재하지 않는 아카이브 */
        // Given: Random ID
        // Then: 404 Not Found (ARCHIVE_NOT_FOUND)
    }

    // ========================================================================================
    // [Category 2]. Read Detail (GET /api/v1/events/{eventId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 이벤트 상세 조회 (권한 및 데이터)")
    class ReadEvent {
        // Setup: UserA가 다양한 타입의 이벤트를 미리 생성
        // - Event_Normal (in Public Archive)
        // - Event_Sport (in Public Archive)
        // - Event_Restricted (in Restricted Archive)
        // - Event_Private (in Private Archive)

        /** SCENE 8. PUBLIC 일반 이벤트 조회 - 데이터 무결성 확인 */
        // Given: UserC(타인) 토큰, Event_Normal ID (hasTime=true, time=14:30, hashtags 포함)
        // When: GET /api/v1/events/{id}
        // Then: 200 OK
        // Then: 응답의 title, date, color, hasTime=true, time=14:30, hashtags 등 모든 필드 검증
        // Then: 응답의 sportInfo는 null이어야 함
        // Then: 응답의 date 필드의 시간 부분이 14:30:00인지 확인

        /** SCENE 9. PUBLIC 스포츠 이벤트 조회 - 스포츠 데이터 확인 */
        // Given: UserC(타인) 토큰, Event_Sport ID
        // When: GET /api/v1/events/{id}
        // Then: 200 OK
        // Then: sportInfo가 null이 아니고 점수 정보가 포함되어야 함

        /** SCENE 10. RESTRICTED 이벤트 조회 - 친구(UserB) 가능 */
        // Given: UserB 토큰, Event_Restricted ID
        // When: GET /api/v1/events/{id}
        // Then: 200 OK

        /** SCENE 11. RESTRICTED 이벤트 조회 - 타인(UserC) 불가 */
        // Given: UserC 토큰, Event_Restricted ID
        // When: GET /api/v1/events/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        /** SCENE 12. PRIVATE 이벤트 조회 - 타인 불가 */
        // Given: UserC 토큰, Event_Private ID
        // Then: 403 Forbidden

        /** SCENE 13. 존재하지 않는 이벤트 조회 */
        // When: GET /api/v1/events/99999
        // Then: 404 Not Found (EVENT_NOT_FOUND)
    }

    // ========================================================================================
    // [Category 3]. Update Event (PATCH /api/v1/events/{eventId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 이벤트 수정 (토글 및 생명주기)")
    class UpdateEvent {
        // Setup: Event_Normal(시간O, 스포츠X), Event_Sport(시간X, 스포츠O) 생성

        /** SCENE 14. 정상 수정 - 기본 정보 (제목, 색상) 변경 */
        // Given: UserA 토큰, Event ID (기존 title="원본", color="#FF5733")
        // Given: Request Body (title="수정된 제목", color="#000000")
        // When: PATCH /api/v1/events/{eventId}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.title == "수정된 제목" 확인
        // Then: **응답 Body 검증** - response.color == "#000000" 확인
        // Then: **응답 Body 검증** - response.id가 변경되지 않았는지 확인 (동일한 ID)
        // Then: **DB 검증** - eventRepository.findById(eventId)로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 title == "수정된 제목" 확인
        // Then: **DB 검증** - DB의 Event 엔티티의 color == "#000000" 확인

        /** SCENE 15. 로직 수정 - 시간 끄기 (hasTime: True -> False) */
        // Given: Event_Normal (기존 시간 있음, hasTime=true, time=14:30)
        // When: PATCH ... body { "hasTime": false }
        // Then: 200 OK
        // Then: 응답의 hasTime=false 확인
        // Then: 응답의 date 필드의 시간 부분이 00:00:00인지 확인
        // Then: DB에서 Event 엔티티의 hasTime=false, date의 시간 부분이 00:00:00으로 변경되었는지 확인

        /** SCENE 16. 로직 수정 - 시간 켜기 (hasTime: False -> True) */
        // Given: Event_Sport (기존 시간 없음, hasTime=false)
        // When: PATCH ... body { "hasTime": true, "time": "18:00" }
        // Then: 200 OK
        // Then: 응답의 hasTime=true 확인
        // Then: 응답의 date 필드의 시간 부분이 18:00:00인지 확인
        // Then: DB에서 Event 엔티티의 hasTime=true, date의 시간 부분이 18:00:00으로 변경되었는지 확인

        /** SCENE 17. 로직 수정 - 스포츠 타입 끄기 (Sport -> Normal) */
        // Given: Event_Sport (기존 SportRecord 있음, isSportType=true)
        // When: PATCH ... body { "isSportType": false }
        // Then: 200 OK
        // Then: 응답의 isSportType=false, sportInfo=null 확인
        // Then: DB에서 Event 엔티티의 isSportType=false로 변경되었는지 확인
        // Then: DB에서 SportRecord 엔티티가 삭제되었는지 확인 (orphanRemoval 또는 명시적 삭제)
        // Then: 상세 조회 API 재호출 시 sportInfo=null인지 확인

        /** SCENE 18. 로직 수정 - 스포츠 타입 켜기 (Normal -> Sport) */
        // Given: Event_Normal (isSportType=false, SportRecord 없음)
        // When: PATCH ... body { "isSportType": true, "sportInfo": {team1: "A", team2: "B", score1: 1, score2: 0} }
        // Then: 200 OK
        // Then: 응답의 isSportType=true, sportInfo 데이터 확인
        // Then: DB에서 Event 엔티티의 isSportType=true로 변경되었는지 확인
        // Then: DB에서 SportRecord 엔티티가 새로 생성되고 Event와 연결되었는지 확인
        // Then: DB에서 SportRecord의 team1, team2, score1, score2 값이 올바른지 확인

        /** SCENE 19. 로직 수정 - 해시태그 전체 교체 */
        // Given: Event (기존 태그 ["old1", "old2"] 연결됨)
        // When: PATCH ... body { "hashtags": ["new1"] }
        // Then: 200 OK
        // Then: 응답의 hashtags가 ["new1"]인지 확인
        // Then: DB에서 기존 EventHashtagMap(2개)이 삭제되었는지 확인
        // Then: DB에서 새로운 EventHashtagMap(1개)이 생성되었는지 확인
        // Then: DB에서 Hashtag 엔티티("old1", "old2")는 유지되는지 확인 (재사용 가능)
        // Then: 상세 조회 API 재호출 시 hashtags=["new1"]인지 확인

        /** SCENE 20. 로직 수정 - 해시태그 삭제 */
        // When: PATCH ... body { "hashtags": [] } (빈 리스트)
        // Then: 200 OK, 태그 목록 비어있음 확인

        /** SCENE 21. 예외 - 타인이 수정 시도 */
        // Given: UserC 토큰
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 4]. Delete Event (DELETE /api/v1/events/{eventId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 이벤트 삭제")
    class DeleteEvent {
        // Setup: Event 생성 (해시태그, 스포츠기록 포함)

        /** SCENE 22. 정상 삭제 - 연관 데이터 삭제 확인 */
        // Given: UserA 토큰, Event ID (해시태그 2개, 스포츠기록 포함)
        // When: DELETE /api/v1/events/{eventId}
        // Then: 204 No Content (응답 Body 없음)
        // Then: **재조회 검증** - GET /api/v1/events/{eventId} 호출 시 404 Not Found 확인
        // Then: **DB 검증** - eventRepository.findById(eventId).isPresent() == false 확인
        // Then: **DB 검증** - eventHashtagMapRepository.findAllByEventId(eventId)가 빈 리스트인지 확인
        // Then: **DB 검증** - sportRecordRepository.existsById(eventId) == false 확인 (스포츠 타입인 경우)
        // Then: **DB 검증** - hashtagRepository.findByName("tag1").isPresent() == true 확인 (재사용 가능)
        // Then: **DB 검증** - hashtagRepository.findByName("tag2").isPresent() == true 확인 (재사용 가능)

        /** SCENE 23. 예외 - 타인이 삭제 시도 */
        // Given: UserC 토큰
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 5]. Monthly List (GET /api/v1/events/monthly/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 월별 이벤트 조회")
    class MonthlyEvents {
        // Setup:
        // UserA Archive에 다음과 같이 이벤트 생성
        // - 4월 30일 (Boundary Pre)
        // - 5월 1일 (Boundary Start)
        // - 5월 15일 (Middle)
        // - 5월 31일 (Boundary End)
        // - 6월 1일 (Boundary Post)

        /** SCENE 24. 정상 조회 - 5월 데이터 조회 */
        // Setup: UserA Archive에 이벤트 생성
        //   - Event1: 4월 30일 23:59:59 (Boundary Pre)
        //   - Event2: 5월 1일 00:00:00 (Boundary Start)
        //   - Event3: 5월 15일 12:00:00 (Middle)
        //   - Event4: 5월 31일 23:59:59 (Boundary End)
        //   - Event5: 6월 1일 00:00:00 (Boundary Post)
        // Given: UserA 토큰, year=2024, month=5
        // When: GET /api/v1/events/monthly/{archiveId}?year=2024&month=5
        // Then: 200 OK
        // Then: 리스트 사이즈 3 (Event2, Event3, Event4) 확인
        // Then: Event1(4월 30일)은 포함되지 않아야 함 (경계값 검증)
        // Then: Event2(5월 1일)은 포함되어야 함 (경계값 검증)
        // Then: Event4(5월 31일)은 포함되어야 함 (경계값 검증)
        // Then: Event5(6월 1일)은 포함되지 않아야 함 (경계값 검증)
        // Then: **정렬 검증** - 리스트가 date 기준 오름차순으로 정렬되었는지 확인 (ORDER BY e.date ASC)
        // Then: **실제 데이터 순서 검증** - content.get(0).getDate() < content.get(1).getDate() 확인
        // Then: content.get(0).getId() == Event2.getId() 확인 (5월 1일)
        // Then: content.get(1).getId() == Event3.getId() 확인 (5월 15일)
        // Then: content.get(2).getId() == Event4.getId() 확인 (5월 31일)

        /** SCENE 25. 권한 필터링 - RESTRICTED 아카이브 */
        // Setup: UserA의 Restricted Archive
        // Given: UserB(친구) -> 200 OK (목록 반환)
        // Given: UserC(타인) -> 403 Forbidden (아카이브 접근 불가 시 목록 조회도 불가)

        /** SCENE 26. 빈 달 조회 */
        // Given: 데이터 없는 12월 조회
        // Then: 200 OK, 빈 리스트([]) 반환

        /** SCENE 27. 해시태그 Fetch Join 검증 (성능 이슈 체크용) */
        // Given: 5월 이벤트 3개 모두 해시태그 보유
        // When: GET /api/v1/events/monthly/{archiveId}?year=2024&month=5
        // Then: 200 OK
        // Then: 각 이벤트 객체 내에 hashtags 리스트가 null이 아니고 정상적으로 채워져 있는지 확인
        // Then: 모든 이벤트의 hashtags가 올바르게 포함되었는지 확인 (N+1 문제 없이 조회되는지 관점)
        // Then: (선택) 쿼리 로그 확인 또는 프로파일링으로 N+1 문제 없음을 검증
    }
}