package com.depth.deokive.domain.post.service;

/** PostService CRUD 테스트 */
class PostServiceTest {

    /** Setup */
    // Users : UserA, UserB, UserC, UserD (N명 설정)
    // Posts : User 당 10개의 Posts 생성 (다양한 카테고리 분배)
    // Files : Post 당 10개의 더미 Files, 테스트를 위한 여분의 더미 파일 넉넉히 세팅
    // PostFileMap : 각 Post의 파일들은 sequence 순서대로, MediaRole(PREVIEW/CONTENT) 분배
    // like, view : hotScore 처리를 위해 적정 비율로 분배 (일부 Post는 높은 like/view, 일부는 낮은 like/view)

    // [Category 1]. Create ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 1. createPost - 정상 케이스 (파일 포함, PREVIEW 파일 있음) */
    // Given: UserA UserPrincipal, 제목, 내용, 카테고리, 파일 목록 (PREVIEW 파일 포함)
    // When: createPost 호출
    // Then: Post 엔티티가 저장되었는지 확인
    // Then: PostFileMap이 생성되었는지 확인
    // Then: 파일 순서(sequence)가 올바르게 저장되었는지 확인
    // Then: 썸네일이 PREVIEW 파일 기반인지 확인
    // Then: 썸네일 Key가 Medium 크기로 변환되었는지 확인

    /** SCENE 2. createPost - 정상 케이스 (파일 포함, PREVIEW 파일 없음) */
    // Given: UserA UserPrincipal, 제목, 내용, 카테고리, 파일 목록 (PREVIEW 없음, CONTENT만)
    // When: createPost 호출
    // Then: Post 엔티티가 저장되었는지 확인
    // Then: PostFileMap이 생성되었는지 확인
    // Then: 썸네일이 첫 번째 파일(sequence 0) 기반인지 확인

    /** SCENE 3. createPost - 정상 케이스 (다양한 카테고리) */
    // Given: UserA UserPrincipal, 제목, 내용, 각 카테고리별로, 파일 목록
    // When: createPost 호출 (각 카테고리별로)
    // Then: Post 엔티티가 저장되었는지 확인
    // Then: Post의 카테고리가 올바르게 설정되었는지 확인

    /** SCENE 4. createPost - 파일 없이 생성 */
    // Given: UserA UserPrincipal, 제목, 내용, 카테고리, 파일 목록 = null
    // When: createPost 호출
    // Then: Post 엔티티가 저장되었는지 확인
    // Then: PostFileMap이 생성되지 않았는지 확인
    // Then: 썸네일이 null인지 확인

    /** SCENE 5. createPost - 파일 없이 생성 (빈 리스트) */
    // Given: UserA UserPrincipal, 제목, 내용, 카테고리, 파일 목록 = []
    // When: createPost 호출
    // Then: Post 엔티티가 저장되었는지 확인
    // Then: PostFileMap이 생성되지 않았는지 확인
    // Then: 썸네일이 null인지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 6. createPost - 존재하지 않는 사용자 */
    // Given: 존재하지 않는 userId를 가진 UserPrincipal
    // When: createPost 호출
    // Then: USER_NOT_FOUND 예외 발생 확인

    /** SCENE 7. createPost - 다른 사용자의 파일 사용 시도 */
    // Given: UserA UserPrincipal, UserB가 소유한 파일 ID
    // When: createPost 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    /** SCENE 8. createPost - 중복된 파일 ID */
    // Given: UserA UserPrincipal, 중복된 파일 ID 목록
    // When: createPost 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인 (파일 개수 불일치)

    /** SCENE 9. createPost - 존재하지 않는 파일 ID */
    // Given: UserA UserPrincipal, 존재하지 않는 파일 ID 목록
    // When: createPost 호출
    // Then: FILE_NOT_FOUND 예외 발생 확인

    // [Category 2]. Read ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 10. getPost - 정상 케이스 (파일 포함, 본인 조회) */
    // Given: UserA의 Post, PostFileMap들 (10개), UserA UserPrincipal
    // When: getPost 호출
    // Then: Post 정보가 정확히 반환되는지 확인
    // Then: PostFileMap이 올바른 순서(sequence)로 반환되는지 확인
    // Then: 조회수가 증가했는지 확인
    // Then: 각 파일의 이미지 URL이 정확히 반환되는지 확인

