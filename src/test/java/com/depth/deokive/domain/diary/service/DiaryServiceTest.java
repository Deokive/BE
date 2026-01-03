package com.depth.deokive.domain.diary.service;

/** DiaryService CRUD 테스트 */
class DiaryServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Arcvhie Files : DummyImages -> 모든 Archive마다 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히 10개정도)
    // Diaries: 각 아카이브에는 Diary Visibility가 PUBLIC, RESTRICTED, PRIVATE가 각각 10개씩 존재한다.
    // Diary Files : 각 다이어리에는 3개의 Dummy Image가 세팅되어야 함, 테스트를 위한 여분의 다이어리 더미 이미지들도 세팅 (넉넉히)

    // [Category 1]. Create ----------------------------------------------------------
    /** SCENE 1. createDiary - 정상 케이스 (파일 포함) */
    // Given: 유효한 UserPrincipal, ArchiveId, 제목, 내용, 공개범위, 파일 목록
    // When: createDiary 호출
    // Then: Diary 엔티티가 저장되었는지 확인
    // Then: DiaryFileMap이 생성되었는지 확인
    // Then: 파일 순서(sequence)가 올바르게 저장되었는지 확인
    // Then: 썸네일이 업데이트되었는지 확인 (PREVIEW 파일 우선, 없으면 첫 번째 파일)

    /** SCENE 2. createDiary - 파일 없이 생성 */
    // Given: 유효한 UserPrincipal, ArchiveId, 파일 목록 = null 또는 빈 리스트
    // When: createDiary 호출
    // Then: Diary 엔티티가 저장되었는지 확인
    // Then: DiaryFileMap이 생성되지 않았는지 확인
    // Then: 썸네일이 null인지 확인

    /** SCENE 3. createDiary - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId
    // When: createDiary 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 4. createDiary - 타인 Archive에 생성 시도 */
    // Given: 타인이 소유한 ArchiveId
    // When: createDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 5. createDiary - 다른 사용자의 파일 사용 시도 */
    // Given: 유효한 UserPrincipal, ArchiveId, 다른 사용자가 소유한 파일 ID
    // When: createDiary 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    /** SCENE 6. createDiary - 중복된 파일 ID */
    // Given: 유효한 UserPrincipal, ArchiveId, 중복된 파일 ID 목록
    // When: createDiary 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인

    /** SCENE 7. createDiary - MediaRole PREVIEW 우선 썸네일 */
    // Given: 유효한 UserPrincipal, ArchiveId, PREVIEW 파일과 일반 파일
    // When: createDiary 호출
    // Then: 썸네일이 PREVIEW 파일 기반인지 확인

    // [Category 2]. Read ----------------------------------------------------------
    
    // === [ PUBLIC Archive + PUBLIC Diary ] ===
    /** SCENE 8. retrieveDiary - PUBLIC Archive + PUBLIC Diary (본인 조회) */
    // Given: Archive_A_Public, PUBLIC Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: DiaryFileMap이 올바른 순서로 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 9. retrieveDiary - PUBLIC Archive + PUBLIC Diary (타인 조회) */
    // Given: Archive_A_Public, PUBLIC Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 10. retrieveDiary - PUBLIC Archive + PUBLIC Diary (친구 조회) */
    // Given: Archive_A_Public, PUBLIC Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 11. retrieveDiary - PUBLIC Archive + PUBLIC Diary (비회원 조회) */
    // Given: Archive_A_Public, PUBLIC Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    // === [ PUBLIC Archive + RESTRICTED Diary ] ===
    /** SCENE 12. retrieveDiary - PUBLIC Archive + RESTRICTED Diary (본인 조회) */
    // Given: Archive_A_Public, RESTRICTED Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 13. retrieveDiary - PUBLIC Archive + RESTRICTED Diary (친구 조회) */
    // Given: Archive_A_Public, RESTRICTED Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 14. retrieveDiary - PUBLIC Archive + RESTRICTED Diary (타인 조회, No Friend) */
    // Given: Archive_A_Public, RESTRICTED Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 15. retrieveDiary - PUBLIC Archive + RESTRICTED Diary (비회원 조회) */
    // Given: Archive_A_Public, RESTRICTED Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ PUBLIC Archive + PRIVATE Diary ] ===
    /** SCENE 16. retrieveDiary - PUBLIC Archive + PRIVATE Diary (본인 조회) */
    // Given: Archive_A_Public, PRIVATE Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 17. retrieveDiary - PUBLIC Archive + PRIVATE Diary (타인 조회, No Friend) */
    // Given: Archive_A_Public, PRIVATE Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 18. retrieveDiary - PUBLIC Archive + PRIVATE Diary (친구 조회) */
    // Given: Archive_A_Public, PRIVATE Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 19. retrieveDiary - PUBLIC Archive + PRIVATE Diary (비회원 조회) */
    // Given: Archive_A_Public, PRIVATE Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ RESTRICTED Archive + PUBLIC Diary ] ===
    /** SCENE 20. retrieveDiary - RESTRICTED Archive + PUBLIC Diary (본인 조회) */
    // Given: Archive_A_Restricted, PUBLIC Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 21. retrieveDiary - RESTRICTED Archive + PUBLIC Diary (친구 조회) */
    // Given: Archive_A_Restricted, PUBLIC Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 22. retrieveDiary - RESTRICTED Archive + PUBLIC Diary (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, PUBLIC Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 23. retrieveDiary - RESTRICTED Archive + PUBLIC Diary (비회원 조회) */
    // Given: Archive_A_Restricted, PUBLIC Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ RESTRICTED Archive + RESTRICTED Diary ] ===
    /** SCENE 24. retrieveDiary - RESTRICTED Archive + RESTRICTED Diary (본인 조회) */
    // Given: Archive_A_Restricted, RESTRICTED Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 25. retrieveDiary - RESTRICTED Archive + RESTRICTED Diary (친구 조회) */
    // Given: Archive_A_Restricted, RESTRICTED Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 26. retrieveDiary - RESTRICTED Archive + RESTRICTED Diary (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, RESTRICTED Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 27. retrieveDiary - RESTRICTED Archive + RESTRICTED Diary (비회원 조회) */
    // Given: Archive_A_Restricted, RESTRICTED Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ RESTRICTED Archive + PRIVATE Diary ] ===
    /** SCENE 28. retrieveDiary - RESTRICTED Archive + PRIVATE Diary (본인 조회) */
    // Given: Archive_A_Restricted, PRIVATE Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 29. retrieveDiary - RESTRICTED Archive + PRIVATE Diary (친구 조회) */
    // Given: Archive_A_Restricted, PRIVATE Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Diary 레벨 권한)

    /** SCENE 30. retrieveDiary - RESTRICTED Archive + PRIVATE Diary (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, PRIVATE Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 31. retrieveDiary - RESTRICTED Archive + PRIVATE Diary (비회원 조회) */
    // Given: Archive_A_Restricted, PRIVATE Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive + PUBLIC Diary ] ===
    /** SCENE 32. retrieveDiary - PRIVATE Archive + PUBLIC Diary (본인 조회) */
    // Given: Archive_A_Private, PUBLIC Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 33. retrieveDiary - PRIVATE Archive + PUBLIC Diary (타인 조회, No Friend) */
    // Given: Archive_A_Private, PUBLIC Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 34. retrieveDiary - PRIVATE Archive + PUBLIC Diary (친구 조회) */
    // Given: Archive_A_Private, PUBLIC Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 35. retrieveDiary - PRIVATE Archive + PUBLIC Diary (비회원 조회) */
    // Given: Archive_A_Private, PUBLIC Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive + RESTRICTED Diary ] ===
    /** SCENE 36. retrieveDiary - PRIVATE Archive + RESTRICTED Diary (본인 조회) */
    // Given: Archive_A_Private, RESTRICTED Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 37. retrieveDiary - PRIVATE Archive + RESTRICTED Diary (타인 조회, No Friend) */
    // Given: Archive_A_Private, RESTRICTED Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 38. retrieveDiary - PRIVATE Archive + RESTRICTED Diary (친구 조회) */
    // Given: Archive_A_Private, RESTRICTED Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 39. retrieveDiary - PRIVATE Archive + RESTRICTED Diary (비회원 조회) */
    // Given: Archive_A_Private, RESTRICTED Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive + PRIVATE Diary ] ===
    /** SCENE 40. retrieveDiary - PRIVATE Archive + PRIVATE Diary (본인 조회) */
    // Given: Archive_A_Private, PRIVATE Diary, UserA UserPrincipal
    // When: retrieveDiary 호출
    // Then: Diary 정보가 정확히 반환되는지 확인
    // Then: 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 41. retrieveDiary - PRIVATE Archive + PRIVATE Diary (타인 조회, No Friend) */
    // Given: Archive_A_Private, PRIVATE Diary, UserC UserPrincipal (No Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 42. retrieveDiary - PRIVATE Archive + PRIVATE Diary (친구 조회) */
    // Given: Archive_A_Private, PRIVATE Diary, UserB UserPrincipal (Friend)
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 43. retrieveDiary - PRIVATE Archive + PRIVATE Diary (비회원 조회) */
    // Given: Archive_A_Private, PRIVATE Diary, userPrincipal = null
    // When: retrieveDiary 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Not Found Cases ] ===
    /** SCENE 44. retrieveDiary - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId를 가진 Diary (실제로는 DiaryBook 조회 시 실패)
    // When: retrieveDiary 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 45. retrieveDiary - 존재하지 않는 Diary */
    // Given: 존재하지 않는 diaryId
    // When: retrieveDiary 호출
    // Then: DIARY_NOT_FOUND 예외 발생 확인

    // [Category 3]. Update ----------------------------------------------------------
    /** SCENE 46. updateDiary - 정상 케이스 (제목, 내용, 공개범위 수정) */
    // Given: 저장된 Diary, 본인 UserPrincipal, 새로운 제목, 내용, 공개범위
    // When: updateDiary 호출
    // Then: Diary의 제목이 업데이트되었는지 확인
    // Then: Diary의 내용이 업데이트되었는지 확인
    // Then: Diary의 공개범위가 업데이트되었는지 확인

    /** SCENE 47. updateDiary - 파일 전체 교체 */
    // Given: 저장된 Diary, 기존 파일들, 본인 UserPrincipal, 새로운 파일 목록
    // When: updateDiary 호출
    // Then: 기존 DiaryFileMap이 삭제되었는지 확인
    // Then: 새로운 DiaryFileMap이 생성되었는지 확인
    // Then: 썸네일이 업데이트되었는지 확인

    /** SCENE 48. updateDiary - 파일 삭제 (빈 리스트) */
    // Given: 저장된 Diary, 기존 파일들, 본인 UserPrincipal, 파일 목록 = []
    // When: updateDiary 호출
    // Then: 모든 DiaryFileMap이 삭제되었는지 확인
    // Then: 썸네일이 null인지 확인

    /** SCENE 49. updateDiary - 파일 유지 (파일 목록 = null) */
    // Given: 저장된 Diary, 기존 파일들, 본인 UserPrincipal, 파일 목록 = null
    // When: updateDiary 호출
    // Then: 기존 DiaryFileMap이 유지되었는지 확인

    /** SCENE 50. updateDiary - 타인 수정 시도 */
    // Given: 저장된 Diary, 타인 UserPrincipal
    // When: updateDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 51. updateDiary - 존재하지 않는 Diary */
    // Given: 존재하지 않는 diaryId
    // When: updateDiary 호출
    // Then: DIARY_NOT_FOUND 예외 발생 확인

    /** SCENE 52. updateDiary - 다른 사용자의 파일 사용 시도 */
    // Given: 저장된 Diary, 본인 UserPrincipal, 다른 사용자가 소유한 파일 ID
    // When: updateDiary 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    // [Category 4]. Delete ----------------------------------------------------------
    /** SCENE 53. deleteDiary - 정상 케이스 */
    // Given: 저장된 Diary, 관련된 DiaryFileMap, 본인 UserPrincipal
    // When: deleteDiary 호출
    // Then: DiaryFileMap이 삭제되었는지 확인
    // Then: Diary가 삭제되었는지 확인

    /** SCENE 54. deleteDiary - 타인 삭제 시도 */
    // Given: 저장된 Diary, 타인 UserPrincipal
    // When: deleteDiary 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 55. deleteDiary - 존재하지 않는 Diary */
    // Given: 존재하지 않는 diaryId
    // When: deleteDiary 호출
    // Then: DIARY_NOT_FOUND 예외 발생 확인

    // [Category 5]. Update Book Title ----------------------------------------------------------
    /** SCENE 56. updateDiaryBookTitle - 정상 케이스 */
    // Given: 저장된 DiaryBook, 본인 UserPrincipal, 새로운 제목
    // When: updateDiaryBookTitle 호출
    // Then: DiaryBook의 제목이 업데이트되었는지 확인
    // Then: Response가 정확히 반환되는지 확인

    /** SCENE 57. updateDiaryBookTitle - 타인 수정 시도 */
    // Given: 저장된 DiaryBook, 타인 UserPrincipal
    // When: updateDiaryBookTitle 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 58. updateDiaryBookTitle - 존재하지 않는 DiaryBook */
    // Given: 존재하지 않는 archiveId
    // When: updateDiaryBookTitle 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    // [Category 6]. Read-Pagination ----------------------------------------------------------
    /** SCENE 59. getDiaries - 정상 케이스 (본인 조회, 전체 공개범위) */
    // Given: 본인 ArchiveId, 여러 공개범위 Diary들, 본인 UserPrincipal, 페이지 정보
    // When: getDiaries 호출
    // Then: 모든 공개범위의 Diary가 반환되는지 확인
    // Then: 페이지 제목이 DiaryBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 60. getDiaries - 친구 조회 (PUBLIC, RESTRICTED) */
    // Given: 친구 관계, 친구의 ArchiveId, 여러 공개범위 Diary들
    // When: getDiaries 호출
    // Then: PUBLIC, RESTRICTED Diary만 반환되는지 확인
    // Then: PRIVATE Diary는 반환되지 않는지 확인

    /** SCENE 61. getDiaries - 비친구 조회 (PUBLIC만) */
    // Given: 친구 관계 아님, 타인의 ArchiveId, 여러 공개범위 Diary들
    // When: getDiaries 호출
    // Then: PUBLIC Diary만 반환되는지 확인
    // Then: RESTRICTED, PRIVATE Diary는 반환되지 않는지 확인

    /** SCENE 62. getDiaries - 비회원 조회 (PUBLIC만) */
    // Given: 타인의 ArchiveId, 여러 공개범위 Diary들, userPrincipal = null
    // When: getDiaries 호출
    // Then: PUBLIC Diary만 반환되는지 확인

    /** SCENE 63. getDiaries - Archive 권한 없음 */
    // Given: PRIVATE ArchiveId, 타인 UserPrincipal
    // When: getDiaries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 64. getDiaries - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId
    // When: getDiaries 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 65. getDiaries - 빈 결과 */
    // Given: ArchiveId, 해당 Archive에 Diary가 없음
    // When: getDiaries 호출
    // Then: 빈 페이지가 반환되는지 확인

    /** SCENE 66. getDiaries - 페이지 범위 초과 */
    // Given: 존재하지 않는 페이지 번호
    // When: getDiaries 호출
    // Then: 페이지 범위 예외 발생 확인
}

