package com.depth.deokive.domain.event.service;

/** EventService CRUD 테스트 */
class EventServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Archive Files : DummyImages -> 모든 Archive마다 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히 10개정도)
    // Events: 각 아카이브에는 이벤트가 각각 10개씩 존재한다.
    // Sport Record: 각 아카이브에는 SportRecord 타입이 5개, SportRecord가 아닌 타입이 5개인 이벤트가 존재한다.
    // hasTime: 각 아카이브에는 hasTime이 True/False인 이벤트가 각각 5개씩 있다.
    // HashTag: 각 이벤트에는 3~4개의 해시태그가 존재한다. 

    // [Category 1]. Create ----------------------------------------------------------
    /** SCENE 1. createEvent - 정상 케이스 (일반 이벤트, 해시태그 포함) */
    // Given: 유효한 UserPrincipal, ArchiveId, 제목, 내용, 날짜, 시간, 해시태그 목록
    // When: createEvent 호출
    // Then: Event 엔티티가 저장되었는지 확인
    // Then: EventHashtagMap이 생성되었는지 확인
    // Then: 기존 Hashtag는 재사용되고, 새로운 Hashtag는 생성되었는지 확인
    // Then: recordAt이 날짜와 시간으로 병합되었는지 확인

    /** SCENE 2. createEvent - 시간 없이 생성 (hasTime = false) */
    // Given: 유효한 UserPrincipal, ArchiveId, 날짜, hasTime = false
    // When: createEvent 호출
    // Then: Event 엔티티가 저장되었는지 확인
    // Then: recordAt의 시간이 MIDNIGHT인지 확인

    /** SCENE 3. createEvent - 스포츠 타입 이벤트 생성 */
    // Given: 유효한 UserPrincipal, ArchiveId, isSportType = true, 스포츠 정보
    // When: createEvent 호출
    // Then: Event 엔티티티가 저장되었는지 확인
    // Then: SportRecord가 생성되었는지 확인
    // Then: Event와 SportRecord가 연결되었는지 확인

    /** SCENE 4. createEvent - 해시태그 없이 생성 */
    // Given: 유효한 UserPrincipal, ArchiveId, 해시태그 = null 또는 빈 리스트
    // When: createEvent 호출
    // Then: Event 엔티티가 저장되었는지 확인
    // Then: EventHashtagMap이 생성되지 않았는지 확인

    /** SCENE 5. createEvent - 중복 해시태그 처리 */
    // Given: 유효한 UserPrincipal, ArchiveId, 중복된 해시태그 목록
    // When: createEvent 호출
    // Then: 중복이 제거되어 저장되었는지 확인

    /** SCENE 6. createEvent - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId
    // When: createEvent 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 7. createEvent - 타인 Archive에 생성 시도 */
    // Given: 타인이 소유한 ArchiveId
    // When: createEvent 호출
    // Then: 권한 예외 발생 확인

    // [Category 2]. Read ----------------------------------------------------------
    
    // === [ PUBLIC Archive + 일반 Event ] ===
    /** SCENE 8. getEvent - PUBLIC Archive + 일반 Event (본인 조회) */
    // Given: Archive_A_Public, 일반 Event (isSportType = false), UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인 (3~4개)
    // Then: SportRecord가 null인지 확인

    /** SCENE 9. getEvent - PUBLIC Archive + 일반 Event (타인 조회) */
    // Given: Archive_A_Public, 일반 Event, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인

    /** SCENE 10. getEvent - PUBLIC Archive + 일반 Event (친구 조회) */
    // Given: Archive_A_Public, 일반 Event, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인

    /** SCENE 11. getEvent - PUBLIC Archive + 일반 Event (비회원 조회) */
    // Given: Archive_A_Public, 일반 Event, userPrincipal = null
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인

    // === [ PUBLIC Archive + 스포츠 타입 Event ] ===
    /** SCENE 12. getEvent - PUBLIC Archive + 스포츠 타입 Event (본인 조회) */
    // Given: Archive_A_Public, 스포츠 타입 Event (isSportType = true), SportRecord, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인

    /** SCENE 13. getEvent - PUBLIC Archive + 스포츠 타입 Event (타인 조회) */
    // Given: Archive_A_Public, 스포츠 타입 Event, SportRecord, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    /** SCENE 14. getEvent - PUBLIC Archive + 스포츠 타입 Event (친구 조회) */
    // Given: Archive_A_Public, 스포츠 타입 Event, SportRecord, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    /** SCENE 15. getEvent - PUBLIC Archive + 스포츠 타입 Event (비회원 조회) */
    // Given: Archive_A_Public, 스포츠 타입 Event, SportRecord, userPrincipal = null
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    // === [ RESTRICTED Archive + 일반 Event ] ===
    /** SCENE 16. getEvent - RESTRICTED Archive + 일반 Event (본인 조회) */
    // Given: Archive_A_Restricted, 일반 Event, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인
    // Then: SportRecord가 null인지 확인

    /** SCENE 17. getEvent - RESTRICTED Archive + 일반 Event (친구 조회) */
    // Given: Archive_A_Restricted, 일반 Event, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인

    /** SCENE 18. getEvent - RESTRICTED Archive + 일반 Event (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 일반 Event, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 19. getEvent - RESTRICTED Archive + 일반 Event (비회원 조회) */
    // Given: Archive_A_Restricted, 일반 Event, userPrincipal = null
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ RESTRICTED Archive + 스포츠 타입 Event ] ===
    /** SCENE 20. getEvent - RESTRICTED Archive + 스포츠 타입 Event (본인 조회) */
    // Given: Archive_A_Restricted, 스포츠 타입 Event, SportRecord, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    /** SCENE 21. getEvent - RESTRICTED Archive + 스포츠 타입 Event (친구 조회) */
    // Given: Archive_A_Restricted, 스포츠 타입 Event, SportRecord, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    /** SCENE 22. getEvent - RESTRICTED Archive + 스포츠 타입 Event (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 스포츠 타입 Event, SportRecord, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 23. getEvent - RESTRICTED Archive + 스포츠 타입 Event (비회원 조회) */
    // Given: Archive_A_Restricted, 스포츠 타입 Event, SportRecord, userPrincipal = null
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive + 일반 Event ] ===
    /** SCENE 24. getEvent - PRIVATE Archive + 일반 Event (본인 조회) */
    // Given: Archive_A_Private, 일반 Event, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: 해시태그 목록이 정확히 반환되는지 확인
    // Then: SportRecord가 null인지 확인

    /** SCENE 25. getEvent - PRIVATE Archive + 일반 Event (타인 조회, No Friend) */
    // Given: Archive_A_Private, 일반 Event, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 26. getEvent - PRIVATE Archive + 일반 Event (친구 조회) */
    // Given: Archive_A_Private, 일반 Event, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 27. getEvent - PRIVATE Archive + 일반 Event (비회원 조회) */
    // Given: Archive_A_Private, 일반 Event, userPrincipal = null
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive + 스포츠 타입 Event ] ===
    /** SCENE 28. getEvent - PRIVATE Archive + 스포츠 타입 Event (본인 조회) */
    // Given: Archive_A_Private, 스포츠 타입 Event, SportRecord, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event 정보가 정확히 반환되는지 확인
    // Then: SportRecord 정보가 정확히 반환되는지 확인

    /** SCENE 29. getEvent - PRIVATE Archive + 스포츠 타입 Event (타인 조회, No Friend) */
    // Given: Archive_A_Private, 스포츠 타입 Event, SportRecord, UserC UserPrincipal (No Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 30. getEvent - PRIVATE Archive + 스포츠 타입 Event (친구 조회) */
    // Given: Archive_A_Private, 스포츠 타입 Event, SportRecord, UserB UserPrincipal (Friend)
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 31. getEvent - PRIVATE Archive + 스포츠 타입 Event (비회원 조회) */
    // Given: Archive_A_Private, 스포츠 타입 Event, SportRecord, userPrincipal = null
    // When: getEvent 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ hasTime 케이스 ] ===
    /** SCENE 32. getEvent - hasTime = true (시간 포함) */
    // Given: Archive_A_Public, hasTime = true Event, 시간 정보 포함, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event의 recordAt이 날짜와 시간으로 병합되었는지 확인
    // Then: 시간 정보가 정확히 반환되는지 확인

    /** SCENE 33. getEvent - hasTime = false (시간 없음) */
    // Given: Archive_A_Public, hasTime = false Event, 시간 = MIDNIGHT, UserA UserPrincipal
    // When: getEvent 호출
    // Then: Event의 recordAt 시간이 MIDNIGHT인지 확인

    // === [ Not Found Cases ] ===
    /** SCENE 34. getEvent - 존재하지 않는 Event */
    // Given: 존재하지 않는 eventId
    // When: getEvent 호출
    // Then: EVENT_NOT_FOUND 예외 발생 확인

    // [Category 3]. Update ----------------------------------------------------------
    /** SCENE 35. updateEvent - 정상 케이스 (제목, 내용, 날짜, 시간 수정) */
    // Given: 저장된 Event, 본인 UserPrincipal, 새로운 제목, 내용, 날짜, 시간
    // When: updateEvent 호출
    // Then: Event의 정보가 업데이트되었는지 확인
    // Then: recordAt이 새로운 날짜와 시간으로 병합되었는지 확인

    /** SCENE 36. updateEvent - 날짜만 수정 (시간 유지) */
    // Given: 저장된 Event, 본인 UserPrincipal, 새로운 날짜, 시간 = null
    // When: updateEvent 호출
    // Then: Event의 날짜가 업데이트되었는지 확인
    // Then: 기존 시간이 유지되었는지 확인

    /** SCENE 37. updateEvent - 해시태그 교체 */
    // Given: 저장된 Event, 기존 해시태그들, 본인 UserPrincipal, 새로운 해시태그 목록
    // When: updateEvent 호출
    // Then: 기존 EventHashtagMap이 삭제되었는지 확인
    // Then: 새로운 EventHashtagMap이 생성되었는지 확인
    // Then: 기존 해시태그가 재사용되는지 확인 (Hashtag 엔티티는 유지)

    /** SCENE 38. updateEvent - 해시태그 유지 (해시태그 = null) */
    // Given: 저장된 Event, 기존 해시태그들, 본인 UserPrincipal, 해시태그 = null
    // When: updateEvent 호출
    // Then: 기존 해시태그가 유지되었는지 확인
    // Then: EventHashtagMap이 삭제되지 않았는지 확인

    /** SCENE 39. updateEvent - 스포츠 타입 ON으로 변경 */
    // Given: 저장된 Event (isSportType = false), 본인 UserPrincipal, isSportType = true, 스포츠 정보
    // When: updateEvent 호출
    // Then: Event의 isSportType이 true인지 확인
    // Then: SportRecord가 생성되었는지 확인
    // Then: Event와 SportRecord가 연결되었는지 확인

    /** SCENE 40. updateEvent - 스포츠 타입 OFF로 변경 */
    // Given: 저장된 Event (isSportType = true), SportRecord, 본인 UserPrincipal, isSportType = false
    // When: updateEvent 호출
    // Then: Event의 isSportType이 false인지 확인
    // Then: SportRecord가 삭제되었는지 확인
    // Then: Event의 SportRecord 참조가 null인지 확인

    /** SCENE 41. updateEvent - 스포츠 정보 업데이트 */
    // Given: 저장된 Event, SportRecord, 본인 UserPrincipal, 새로운 스포츠 정보
    // When: updateEvent 호출
    // Then: SportRecord의 정보가 업데이트되었는지 확인
    // Then: 기존 SportRecord 엔티티가 재사용되었는지 확인 (새로 생성되지 않음)

    /** SCENE 42. updateEvent - hasTime 변경 (false -> true) */
    // Given: 저장된 Event (hasTime = false), 본인 UserPrincipal, hasTime = true, 시간 정보
    // When: updateEvent 호출
    // Then: Event의 hasTime이 true인지 확인
    // Then: recordAt이 새로운 시간으로 업데이트되었는지 확인

    /** SCENE 43. updateEvent - hasTime 변경 (true -> false) */
    // Given: 저장된 Event (hasTime = true), 본인 UserPrincipal, hasTime = false
    // When: updateEvent 호출
    // Then: Event의 hasTime이 false인지 확인
    // Then: recordAt의 시간이 MIDNIGHT로 변경되었는지 확인

    /** SCENE 44. updateEvent - 타인 수정 시도 */
    // Given: 저장된 Event, 타인 UserPrincipal
    // When: updateEvent 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 45. updateEvent - 존재하지 않는 Event */
    // Given: 존재하지 않는 eventId
    // When: updateEvent 호출
    // Then: EVENT_NOT_FOUND 예외 발생 확인

    // [Category 4]. Delete ----------------------------------------------------------
    /** SCENE 46. deleteEvent - 정상 케이스 (일반 이벤트) */
    // Given: 저장된 Event, EventHashtagMap, 본인 UserPrincipal
    // When: deleteEvent 호출
    // Then: EventHashtagMap이 삭제되었는지 확인
    // Then: Event가 삭제되었는지 확인
    // Then: Hashtag 엔티티는 유지되었는지 확인 (다른 Event에서 사용 가능)

    /** SCENE 47. deleteEvent - 정상 케이스 (스포츠 타입 이벤트) */
    // Given: 저장된 Event, SportRecord, EventHashtagMap, 본인 UserPrincipal
    // When: deleteEvent 호출
    // Then: EventHashtagMap이 삭제되었는지 확인
    // Then: SportRecord가 삭제되었는지 확인
    // Then: Event가 삭제되었는지 확인

    /** SCENE 48. deleteEvent - 타인 삭제 시도 */
    // Given: 저장된 Event, 타인 UserPrincipal
    // When: deleteEvent 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 49. deleteEvent - 존재하지 않는 Event */
    // Given: 존재하지 않는 eventId
    // When: deleteEvent 호출
    // Then: EVENT_NOT_FOUND 예외 발생 확인

    // [Category 5]. Read-Pagination (Monthly Events) ----------------------------------------------------------
    
    // === [ PUBLIC Archive ] ===
    /** SCENE 50. getMonthlyEvents - PUBLIC Archive (본인 조회) */
    // Given: Archive_A_Public, 해당 월의 여러 Event들 (일반 5개, 스포츠 5개), UserA UserPrincipal, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인 (10개)
    // Then: 해시태그가 N+1 없이 조회되었는지 확인 (bulk 조회)
    // Then: 각 Event의 해시태그가 정확히 포함되었는지 확인 (3~4개)
    // Then: 스포츠 타입 Event의 SportRecord가 포함되었는지 확인
    // Then: hasTime = true/false Event가 모두 포함되었는지 확인

    /** SCENE 51. getMonthlyEvents - PUBLIC Archive (타인 조회) */
    // Given: Archive_A_Public, 해당 월의 여러 Event들, UserC UserPrincipal (No Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인

    /** SCENE 52. getMonthlyEvents - PUBLIC Archive (친구 조회) */
    // Given: Archive_A_Public, 해당 월의 여러 Event들, UserB UserPrincipal (Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인

    /** SCENE 53. getMonthlyEvents - PUBLIC Archive (비회원 조회) */
    // Given: Archive_A_Public, 해당 월의 여러 Event들, userPrincipal = null, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 54. getMonthlyEvents - RESTRICTED Archive (본인 조회) */
    // Given: Archive_A_Restricted, 해당 월의 여러 Event들, UserA UserPrincipal, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인
    // Then: 스포츠 타입 Event의 SportRecord가 포함되었는지 확인

    /** SCENE 55. getMonthlyEvents - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted, 해당 월의 여러 Event들, UserB UserPrincipal (Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인

    /** SCENE 56. getMonthlyEvents - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 해당 월의 여러 Event들, UserC UserPrincipal (No Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 57. getMonthlyEvents - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted, 해당 월의 여러 Event들, userPrincipal = null, year, month
    // When: getMonthlyEvents 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive ] ===
    /** SCENE 58. getMonthlyEvents - PRIVATE Archive (본인 조회) */
    // Given: Archive_A_Private, 해당 월의 여러 Event들, UserA UserPrincipal, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월의 모든 Event가 반환되는지 확인
    // Then: 해시태그가 정확히 포함되었는지 확인
    // Then: 스포츠 타입 Event의 SportRecord가 포함되었는지 확인

    /** SCENE 59. getMonthlyEvents - PRIVATE Archive (타인 조회, No Friend) */
    // Given: Archive_A_Private, 해당 월의 여러 Event들, UserC UserPrincipal (No Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 60. getMonthlyEvents - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private, 해당 월의 여러 Event들, UserB UserPrincipal (Friend), year, month
    // When: getMonthlyEvents 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 61. getMonthlyEvents - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private, 해당 월의 여러 Event들, userPrincipal = null, year, month
    // When: getMonthlyEvents 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Edge Cases ] ===
    /** SCENE 62. getMonthlyEvents - 빈 결과 */
    // Given: Archive_A_Public, 해당 월에 Event 없음, year, month
    // When: getMonthlyEvents 호출
    // Then: 빈 리스트가 반환되는지 확인

    /** SCENE 63. getMonthlyEvents - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, year, month
    // When: getMonthlyEvents 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 64. getMonthlyEvents - 월 경계 처리 (월 말일 23:59) */
    // Given: Archive_A_Public, 월 말일 23:59의 Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 Event가 반환되는지 확인

    /** SCENE 65. getMonthlyEvents - 월 경계 처리 (월 초일 00:00) */
    // Given: Archive_A_Public, 월 초일 00:00의 Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 Event가 반환되는지 확인

    /** SCENE 66. getMonthlyEvents - 월 경계 처리 (다음 달 Event 제외) */
    // Given: Archive_A_Public, 해당 월 말일 Event, 다음 달 1일 Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월 Event만 반환되는지 확인
    // Then: 다음 달 Event는 반환되지 않는지 확인

    /** SCENE 67. getMonthlyEvents - 이전 달 Event 제외 */
    // Given: Archive_A_Public, 해당 월 1일 Event, 이전 달 말일 Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 해당 월 Event만 반환되는지 확인
    // Then: 이전 달 Event는 반환되지 않는지 확인

    /** SCENE 68. getMonthlyEvents - 스포츠 타입 이벤트 포함 */
    // Given: Archive_A_Public, 일반 Event와 스포츠 타입 Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 모든 Event가 반환되는지 확인
    // Then: 스포츠 타입 Event의 SportRecord가 포함되었는지 확인
    // Then: 일반 Event의 SportRecord가 null인지 확인

    /** SCENE 69. getMonthlyEvents - hasTime 케이스 포함 */
    // Given: Archive_A_Public, hasTime = true Event, hasTime = false Event, year, month
    // When: getMonthlyEvents 호출
    // Then: 모든 Event가 반환되는지 확인
    // Then: hasTime = true Event의 시간 정보가 정확한지 확인
    // Then: hasTime = false Event의 시간이 MIDNIGHT인지 확인
}