    /** SCENE 11. getPost - 정상 케이스 (파일 포함, 타인 조회) */
    // Given: UserA의 Post, PostFileMap들, UserB UserPrincipal
    // When: getPost 호출
    // Then: Post 정보가 정확히 반환되는지 확인
    // Then: PostFileMap이 올바른 순서로 반환되는지 확인
    // Then: 조회수가 증가했는지 확인

    /** SCENE 12. getPost - 정상 케이스 (파일 없음) */
    // Given: UserA의 Post, PostFileMap 없음
    // When: getPost 호출
    // Then: Post 정보가 정확히 반환되는지 확인
    // Then: 파일 목록이 빈 리스트인지 확인
    // Then: 조회수가 증가했는지 확인

    /** SCENE 13. getPost - 정상 케이스 (PREVIEW 파일 포함) */
    // Given: UserA의 Post, PostFileMap들 (PREVIEW 파일 포함)
    // When: getPost 호출
    // Then: Post 정보가 정확히 반환되는지 확인
    // Then: PREVIEW 파일이 올바른 순서로 포함되었는지 확인

    /** SCENE 14. getPost - 정상 케이스 (CONTENT 파일만) */
    // Given: UserA의 Post, PostFileMap들 (CONTENT만, PREVIEW 없음)
    // When: getPost 호출
    // Then: Post 정보가 정확히 반환되는지 확인
    // Then: 모든 파일이 CONTENT로 반환되는지 확인

    /** SCENE 15. getPost - 조회수 증가 확인 (동시성 고려) */
    // Given: UserA의 Post (초기 viewCount = 0), 여러 번 조회
    // When: getPost 호출 (3번)
    // Then: 조회수가 3으로 증가했는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 16. getPost - 존재하지 않는 Post */
    // Given: 존재하지 않는 postId
    // When: getPost 호출
    // Then: POST_NOT_FOUND 예외 발생 확인

    // [Category 3]. Update ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 17. updatePost - 정상 케이스 (제목, 내용, 카테고리 수정) */
    // Given: UserA의 Post, UserA UserPrincipal, 새로운 제목, 내용, 카테고리
    // When: updatePost 호출
    // Then: Post의 제목이 업데이트되었는지 확인
    // Then: Post의 내용이 업데이트되었는지 확인
    // Then: Post의 카테고리가 업데이트되었는지 확인
    // Then: 기존 PostFileMap이 유지되었는지 확인

    /** SCENE 18. updatePost - 제목만 수정 */
    // Given: UserA의 Post, UserA UserPrincipal, 새로운 제목, 내용 = null, 카테고리 = null
    // When: updatePost 호출
    // Then: Post의 제목만 업데이트되었는지 확인
    // Then: 기존 내용과 카테고리가 유지되었는지 확인

    /** SCENE 19. updatePost - 내용만 수정 */
    // Given: UserA의 Post, UserA UserPrincipal, 제목 = null, 새로운 내용, 카테고리 = null
    // When: updatePost 호출
    // Then: Post의 내용만 업데이트되었는지 확인
    // Then: 기존 제목과 카테고리가 유지되었는지 확인

    /** SCENE 20. updatePost - 카테고리만 수정 */
    // Given: UserA의 Post, UserA UserPrincipal, 제목 = null, 내용 = null, 새로운 카테고리
    // When: updatePost 호출
    // Then: Post의 카테고리만 업데이트되었는지 확인
    // Then: 기존 제목과 내용이 유지되었는지 확인

    /** SCENE 21. updatePost - 파일 전체 교체 (PREVIEW 파일 포함) */
    // Given: UserA의 Post, 기존 파일들, UserA UserPrincipal, 새로운 파일 목록 (PREVIEW 포함)
    // When: updatePost 호출
    // Then: 기존 PostFileMap이 삭제되었는지 확인
    // Then: 새로운 PostFileMap이 생성되었는지 확인
    // Then: 썸네일이 PREVIEW 파일 기반으로 업데이트되었는지 확인

    /** SCENE 22. updatePost - 파일 전체 교체 (PREVIEW 파일 없음) */
    // Given: UserA의 Post, 기존 파일들, UserA UserPrincipal, 새로운 파일 목록 (PREVIEW 없음)
    // When: updatePost 호출
    // Then: 기존 PostFileMap이 삭제되었는지 확인
    // Then: 새로운 PostFileMap이 생성되었는지 확인
    // Then: 썸네일이 첫 번째 파일 기반으로 업데이트되었는지 확인

    /** SCENE 23. updatePost - 파일 삭제 (빈 리스트) */
    // Given: UserA의 Post, 기존 파일들, UserA UserPrincipal, 파일 목록 = []
    // When: updatePost 호출
    // Then: 모든 PostFileMap이 삭제되었는지 확인
    // Then: 썸네일이 null인지 확인

