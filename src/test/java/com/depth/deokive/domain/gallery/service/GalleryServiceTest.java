package com.depth.deokive.domain.gallery.service;

/** GalleryService CRUD 테스트 */
class GalleryServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, AnonymousUser(not login)
    // Friend: UserA, UserB
    // Archives ::
    // - UserA : Archive_A_Public, Archive_A_Restricted, Archive_A_Private
    // - UserB : Archive_B_Public, Archive_B_Restricted, Archive_B_Private
    // - UserC : Archive_C_Public, Archive_C_Restricted, Archive_C_Private
    // Archive Files : DummyImages -> 모든 Archive마다 존재하도록 세팅, 테스트를 위한 여분 더미 이미지들도 세팅 (넉넉히 10개정도)
    // Galleries: 각 아카이브에는 갤러리가 각각 10개씩 존재한다.
    // Gallery Files : DummyImages -> 각 갤러리 수에 맞게 세팅 및 테스트를 위한 여분 더미 이미지들도 넉넉히 세팅

    // [Category 1]. Create ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 1. createGalleries - 정상 케이스 (단일 파일, PUBLIC Archive) */
    // Given: Archive_A_Public, UserA UserPrincipal, 단일 파일 ID
    // When: createGalleries 호출
    // Then: Gallery 엔티티가 저장되었는지 확인
    // Then: Gallery의 originalKey가 File의 s3ObjectKey와 일치하는지 확인
    // Then: Gallery의 archiveId가 Archive_A_Public의 ID와 일치하는지 확인
    // Then: Response의 createdCount가 1인지 확인
    // Then: Response의 archiveId가 정확한지 확인

    /** SCENE 2. createGalleries - 정상 케이스 (여러 파일, PUBLIC Archive) */
    // Given: Archive_A_Public, UserA UserPrincipal, 여러 파일 ID 목록 (5개)
    // When: createGalleries 호출
    // Then: 모든 Gallery 엔티티가 저장되었는지 확인 (5개)
    // Then: Response의 createdCount가 파일 개수와 일치하는지 확인
    // Then: 각 Gallery의 originalKey가 올바르게 설정되었는지 확인
    // Then: 각 Gallery의 archiveId가 올바르게 설정되었는지 확인

    /** SCENE 3. createGalleries - 정상 케이스 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserA UserPrincipal, 여러 파일 ID 목록
    // When: createGalleries 호출
    // Then: 모든 Gallery 엔티티가 저장되었는지 확인
    // Then: Response의 createdCount가 파일 개수와 일치하는지 확인

    /** SCENE 4. createGalleries - 정상 케이스 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserA UserPrincipal, 여러 파일 ID 목록
    // When: createGalleries 호출
    // Then: 모든 Gallery 엔티티가 저장되었는지 확인
    // Then: Response의 createdCount가 파일 개수와 일치하는지 확인

    // === [ 권한 케이스 ] ===
    /** SCENE 5. createGalleries - 타인 Archive에 생성 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, UserC UserPrincipal (No Friend), 파일 ID 목록
    // When: createGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 6. createGalleries - 타인 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserC UserPrincipal (No Friend), 파일 ID 목록
    // When: createGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 7. createGalleries - 타인 Archive에 생성 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, UserC UserPrincipal (No Friend), 파일 ID 목록
    // When: createGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 8. createGalleries - 친구 Archive에 생성 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, UserB UserPrincipal (Friend), 파일 ID 목록
    // When: createGalleries 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 생성 불가)

    /** SCENE 9. createGalleries - 비회원 생성 시도 */
    // Given: Archive_A_Public, userPrincipal = null, 파일 ID 목록
    // When: createGalleries 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ 파일 검증 케이스 ] ===
    /** SCENE 10. createGalleries - 다른 사용자의 파일 사용 시도 */
    // Given: Archive_A_Public, UserA UserPrincipal, UserC가 소유한 파일 ID
    // When: createGalleries 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    /** SCENE 11. createGalleries - 파일 개수 불일치 */
    // Given: Archive_A_Public, UserA UserPrincipal, 파일 ID 목록 (일부 파일이 존재하지 않음)
    // When: createGalleries 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인

    /** SCENE 12. createGalleries - 중복된 파일 ID */
    // Given: Archive_A_Public, UserA UserPrincipal, 중복된 파일 ID 목록
    // When: createGalleries 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인 (파일 개수 불일치)

    /** SCENE 13. createGalleries - 빈 파일 목록 */
    // Given: Archive_A_Public, UserA UserPrincipal, 빈 파일 ID 목록
    // When: createGalleries 호출
    // Then: createdCount가 0인지 확인 또는 예외 발생 확인 (비즈니스 로직에 따라)

    // === [ Edge Cases ] ===
    /** SCENE 14. createGalleries - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, UserA UserPrincipal, 파일 ID 목록
    // When: createGalleries 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    // [Category 2]. Read-Pagination ----------------------------------------------------------
    
    // === [ PUBLIC Archive ] ===
    /** SCENE 8. getGalleries - PUBLIC Archive (본인 조회) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserA UserPrincipal, 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인
    // Then: 각 Gallery의 originalKey가 정확히 반환되는지 확인

    /** SCENE 9. getGalleries - PUBLIC Archive (타인 조회) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 10. getGalleries - PUBLIC Archive (친구 조회) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인

    /** SCENE 11. getGalleries - PUBLIC Archive (비회원 조회) */
    // Given: Archive_A_Public, 10개의 Gallery들, userPrincipal = null, 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인

    // === [ RESTRICTED Archive ] ===
    /** SCENE 12. getGalleries - RESTRICTED Archive (본인 조회) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, UserA UserPrincipal, 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 13. getGalleries - RESTRICTED Archive (친구 조회) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인

    /** SCENE 14. getGalleries - RESTRICTED Archive (타인 조회, No Friend) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 15. getGalleries - RESTRICTED Archive (비회원 조회) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, userPrincipal = null, 페이지 정보
    // When: getGalleries 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ PRIVATE Archive ] ===
    /** SCENE 16. getGalleries - PRIVATE Archive (본인 조회) */
    // Given: Archive_A_Private, 10개의 Gallery들, UserA UserPrincipal, 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 정확히 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인

    /** SCENE 17. getGalleries - PRIVATE Archive (타인 조회, No Friend) */
    // Given: Archive_A_Private, 10개의 Gallery들, UserC UserPrincipal (No Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 18. getGalleries - PRIVATE Archive (친구 조회) */
    // Given: Archive_A_Private, 10개의 Gallery들, UserB UserPrincipal (Friend), 페이지 정보
    // When: getGalleries 호출
    // Then: 권한 예외 발생 확인 (Archive 레벨 권한)

    /** SCENE 19. getGalleries - PRIVATE Archive (비회원 조회) */
    // Given: Archive_A_Private, 10개의 Gallery들, userPrincipal = null, 페이지 정보
    // When: getGalleries 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized, Archive 레벨 권한)

    // === [ Edge Cases ] ===
    /** SCENE 20. getGalleries - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, 페이지 정보
    // When: getGalleries 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 21. getGalleries - 빈 결과 */
    // Given: Archive_A_Public, Gallery가 없는 Archive, 페이지 정보
    // When: getGalleries 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: 페이지 제목이 GalleryBook 제목인지 확인

    /** SCENE 22. getGalleries - 페이지 범위 초과 */
    // Given: Archive_A_Public, 10개의 Gallery들, 존재하지 않는 페이지 번호
    // When: getGalleries 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 23. getGalleries - 페이지네이션 정렬 확인 */
    // Given: Archive_A_Public, 10개의 Gallery들, 페이지 정보
    // When: getGalleries 호출
    // Then: Gallery 목록이 올바른 순서로 반환되는지 확인
    // Then: 페이지 크기가 정확한지 확인

    // [Category 3]. Update Book Title ----------------------------------------------------------
    /** SCENE 24. updateGalleryBookTitle - 정상 케이스 */
    // Given: 저장된 GalleryBook, 본인 UserPrincipal, 새로운 제목
    // When: updateGalleryBookTitle 호출
    // Then: GalleryBook의 제목이 업데이트되었는지 확인
    // Then: Response가 정확히 반환되는지 확인

    /** SCENE 25. updateGalleryBookTitle - 타인 수정 시도 */
    // Given: 저장된 GalleryBook, 타인 UserPrincipal
    // When: updateGalleryBookTitle 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 26. updateGalleryBookTitle - 존재하지 않는 GalleryBook */
    // Given: 존재하지 않는 archiveId
    // When: updateGalleryBookTitle 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    // [Category 4]. Delete ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 27. deleteGalleries - 정상 케이스 (단일 삭제) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserA UserPrincipal, 단일 galleryId
    // When: deleteGalleries 호출
    // Then: 해당 Gallery가 삭제되었는지 확인
    // Then: 다른 Gallery는 유지되었는지 확인
    // Then: 삭제된 Gallery의 File 연결이 해제되었는지 확인

    /** SCENE 28. deleteGalleries - 정상 케이스 (여러 개 삭제) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserA UserPrincipal, 여러 galleryId 목록 (3개)
    // When: deleteGalleries 호출
    // Then: 모든 요청된 Gallery가 삭제되었는지 확인
    // Then: 다른 Gallery는 유지되었는지 확인
    // Then: 삭제된 Gallery들의 File 연결이 해제되었는지 확인

    /** SCENE 29. deleteGalleries - 정상 케이스 (전체 삭제) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserA UserPrincipal, 모든 galleryId 목록
    // When: deleteGalleries 호출
    // Then: 모든 Gallery가 삭제되었는지 확인
    // Then: Archive는 유지되었는지 확인

    // === [ 권한 케이스 ] ===
    /** SCENE 30. deleteGalleries - 타인 삭제 시도 (PUBLIC Archive) */
    // Given: Archive_A_Public, 10개의 Gallery들, UserC UserPrincipal (No Friend), galleryId
    // When: deleteGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 31. deleteGalleries - 타인 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, UserC UserPrincipal (No Friend), galleryId
    // When: deleteGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 32. deleteGalleries - 타인 삭제 시도 (PRIVATE Archive) */
    // Given: Archive_A_Private, 10개의 Gallery들, UserC UserPrincipal (No Friend), galleryId
    // When: deleteGalleries 호출
    // Then: 권한 예외 발생 확인

    /** SCENE 33. deleteGalleries - 친구 삭제 시도 (RESTRICTED Archive) */
    // Given: Archive_A_Restricted, 10개의 Gallery들, UserB UserPrincipal (Friend), galleryId
    // When: deleteGalleries 호출
    // Then: 권한 예외 발생 확인 (친구는 조회만 가능, 삭제 불가)

    /** SCENE 34. deleteGalleries - 비회원 삭제 시도 */
    // Given: Archive_A_Public, 10개의 Gallery들, userPrincipal = null, galleryId
    // When: deleteGalleries 호출
    // Then: 권한 예외 발생 확인 (401 Unauthorized)

    // === [ Edge Cases ] ===
    /** SCENE 35. deleteGalleries - 존재하지 않는 Archive */
    // Given: 존재하지 않는 archiveId, galleryId
    // When: deleteGalleries 호출
    // Then: ARCHIVE_NOT_FOUND 예외 발생 확인

    /** SCENE 36. deleteGalleries - 다른 Archive의 Gallery 삭제 시도 */
    // Given: Archive_A_Public의 Gallery, Archive_B_Public의 archiveId, UserA UserPrincipal, Archive_A의 galleryId
    // When: deleteGalleries 호출
    // Then: 해당 Gallery가 삭제되지 않았는지 확인 (archiveId와 galleryId 매칭 검증)
    // Then: Archive_A의 Gallery는 유지되었는지 확인

    /** SCENE 37. deleteGalleries - 존재하지 않는 Gallery 삭제 시도 */
    // Given: Archive_A_Public, 존재하지 않는 galleryId, UserA UserPrincipal
    // When: deleteGalleries 호출
    // Then: 삭제된 Gallery가 0개인지 확인 (정상 처리, 예외 없음)

    /** SCENE 38. deleteGalleries - 빈 galleryId 목록 */
    // Given: Archive_A_Public, 빈 galleryId 목록, UserA UserPrincipal
    // When: deleteGalleries 호출
    // Then: 삭제된 Gallery가 0개인지 확인 (정상 처리, 예외 없음)
}

