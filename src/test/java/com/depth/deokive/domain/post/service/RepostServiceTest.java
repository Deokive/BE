package com.depth.deokive.domain.post.service;

/** RepostService CRUD 테스트 */
class RepostServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Archive Files : DummyImages -> 모든 Archive에 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히 10개정도)
    // Posts: 각 유저당 10개의 Posts를 가져야 한다. (다양한 카테고리 분배)
    // RepostTabs: 각 Archive의 RepostBook에는 3~5개의 RepostTab이 존재한다.
    // Reposts: 각 RepostTab에는 여러 Posts에 대한 Reposts가 존재한다. (각 Tab당 5~10개)
    // Repost 스냅샷: 각 Repost는 Post의 title과 thumbnailKey를 스냅샷으로 저장한다. 

    // [Category 1]. Create Repost ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 1. createRepost - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, UserA의 Post
    // When: createRepost 호출
    // Then: Repost 엔티티가 저장되었는지 확인
    // Then: Repost의 title이 Post의 제목과 일치하는지 확인 (스냅샷)
    // Then: Repost의 thumbnailKey가 Post의 썸네일과 일치하는지 확인 (스냅샷)
    // Then: Repost의 postId가 원본 Post의 ID와 일치하는지 확인

    /** SCENE 2. createRepost - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserA UserPrincipal, UserA의 Post
    // When: createRepost 호출
    // Then: Repost 엔티티가 저장되었는지 확인
    // Then: 스냅샷 데이터가 정확히 저장되었는지 확인

    /** SCENE 3. createRepost - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, UserA UserPrincipal, UserA의 Post
    // When: createRepost 호출
    // Then: Repost 엔티티가 저장되었는지 확인
    // Then: 스냅샷 데이터가 정확히 저장되었는지 확인

    /** SCENE 4. createRepost - 정상 케이스 (다른 사용자의 Post) */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, UserB의 Post
    // When: createRepost 호출
    // Then: Repost 엔티티가 저장되었는지 확인
    // Then: 스냅샷 데이터가 UserB의 Post 정보와 일치하는지 확인

    /** SCENE 5. createRepost - 정상 케이스 (썸네일 없는 Post) */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, 썸네일 없는 Post
    // When: createRepost 호출
    // Then: Repost 엔티티가 저장되었는지 확인
    // Then: Repost의 thumbnailKey가 null인지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 6. createRepost - 존재하지 않는 Tab */
    // Given: 존재하지 않는 tabId, UserA UserPrincipal, Post
    // When: createRepost 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 7. createRepost - 타인 Tab에 생성 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 RepostTab, UserC UserPrincipal (No Friend), Post
    // When: createRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 8. createRepost - 타인 Tab에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserC UserPrincipal (No Friend), Post
    // When: createRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 9. createRepost - 타인 Tab에 생성 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, UserC UserPrincipal (No Friend), Post
    // When: createRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 10. createRepost - 친구 Tab에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserB UserPrincipal (Friend), Post
    // When: createRepost 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 생성 불가)

    /** SCENE 11. createRepost - 존재하지 않는 Post */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, 존재하지 않는 postId
    // When: createRepost 호출
    // Then: POST_NOT_FOUND 예외 발생 확인

    /** SCENE 12. createRepost - 중복 생성 시도 (같은 Tab, 같은 Post) */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, 이미 저장된 postId
    // When: createRepost 호출
    // Then: REPOST_TAB_AND_POST_DUPLICATED 예외 발생 확인

    /** SCENE 13. createRepost - 같은 Post를 다른 Tab에 생성 (정상) */
    // Given: Archive_A_Public의 RepostTab1, RepostTab2, UserA UserPrincipal, 같은 Post
    // When: createRepost 호출 (각 Tab에)
    // Then: 두 Repost가 모두 저장되었는지 확인
    // Then: 각 Repost가 올바른 Tab에 연결되었는지 확인

    // [Category 2]. Update Repost ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 14. updateRepost - 정상 케이스 (제목 수정) */
    // Given: UserA의 Repost, UserA UserPrincipal, 새로운 제목
    // When: updateRepost 호출
    // Then: Repost의 제목이 업데이트되었는지 확인
    // Then: 썸네일은 변경되지 않았는지 확인 (스냅샷 유지)
    // Then: postId는 변경되지 않았는지 확인

    /** SCENE 15. updateRepost - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Repost, UserA UserPrincipal, 새로운 제목
    // When: updateRepost 호출
    // Then: Repost의 제목이 업데이트되었는지 확인
    // Then: 스냅샷 데이터가 유지되었는지 확인

    /** SCENE 16. updateRepost - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserA UserPrincipal, 새로운 제목
    // When: updateRepost 호출
    // Then: Repost의 제목이 업데이트되었는지 확인

    /** SCENE 17. updateRepost - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Repost, UserA UserPrincipal, 새로운 제목
    // When: updateRepost 호출
    // Then: Repost의 제목이 업데이트되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 18. updateRepost - 타인 수정 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Repost, UserC UserPrincipal (No Friend)
    // When: updateRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 19. updateRepost - 타인 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserC UserPrincipal (No Friend)
    // When: updateRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 20. updateRepost - 타인 수정 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Repost, UserC UserPrincipal (No Friend)
    // When: updateRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 21. updateRepost - 친구 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserB UserPrincipal (Friend)
    // When: updateRepost 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 수정 불가)

    /** SCENE 22. updateRepost - 존재하지 않는 Repost */
    // Given: 존재하지 않는 repostId, UserA UserPrincipal
    // When: updateRepost 호출
    // Then: REPOST_NOT_FOUND 예외 발생 확인

    // [Category 3]. Delete Repost ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 23. deleteRepost - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Repost, UserA UserPrincipal
    // When: deleteRepost 호출
    // Then: Repost가 삭제되었는지 확인
    // Then: 원본 Post는 유지되었는지 확인

    /** SCENE 24. deleteRepost - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserA UserPrincipal
    // When: deleteRepost 호출
    // Then: Repost가 삭제되었는지 확인

    /** SCENE 25. deleteRepost - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Repost, UserA UserPrincipal
    // When: deleteRepost 호출
    // Then: Repost가 삭제되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 26. deleteRepost - 타인 삭제 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 Repost, UserC UserPrincipal (No Friend)
    // When: deleteRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 27. deleteRepost - 타인 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserC UserPrincipal (No Friend)
    // When: deleteRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 28. deleteRepost - 타인 삭제 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 Repost, UserC UserPrincipal (No Friend)
    // When: deleteRepost 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 29. deleteRepost - 친구 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 Repost, UserB UserPrincipal (Friend)
    // When: deleteRepost 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 삭제 불가)

    /** SCENE 30. deleteRepost - 존재하지 않는 Repost */
    // Given: 존재하지 않는 repostId, UserA UserPrincipal
    // When: deleteRepost 호출
    // Then: REPOST_NOT_FOUND 예외 발생 확인

    // [Category 4]. Create RepostTab ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 31. createRepostTab - 정상 케이스 (PUBLIC Archive, 첫 번째 Tab) */
    // Given: Archive_A_Public, UserA UserPrincipal, Tab이 0개
    // When: createRepostTab 호출
    // Then: RepostTab 엔티티가 저장되었는지 확인
    // Then: RepostTab의 제목이 "1번째 탭"인지 확인
    // Then: Response가 정확히 반환되는지 확인

    /** SCENE 32. createRepostTab - 정상 케이스 (여러 Tab 생성) */
    // Given: Archive_A_Public, UserA UserPrincipal, 기존 Tab 3개
    // When: createRepostTab 호출
    // Then: RepostTab 엔티티가 저장되었는지 확인
    // Then: RepostTab의 제목이 "4번째 탭"인지 확인

    /** SCENE 33. createRepostTab - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserA UserPrincipal
    // When: createRepostTab 호출
    // Then: RepostTab 엔티티가 저장되었는지 확인

    /** SCENE 34. createRepostTab - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserA UserPrincipal
    // When: createRepostTab 호출
    // Then: RepostTab 엔티티가 저장되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 35. createRepostTab - 10개 제한 초과 */
    // Given: Archive_A_Public, UserA UserPrincipal, 이미 10개의 Tab 존재
    // When: createRepostTab 호출
    // Then: REPOST_TAB_LIMIT_EXCEED 예외 발생 확인

    /** SCENE 36. createRepostTab - 존재하지 않는 RepostBook */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal
    // When: createRepostTab 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 37. createRepostTab - 타인 Archive에 생성 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend)
    // When: createRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 38. createRepostTab - 타인 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend)
    // When: createRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 39. createRepostTab - 타인 Archive에 생성 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend)
    // When: createRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 40. createRepostTab - 친구 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend)
    // When: createRepostTab 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 생성 불가)

    // [Category 5]. Update RepostTab ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 41. updateRepostTab - 정상 케이스 (PUBLIC Archive) */
    // Given: Archive_A_Public의 RepostTab, UserA UserPrincipal, 새로운 제목
    // When: updateRepostTab 호출
    // Then: RepostTab의 제목이 업데이트되었는지 확인
    // Then: Response가 정확히 반환되는지 확인

    /** SCENE 42. updateRepostTab - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserA UserPrincipal, 새로운 제목
    // When: updateRepostTab 호출
    // Then: RepostTab의 제목이 업데이트되었는지 확인

    /** SCENE 43. updateRepostTab - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, UserA UserPrincipal, 새로운 제목
    // When: updateRepostTab 호출
    // Then: RepostTab의 제목이 업데이트되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 44. updateRepostTab - 타인 수정 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 RepostTab, UserC UserPrincipal (No Friend)
    // When: updateRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 45. updateRepostTab - 타인 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserC UserPrincipal (No Friend)
    // When: updateRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 46. updateRepostTab - 타인 수정 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, UserC UserPrincipal (No Friend)
    // When: updateRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 47. updateRepostTab - 친구 수정 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserB UserPrincipal (Friend)
    // When: updateRepostTab 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 수정 불가)

    /** SCENE 48. updateRepostTab - 존재하지 않는 RepostTab */
    // Given: 존재하지 않는 tabId, UserA UserPrincipal
    // When: updateRepostTab 호출
    // Then: REPOST_TAB_NOT_FOUND 예외 발생 확인

    // [Category 6]. Delete RepostTab ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 49. deleteRepostTab - 정상 케이스 (PUBLIC Archive, Repost 포함) */
    // Given: Archive_A_Public의 RepostTab, 관련된 Repost들 (5개), UserA UserPrincipal
    // When: deleteRepostTab 호출
    // Then: 모든 Repost가 삭제되었는지 확인 (Bulk 삭제)
    // Then: RepostTab이 삭제되었는지 확인
    // Then: 원본 Post는 유지되었는지 확인

    /** SCENE 50. deleteRepostTab - 정상 케이스 (Repost 없음) */
    // Given: Archive_A_Public의 RepostTab, Repost 없음, UserA UserPrincipal
    // When: deleteRepostTab 호출
    // Then: RepostTab이 삭제되었는지 확인

    /** SCENE 51. deleteRepostTab - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, 관련된 Repost들, UserA UserPrincipal
    // When: deleteRepostTab 호출
    // Then: 모든 Repost가 삭제되었는지 확인
    // Then: RepostTab이 삭제되었는지 확인

    /** SCENE 52. deleteRepostTab - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, 관련된 Repost들, UserA UserPrincipal
    // When: deleteRepostTab 호출
    // Then: 모든 Repost가 삭제되었는지 확인
    // Then: RepostTab이 삭제되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 53. deleteRepostTab - 타인 삭제 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public의 RepostTab, UserC UserPrincipal (No Friend)
    // When: deleteRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 54. deleteRepostTab - 타인 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserC UserPrincipal (No Friend)
    // When: deleteRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 55. deleteRepostTab - 타인 삭제 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private의 RepostTab, UserC UserPrincipal (No Friend)
    // When: deleteRepostTab 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 56. deleteRepostTab - 친구 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted의 RepostTab, UserB UserPrincipal (Friend)
    // When: deleteRepostTab 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 삭제 불가)

    /** SCENE 57. deleteRepostTab - 존재하지 않는 RepostTab */
    // Given: 존재하지 않는 tabId, UserA UserPrincipal
    // When: deleteRepostTab 호출
    // Then: REPOST_TAB_NOT_FOUND 예외 발생 확인

    // [Category 7]. Read-Pagination ----------------------------------------------------------
    
    // === [ PUBLIC Archive ] ===
    /** SCENE 58. getReposts - PUBLIC Archive (본인 조회, tabId 지정) */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, UserA UserPrincipal, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인
    // Then: 페이지 제목이 RepostBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인
    // Then: 각 Repost의 스냅샷 데이터가 정확히 반환되는지 확인

    /** SCENE 59. getReposts - PUBLIC Archive (타인 조회, tabId 지정) */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, UserC UserPrincipal (No Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 60. getReposts - PUBLIC Archive (친구 조회, tabId 지정) */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, UserB UserPrincipal (Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 61. getReposts - PUBLIC Archive (비회원 조회, tabId 지정) */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, userPrincipal = null, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 62. getReposts - PUBLIC Archive (tabId = null, 첫 번째 Tab) */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, UserA UserPrincipal, tabId = null
    // When: getReposts 호출
    // Then: 첫 번째 Tab의 Repost가 반환되는지 확인
    // Then: Response의 targetTabId가 첫 번째 Tab ID인지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 63. getReposts - RESTRICTED Archive (본인 조회) */
    // Given: Archive_A_Restricted, 여러 RepostTab들, Repost들, UserA UserPrincipal, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 64. getReposts - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted, 여러 RepostTab들, Repost들, UserB UserPrincipal (Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 65. getReposts - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 여러 RepostTab들, Repost들, UserC UserPrincipal (No Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 66. getReposts - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted, 여러 RepostTab들, Repost들, userPrincipal = null, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive ] ===
    /** SCENE 67. getReposts - PRIVATE Archive (본인 조회) */
    // Given: Archive_A_Private, 여러 RepostTab들, Repost들, UserA UserPrincipal, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 해당 Tab의 Repost만 반환되는지 확인
    // Then: 모든 Tab 목록이 반환되는지 확인

    /** SCENE 68. getReposts - PRIVATE Archive (타인 조회, No Friend) */
    // Given: Archive_A_Private, 여러 RepostTab들, Repost들, UserC UserPrincipal (No Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 69. getReposts - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private, 여러 RepostTab들, Repost들, UserB UserPrincipal (Friend), tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 70. getReposts - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private, 여러 RepostTab들, Repost들, userPrincipal = null, tabId, 페이지 정보
    // When: getReposts 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Edge Cases ] ===
    /** SCENE 71. getReposts - Tab이 없는 경우 */
    // Given: Archive_A_Public, Tab이 없음
    // When: getReposts 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: Tab 목록이 빈 리스트인지 확인
    // Then: targetTabId가 null인지 확인

    /** SCENE 72. getReposts - 존재하지 않는 tabId */
    // Given: Archive_A_Public, 다른 Tab들, 존재하지 않는 tabId, UserA UserPrincipal
    // When: getReposts 호출
    // Then: REPOST_TAB_NOT_FOUND 예외 발생 확인

    /** SCENE 73. getReposts - 존재하지 않는 RepostBook */
    // Given: 존재하지 않는 archiveId, tabId, UserA UserPrincipal
    // When: getReposts 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 74. getReposts - 빈 결과 (Tab에 Repost 없음) */
    // Given: Archive_A_Public, RepostTab, 해당 Tab에 Repost 없음, UserA UserPrincipal
    // When: getReposts 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: Tab 목록은 반환되는지 확인

    /** SCENE 75. getReposts - 페이지 범위 초과 */
    // Given: Archive_A_Public, 여러 RepostTab들, Repost들, 존재하지 않는 페이지 번호
    // When: getReposts 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 76. getReposts - 여러 Tab 간 Repost 분리 확인 */
    // Given: Archive_A_Public, RepostTab1 (Repost 5개), RepostTab2 (Repost 3개), UserA UserPrincipal
    // When: getReposts 호출 (각 Tab별로)
    // Then: Tab1 조회 시 Tab1의 Repost만 반환되는지 확인
    // Then: Tab2 조회 시 Tab2의 Repost만 반환되는지 확인
    // Then: 각 Tab의 Repost가 섞이지 않았는지 확인
}

