package com.depth.deokive.domain.archive.service;

/** ArchiveService CRUD 테스트 */
class ArchiveServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Files : DummyImages -> 모든 Archive마다 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히)

    // [Category 1]: Create ----------------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 1. createArchive - 정상 케이스 (PUBLIC, 배너 포함) */
    // Given: UserA UserPrincipal, 제목, Visibility = PUBLIC, 배너 이미지 ID
    // When: createArchive 호출
    // Then: Archive 엔티티가 저장되었는지 확인
    // Then: Archive의 기본 뱃지가 NEWBIE인지 확인
    // Then: Archive의 Visibility가 PUBLIC인지 확인
    // Then: 배너 파일이 연결되었는지 확인
    // Then: Sub Domain Books(DiaryBook, GalleryBook, TicketBook, RepostBook)가 생성되었는지 확인
    // Then: 각 Book의 제목이 Archive 제목 기반인지 확인
    // Then: Response의 isOwner가 true인지 확인
    // Then: Response의 viewCount, likeCount가 0인지 확인
    // Then: Response의 bannerUrl이 올바른 CDN URL인지 확인
    // Then: 실제 DB구조에서도 제대로 데이터가 반영되었는지 확인 (연관된 File, Books까지 전부)

    /** SCENE 2. createArchive - 정상 케이스 (RESTRICTED, 배너 포함) */
    // Given: UserA UserPrincipal, 제목, Visibility = RESTRICTED, 배너 이미지 ID
    // When: createArchive 호출
    // Then: Archive 엔티티가 저장되었는지 확인
    // Then: Archive의 Visibility가 RESTRICTED인지 확인
    // Then: 배너 파일이 연결되었는지 확인
    // Then: Sub Domain Books가 생성되었는지 확인

    /** SCENE 3. createArchive - 정상 케이스 (PRIVATE, 배너 포함) */
    // Given: UserA UserPrincipal, 제목, Visibility = PRIVATE, 배너 이미지 ID
    // When: createArchive 호출
    // Then: Archive 엔티티가 저장되었는지 확인
    // Then: Archive의 Visibility가 PRIVATE인지 확인
    // Then: 배너 파일이 연결되었는지 확인
    // Then: Sub Domain Books가 생성되었는지 확인

    /** SCENE 4. createArchive - 배너 이미지 없이 생성 (PUBLIC) */
    // Given: UserA UserPrincipal, 제목, Visibility = PUBLIC, 배너 이미지 ID = null
    // When: createArchive 호출
    // Then: Archive가 정상 생성되었는지 확인
    // Then: 배너 파일이 null인지 확인
    // Then: Response의 bannerUrl이 null인지 확인
    // Then: Sub Domain Books가 생성되었는지 확인
    // Then: 실제 DB구조에서도 제대로 데이터가 반영되었는지 확인

    /** SCENE 5. createArchive - 배너 이미지 없이 생성 (RESTRICTED) */
    // Given: UserA UserPrincipal, 제목, Visibility = RESTRICTED, 배너 이미지 ID = null
    // When: createArchive 호출
    // Then: Archive가 정상 생성되었는지 확인
    // Then: 배너 파일이 null인지 확인
    // Then: Sub Domain Books가 생성되었는지 확인

    /** SCENE 6. createArchive - 배너 이미지 없이 생성 (PRIVATE) */
    // Given: UserA UserPrincipal, 제목, Visibility = PRIVATE, 배너 이미지 ID = null
    // When: createArchive 호출
    // Then: Archive가 정상 생성되었는지 확인
    // Then: 배너 파일이 null인지 확인
    // Then: Sub Domain Books가 생성되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 7. createArchive - 존재하지 않는 사용자 */
    // Given: 존재하지 않는 userId를 가진 UserPrincipal, 제목, Visibility, 배너 이미지 ID
    // When: createArchive 호출
    // Then: USER_NOT_FOUND 예외 발생 확인

    /** SCENE 8. createArchive - 다른 사용자의 파일을 배너로 사용 시도 (IDOR 테스트) */
    // Given: UserA UserPrincipal, 제목, Visibility, UserC가 소유한 파일 ID
    // When: createArchive 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    /** SCENE 9. createArchive - 존재하지 않는 파일 ID */
    // Given: UserA UserPrincipal, 제목, Visibility, 존재하지 않는 파일 ID
    // When: createArchive 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인


    // [Category 2]: Read ----------------------------------------------------------------
    
    // === [ 본인 조회 ] ===
    /** SCENE 10. getArchiveDetail - 본인 조회 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserA UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 isOwner가 true인지 확인
    // Then: Response의 isLiked가 false인지 확인 (좋아요 안한 경우)
    // Then: Response의 bannerUrl이 올바른 CDN URL인지 확인
    // Then: Response의 viewCount, likeCount가 정확한지 확인

    /** SCENE 11. getArchiveDetail - 본인 조회 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserA UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 isOwner가 true인지 확인

    /** SCENE 12. getArchiveDetail - 본인 조회 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserA UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 isOwner가 true인지 확인

    /** SCENE 13. getArchiveDetail - 본인 조회 (배너 없음) */
    // Given: Archive_A_Public (배너 없음), UserA UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: Response의 bannerUrl이 null인지 확인

    // === [ PUBLIC Archive ] ===
    /** SCENE 14. getArchiveDetail - PUBLIC Archive (타인 조회) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend)
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: Response의 isOwner가 false인지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 bannerUrl이 올바른 CDN URL인지 확인

    /** SCENE 15. getArchiveDetail - PUBLIC Archive (친구 조회) */
    // Given: Archive_A_Public, UserB UserPrincipal (Friend)
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: Response의 isOwner가 false인지 확인
    // Then: 조회수가 증가했는지 확인

    /** SCENE 16. getArchiveDetail - PUBLIC Archive (비회원 조회) */
    // Given: Archive_A_Public, userPrincipal = null
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: Response의 isOwner가 false인지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 bannerUrl이 올바른 CDN URL인지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 17. getArchiveDetail - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend)
    // When: getArchiveDetail 호출
    // Then: Archive 정보가 정확히 반환되는지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: Response의 isOwner가 false인지 확인
    // Then: Response의 isLiked가 false인지 확인 (좋아요 안한 경우)
    // Then: Response의 bannerUrl이 올바른 CDN URL인지 확인

    /** SCENE 18. getArchiveDetail - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend)
    // When: getArchiveDetail 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 19. getArchiveDetail - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted, userPrincipal = null
    // When: getArchiveDetail 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ PRIVATE Archive ] ===
    /** SCENE 20. getArchiveDetail - PRIVATE Archive (타인 조회) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend)
    // When: getArchiveDetail 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 21. getArchiveDetail - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private, UserB UserPrincipal (Friend)
    // When: getArchiveDetail 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 22. getArchiveDetail - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private, userPrincipal = null
    // When: getArchiveDetail 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ Edge Cases ] ===
    /** SCENE 23. getArchiveDetail - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal
    // When: getArchiveDetail 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 24. getArchiveDetail - 좋아요 상태 확인 (좋아요 있음) */
    // Given: Archive_A_Public, 좋아요를 누른 UserB UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Response의 isLiked가 true인지 확인
    // Then: Response의 likeCount가 정확한지 확인

    /** SCENE 25. getArchiveDetail - 좋아요 상태 확인 (좋아요 없음) */
    // Given: Archive_A_Public, 좋아요를 누르지 않은 UserC UserPrincipal
    // When: getArchiveDetail 호출
    // Then: Response의 isLiked가 false인지 확인
    // Then: Response의 likeCount가 정확한지 확인

    /** SCENE 26. getArchiveDetail - 조회수 증가 확인 (동시성 고려) */
    // Given: Archive_A_Public (초기 viewCount = 0), 여러 번 조회
    // When: getArchiveDetail 호출 (3번)
    // Then: 조회수가 3으로 증가했는지 확인

    // [Category 3]: Update ----------------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 27. updateArchive - 정상 케이스 (제목, 공개범위 수정) */
    // Given: Archive_A_Public, UserA UserPrincipal, 새로운 제목과 공개범위
    // When: updateArchive 호출
    // Then: Archive의 제목이 업데이트되었는지 확인
    // Then: Archive의 공개범위가 업데이트되었는지 확인
    // Then: Response가 정확히 반환되는지 확인
    // Then: 기존 배너 파일이 유지되었는지 확인

    /** SCENE 28. updateArchive - 제목만 수정 */
    // Given: Archive_A_Public, UserA UserPrincipal, 새로운 제목, 공개범위 = null
    // When: updateArchive 호출
    // Then: Archive의 제목만 업데이트되었는지 확인
    // Then: 기존 공개범위가 유지되었는지 확인

    /** SCENE 29. updateArchive - 공개범위만 수정 */
    // Given: Archive_A_Public, UserA UserPrincipal, 제목 = null, 새로운 공개범위
    // When: updateArchive 호출
    // Then: Archive의 공개범위만 업데이트되었는지 확인
    // Then: 기존 제목이 유지되었는지 확인

    /** SCENE 30. updateArchive - 공개범위 변경 (PUBLIC -> RESTRICTED) */
    // Given: Archive_A_Public, UserA UserPrincipal, 공개범위 = RESTRICTED
    // When: updateArchive 호출
    // Then: Archive의 공개범위가 RESTRICTED로 변경되었는지 확인

    /** SCENE 31. updateArchive - 공개범위 변경 (RESTRICTED -> PRIVATE) */
    // Given: Archive_A_Restricted, UserA UserPrincipal, 공개범위 = PRIVATE
    // When: updateArchive 호출
    // Then: Archive의 공개범위가 PRIVATE로 변경되었는지 확인

    /** SCENE 32. updateArchive - 공개범위 변경 (PRIVATE -> PUBLIC) */
    // Given: Archive_A_Private, UserA UserPrincipal, 공개범위 = PUBLIC
    // When: updateArchive 호출
    // Then: Archive의 공개범위가 PUBLIC로 변경되었는지 확인

    /** SCENE 33. updateArchive - 배너 이미지 변경 (기존 배너 있음) */
    // Given: Archive_A_Public (배너 있음), UserA UserPrincipal, 새로운 배너 이미지 ID
    // When: updateArchive 호출
    // Then: Archive의 배너 파일이 변경되었는지 확인
    // Then: Response의 bannerUrl이 새로운 파일의 URL인지 확인
    // Then: 기존 배너 파일 연결이 해제되었는지 확인

    /** SCENE 34. updateArchive - 배너 이미지 추가 (기존 배너 없음) */
    // Given: Archive_A_Public (배너 없음), UserA UserPrincipal, 새로운 배너 이미지 ID
    // When: updateArchive 호출
    // Then: Archive의 배너 파일이 연결되었는지 확인
    // Then: Response의 bannerUrl이 새로운 파일의 URL인지 확인

    /** SCENE 35. updateArchive - 배너 이미지 삭제 (fileId = -1) */
    // Given: Archive_A_Public (배너 있음), UserA UserPrincipal, fileId = -1
    // When: updateArchive 호출
    // Then: Archive의 배너 파일이 null인지 확인
    // Then: Response의 bannerUrl이 null인지 확인
    // Then: 기존 배너 파일 연결이 해제되었는지 확인

    /** SCENE 36. updateArchive - 배너 이미지 유지 (fileId = null) */
    // Given: Archive_A_Public (배너 있음), UserA UserPrincipal, fileId = null
    // When: updateArchive 호출
    // Then: Archive의 배너 파일이 기존과 동일한지 확인
    // Then: Response의 bannerUrl이 기존과 동일한지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 37. updateArchive - 타인 수정 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend)
    // When: updateArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 38. updateArchive - 타인 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend)
    // When: updateArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 39. updateArchive - 타인 수정 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend)
    // When: updateArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 40. updateArchive - 친구 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend)
    // When: updateArchive 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 수정 불가)

    /** SCENE 41. updateArchive - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal
    // When: updateArchive 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 42. updateArchive - 다른 사용자의 파일을 배너로 사용 시도 (IDOR) */
    // Given: Archive_A_Public, UserA UserPrincipal, UserC가 소유한 파일 ID
    // When: updateArchive 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    /** SCENE 43. updateArchive - 존재하지 않는 파일 ID */
    // Given: Archive_A_Public, UserA UserPrincipal, 존재하지 않는 파일 ID
    // When: updateArchive 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인

    // [Category 4]: Delete ----------------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 44. deleteArchive - 정상 케이스 (PUBLIC Archive, 모든 하위 데이터 포함) */
    // Given: Archive_A_Public, 관련된 Event, Diary, Ticket, Gallery, Repost 데이터, UserA UserPrincipal
    // When: deleteArchive 호출
    // Then: Archive가 삭제되었는지 확인
    // Then: EventHashtagMap가 삭제되었는지 확인
    // Then: SportRecord가 삭제되었는지 확인
    // Then: Event가 삭제되었는지 확인
    // Then: DiaryFileMap가 삭제되었는지 확인
    // Then: Diary가 삭제되었는지 확인
    // Then: Ticket가 삭제되었는지 확인
    // Then: Gallery가 삭제되었는지 확인
    // Then: Repost가 삭제되었는지 확인
    // Then: RepostTab가 삭제되었는지 확인
    // Then: Sticker가 삭제되었는지 확인
    // Then: ArchiveLike가 삭제되었는지 확인
    // Then: Sub Domain Books가 삭제되었는지 확인 (Cascade)
    // Then: 배너 File 연결이 해제되었는지 확인 (File 엔티티는 유지)

    /** SCENE 45. deleteArchive - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, 관련된 하위 데이터, UserA UserPrincipal
    // When: deleteArchive 호출
    // Then: Archive가 삭제되었는지 확인
    // Then: 모든 하위 데이터가 삭제되었는지 확인

    /** SCENE 46. deleteArchive - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private, 관련된 하위 데이터, UserA UserPrincipal
    // When: deleteArchive 호출
    // Then: Archive가 삭제되었는지 확인
    // Then: 모든 하위 데이터가 삭제되었는지 확인

    /** SCENE 47. deleteArchive - 정상 케이스 (하위 데이터 없음) */
    // Given: Archive_A_Public, 하위 데이터 없음, UserA UserPrincipal
    // When: deleteArchive 호출
    // Then: Archive가 삭제되었는지 확인
    // Then: Sub Domain Books가 삭제되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 48. deleteArchive - 타인 삭제 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend)
    // When: deleteArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 49. deleteArchive - 타인 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend)
    // When: deleteArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 50. deleteArchive - 타인 삭제 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend)
    // When: deleteArchive 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 51. deleteArchive - 친구 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend)
    // When: deleteArchive 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 삭제 불가)

    /** SCENE 52. deleteArchive - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal
    // When: deleteArchive 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    // [Category 5]: Read-Pagination ----------------------------------------------------------------
    
    // === [ getGlobalFeed ] ===
    /** SCENE 53. getGlobalFeed - 정상 케이스 (PUBLIC만 조회, 기본 정렬) */
    // Given: 여러 PUBLIC Archive들, RESTRICTED/PRIVATE Archive들, sort = null, 페이지 정보
    // When: getGlobalFeed 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: RESTRICTED, PRIVATE Archive는 반환되지 않는지 확인
    // Then: 페이지 제목이 "최신 아카이브 피드"인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인
    // Then: 정렬이 최신순(기본)인지 확인

    /** SCENE 54. getGlobalFeed - 정상 케이스 (hotScore 정렬) */
    // Given: 여러 PUBLIC Archive들 (다양한 hotScore), sort = "hotScore", 페이지 정보
    // When: getGlobalFeed 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: hotScore 기준으로 정렬되었는지 확인 (높은 순서)
    // Then: 페이지 제목이 "지금 핫한 피드"인지 확인

    /** SCENE 55. getGlobalFeed - 정상 케이스 (모든 사용자의 PUBLIC Archive 포함) */
    // Given: UserA, UserB, UserC의 PUBLIC Archive들, 페이지 정보
    // When: getGlobalFeed 호출
    // Then: 모든 사용자의 PUBLIC Archive가 반환되는지 확인
    // Then: 특정 사용자로 필터링되지 않았는지 확인

    /** SCENE 56. getGlobalFeed - 빈 결과 */
    // Given: PUBLIC Archive가 없음, RESTRICTED/PRIVATE만 존재
    // When: getGlobalFeed 호출
    // Then: 빈 페이지가 반환되는지 확인

    /** SCENE 57. getGlobalFeed - 페이지 범위 초과 */
    // Given: 여러 PUBLIC Archive들, 존재하지 않는 페이지 번호
    // When: getGlobalFeed 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 58. getGlobalFeed - 페이지네이션 정렬 확인 */
    // Given: 여러 PUBLIC Archive들, 페이지 정보
    // When: getGlobalFeed 호출
    // Then: Archive 목록이 올바른 순서로 반환되는지 확인
    // Then: 페이지 크기가 정확한지 확인

    // === [ getUserArchives ] ===
    /** SCENE 59. getUserArchives - 본인 조회 (전체 공개범위) */
    // Given: UserA의 Archive들 (PUBLIC, RESTRICTED, PRIVATE), UserA UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: 모든 공개범위의 Archive가 반환되는지 확인
    // Then: 페이지 제목이 "마이 아카이브"인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 60. getUserArchives - 본인 조회 (다양한 정렬) */
    // Given: UserA의 Archive들, 각 정렬 조건, UserA UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: 모든 공개범위의 Archive가 반환되는지 확인
    // Then: 정렬이 올바르게 적용되는지 확인

    /** SCENE 61. getUserArchives - 친구 조회 (PUBLIC, RESTRICTED) */
    // Given: 친구 관계, UserA의 Archive들 (PUBLIC, RESTRICTED, PRIVATE), UserB UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC, RESTRICTED Archive만 반환되는지 확인
    // Then: PRIVATE Archive는 반환되지 않는지 확인
    // Then: 페이지 제목이 UserA의 닉네임을 포함하는지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 62. getUserArchives - 친구 조회 (다양한 정렬) */
    // Given: 친구 관계, UserA의 Archive들, 각 정렬 조건, UserB UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC, RESTRICTED Archive만 반환되는지 확인
    // Then: 정렬이 올바르게 적용되는지 확인

    /** SCENE 63. getUserArchives - 비친구 조회 (PUBLIC만) */
    // Given: 친구 관계 아님, UserA의 Archive들 (PUBLIC, RESTRICTED, PRIVATE), UserC UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: RESTRICTED, PRIVATE Archive는 반환되지 않는지 확인
    // Then: 페이지 제목이 UserA의 닉네임을 포함하는지 확인

    /** SCENE 64. getUserArchives - 비친구 조회 (다양한 정렬) */
    // Given: 친구 관계 아님, UserA의 Archive들, 각 정렬 조건, UserC UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: 정렬이 올바르게 적용되는지 확인

    /** SCENE 65. getUserArchives - 비회원 조회 (PUBLIC만) */
    // Given: UserA의 Archive들 (PUBLIC, RESTRICTED, PRIVATE), userPrincipal = null, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: RESTRICTED, PRIVATE Archive는 반환되지 않는지 확인
    // Then: 페이지 제목이 UserA의 닉네임을 포함하는지 확인

    /** SCENE 66. getUserArchives - 비회원 조회 (다양한 정렬) */
    // Given: UserA의 Archive들, 각 정렬 조건, userPrincipal = null, 페이지 정보
    // When: getUserArchives 호출
    // Then: PUBLIC Archive만 반환되는지 확인
    // Then: 정렬이 올바르게 적용되는지 확인

    // === [ Edge Cases ] ===
    /** SCENE 67. getUserArchives - 존재하지 않는 사용자 */
    // Given: 존재하지 않는 targetUserId, UserA UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: 빈 결과가 반환되는지 확인 또는 예외 발생 확인

    /** SCENE 68. getUserArchives - 빈 결과 (PUBLIC Archive 없음) */
    // Given: UserA의 Archive들 (RESTRICTED, PRIVATE만), UserC UserPrincipal, 페이지 정보
    // When: getUserArchives 호출
    // Then: 빈 페이지가 반환되는지 확인

    /** SCENE 69. getUserArchives - 페이지 범위 초과 */
    // Given: UserA의 Archive들, 존재하지 않는 페이지 번호
    // When: getUserArchives 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 70. getUserArchives - 페이지네이션 정렬 확인 */
    // Given: UserA의 Archive들, 페이지 정보
    // When: getUserArchives 호출
    // Then: Archive 목록이 올바른 순서로 반환되는지 확인
    // Then: 페이지 크기가 정확한지 확인
}