    /** SCENE 24. updatePost - 파일 유지 (파일 목록 = null) */
    // Given: UserA의 Post, 기존 파일들, UserA UserPrincipal, 파일 목록 = null
    // When: updatePost 호출
    // Then: 기존 PostFileMap이 유지되었는지 확인
    // Then: 썸네일이 기존과 동일한지 확인

    /** SCENE 25. updatePost - 파일 추가 (기존 파일 유지 후 새 파일 추가) */
    // Given: UserA의 Post, 기존 파일들 (5개), UserA UserPrincipal, 새로운 파일 목록 (기존 5개 + 새 3개)
    // When: updatePost 호출
    // Then: 기존 PostFileMap이 삭제되었는지 확인
    // Then: 새로운 PostFileMap이 8개 생성되었는지 확인
    // Then: 파일 순서가 올바르게 설정되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 26. updatePost - 타인 수정 시도 */
    // Given: UserA의 Post, UserB UserPrincipal
    // When: updatePost 호출
    // Then: AUTH_FORBIDDEN 예외 발생 확인

    /** SCENE 27. updatePost - 존재하지 않는 Post */
    // Given: 존재하지 않는 postId, UserA UserPrincipal
    // When: updatePost 호출
    // Then: POST_NOT_FOUND 예외 발생 확인

    /** SCENE 28. updatePost - 다른 사용자의 파일 사용 시도 */
    // Given: UserA의 Post, UserA UserPrincipal, UserB가 소유한 파일 ID
    // When: updatePost 호출
    // Then: FILE_NOT_FOUND 또는 권한 예외 발생 확인

    // [Category 4]. Delete ----------------------------------------------------------
    
    // === [ 정상 케이스 ] ===
    /** SCENE 29. deletePost - 정상 케이스 (파일 포함) */
    // Given: UserA의 Post, 관련된 PostFileMap들 (10개), UserA UserPrincipal
    // When: deletePost 호출
    // Then: PostFileMap이 삭제되었는지 확인 (Bulk 삭제)
    // Then: Post가 삭제되었는지 확인
    // Then: File 엔티티는 유지되었는지 확인 (다른 Post에서 사용 가능)

    /** SCENE 30. deletePost - 정상 케이스 (파일 없음) */
    // Given: UserA의 Post, PostFileMap 없음, UserA UserPrincipal
    // When: deletePost 호출
    // Then: Post가 삭제되었는지 확인

    // === [ 예외 케이스 ] ===
    /** SCENE 31. deletePost - 타인 삭제 시도 */
    // Given: UserA의 Post, UserB UserPrincipal
    // When: deletePost 호출
    // Then: AUTH_FORBIDDEN 예외 발생 확인

    /** SCENE 32. deletePost - 존재하지 않는 Post */
    // Given: 존재하지 않는 postId, UserA UserPrincipal
    // When: deletePost 호출
    // Then: POST_NOT_FOUND 예외 발생 확인

    // [Category 5]. Read-Pagination ----------------------------------------------------------
    
    // === [ 전체 카테고리 ] ===
    /** SCENE 33. getPosts - 정상 케이스 (전체 카테고리, 기본 정렬) */
    // Given: 여러 카테고리의 Post들, 카테고리 = null, sort = null, 페이지 정보
    // When: getPosts 호출
    // Then: 모든 카테고리의 Post가 반환되는지 확인
    // Then: 페이지 제목이 "전체 게시판"인지 확인
    // Then: 페이지네이션이 정상 동작하는지 확인
    // Then: 정렬이 최신순(기본)인지 확인

    /** SCENE 34. getPosts - 정상 케이스 (전체 카테고리, hotScore 정렬) */
    // Given: 여러 카테고리의 Post들, 카테고리 = null, sort = "hotScore", 페이지 정보
    // When: getPosts 호출
    // Then: 모든 카테고리의 Post가 반환되는지 확인
    // Then: hotScore 기준으로 정렬되었는지 확인 (높은 순서)
    // Then: 페이지 제목이 "핫한 게시판"인지 확인

    // === [ 특정 카테고리 ] ===
    /** SCENE 35. getPosts - 정상 케이스 (특정 카테고리, 기본 정렬) */
    // Given: 특정 카테고리의 Post들, 카테고리 지정, sort = null, 페이지 정보
    // When: getPosts 호출
    // Then: 해당 카테고리의 Post만 반환되는지 확인
    // Then: 다른 카테고리의 Post는 반환되지 않는지 확인
    // Then: 페이지 제목이 카테고리명을 포함하는지 확인
    // Then: 정렬이 최신순(기본)인지 확인

    /** SCENE 36. getPosts - 정상 케이스 (특정 카테고리, hotScore 정렬) */
    // Given: 특정 카테고리의 Post들, 카테고리 지정, sort = "hotScore", 페이지 정보
    // When: getPosts 호출
    // Then: 해당 카테고리의 Post만 반환되는지 확인
    // Then: hotScore 기준으로 정렬되었는지 확인
    // Then: 페이지 제목이 카테고리명을 포함하는지 확인

    /** SCENE 37. getPosts - 정상 케이스 (각 카테고리별 조회) */
    // Given: 각 카테고리별 Post들, 각 카테고리별로 조회
    // When: getPosts 호출 (각 카테고리별로)
    // Then: 해당 카테고리의 Post만 반환되는지 확인
    // Then: 페이지 제목이 각 카테고리명을 포함하는지 확인

    // === [ hotScore 정렬 상세 테스트 ] ===
    /** SCENE 38. getPosts - hotScore 정렬 (높은 like/view 우선) */
    // Given: 여러 Post들 (다양한 like/view 수), sort = "hotScore"
    // When: getPosts 호출
    // Then: hotScore가 높은 Post가 먼저 반환되는지 확인
    // Then: hotScore 계산이 정확한지 확인 (like, view 비율)

    /** SCENE 39. getPosts - hotScore 정렬 (동일한 hotScore 처리) */
    // Given: 여러 Post들 (동일한 hotScore), sort = "hotScore"
    // When: getPosts 호출
    // Then: 동일한 hotScore의 Post들이 올바르게 정렬되는지 확인

    /** SCENE 40. getPosts - hotScore 정렬 (카테고리별) */
    // Given: 특정 카테고리의 Post들 (다양한 hotScore), 카테고리 지정, sort = "hotScore"
    // When: getPosts 호출
    // Then: 해당 카테고리 내에서 hotScore 기준으로 정렬되는지 확인

    // === [ 페이지네이션 테스트 ] ===
    /** SCENE 41. getPosts - 페이지네이션 (첫 페이지) */
    // Given: 여러 Post들, 페이지 정보 (page = 0)
    // When: getPosts 호출
    // Then: 첫 페이지의 Post들이 반환되는지 확인
    // Then: 페이지 정보가 정확한지 확인

    /** SCENE 42. getPosts - 페이지네이션 (중간 페이지) */
    // Given: 여러 Post들, 페이지 정보 (page = 1)
    // When: getPosts 호출
    // Then: 두 번째 페이지의 Post들이 반환되는지 확인
    // Then: 페이지 정보가 정확한지 확인

    /** SCENE 43. getPosts - 페이지네이션 (마지막 페이지) */
    // Given: 여러 Post들, 페이지 정보 (마지막 페이지)
    // When: getPosts 호출
    // Then: 마지막 페이지의 Post들이 반환되는지 확인
    // Then: 다음 페이지가 없는지 확인

    /** SCENE 44. getPosts - 페이지네이션 (페이지 크기 변경) */
    // Given: 여러 Post들, 다양한 페이지 크기 (size = 10, 20, 50)
    // When: getPosts 호출 (각 크기별로)
    // Then: 각 페이지 크기에 맞게 Post가 반환되는지 확인

    // === [ Edge Cases ] ===
    /** SCENE 45. getPosts - 빈 결과 (전체 카테고리) */
    // Given: Post가 없음, 카테고리 = null
    // When: getPosts 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: 페이지 제목이 "전체 게시판"인지 확인

    /** SCENE 46. getPosts - 빈 결과 (특정 카테고리) */
    // Given: 특정 카테고리에 Post 없음, 카테고리 지정
    // When: getPosts 호출
    // Then: 빈 페이지가 반환되는지 확인
    // Then: 페이지 제목이 카테고리명을 포함하는지 확인

    /** SCENE 47. getPosts - 페이지 범위 초과 */
    // Given: 여러 Post들, 존재하지 않는 페이지 번호
    // When: getPosts 호출
    // Then: 페이지 범위 예외 발생 확인

    /** SCENE 48. getPosts - 잘못된 카테고리 */
    // Given: 존재하지 않는 카테고리, 카테고리 지정
    // When: getPosts 호출
    // Then: 빈 결과 또는 예외 발생 확인
}

