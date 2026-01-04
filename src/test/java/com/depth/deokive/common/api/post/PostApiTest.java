package com.depth.deokive.domain.post.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Post 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis, MailHog, S3(Mock)
 * 2. 공통 유틸 (AuthSteps, FileSteps):
 * - 회원가입/로그인, 파일 업로드(S3 Multipart) 선행
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Writer): 게시글 작성자
 * - UserB (Stranger): 타인
 * - Anonymous: 비회원 (게시글 조회 가능)
 *
 * ■ 주요 검증 포인트
 * - 게시글 생성/수정 시 파일 첨부 및 썸네일(PREVIEW) 자동 설정 로직
 * - 게시글 수정 시 기존 파일 삭제 및 신규 파일 교체(Replace) 로직
 * - IDOR 방지 (타인의 파일을 내 게시글에 첨부 시도 차단)
 * - 피드 조회 시 카테고리 필터링 및 다양한 정렬 조건(Hot, View, Like) 검증
 */
@DisplayName("Post API 통합 테스트 시나리오")
class PostApiTest {

    /**
     * [Setup]
     * 1. AuthSteps: UserA, UserB 토큰 확보
     * 2. FileSteps:
     * - UserA가 파일 5개 업로드 (FileA_1 ~ FileA_5)
     * - UserB가 파일 1개 업로드 (FileB_1) -> IDOR 테스트용
     */

    // ========================================================================================
    // [Category 1]. Create Post (POST /api/v1/posts)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 게시글 생성")
    class CreatePost {

        /** SCENE 1. 정상 생성 - 파일 포함 (썸네일 지정) */
        // Given: UserA 토큰
        // Given: Request Body
        //   - title, content, category=IDOL
        //   - files=[ {fileId: FileA_1, role: PREVIEW, seq:0}, {fileId: FileA_2, role: CONTENT, seq:1} ]
        // When: POST /api/v1/posts
        // Then: 201 Created
        // Then: 응답 내 files 리스트 순서 및 mediaRole 확인
        // Then: (목록 조회 시) FileA_1이 썸네일로 설정되었는지 확인

        /** SCENE 2. 정상 생성 - 파일 없이 생성 */
        // Given: UserA 토큰, category=SPORT, files=null (or empty)
        // When: POST /api/v1/posts
        // Then: 201 Created
        // Then: 썸네일 URL이 null인지 확인

        /** SCENE 3. 정상 생성 - 모든 카테고리 지원 확인 */
        // Given: UserA 토큰
        // When: 각 Category Enum(ACTOR, MUSICIAN, ...) 별로 생성 요청
        // Then: 모두 201 Created

        /** SCENE 4. 예외 - 필수값 누락 */
        // Given: title="", content="", category=null
        // When: POST /api/v1/posts
        // Then: 400 Bad Request

        /** SCENE 5. 예외 - IDOR (타인의 파일 첨부 시도) */
        // Given: UserA 토큰
        // Given: files=[ {fileId: FileB_1} ] (UserB 소유 파일)
        // When: POST /api/v1/posts
        // Then: 403 Forbidden (AUTH_FORBIDDEN) - FileService.validateFileOwners 검증

        /** SCENE 6. 예외 - 존재하지 않는 파일 ID */
        // Given: UserA 토큰, files=[ {fileId: 99999} ]
        // When: POST /api/v1/posts
        // Then: 404 Not Found (FILE_NOT_FOUND)

        /** SCENE 7. 예외 - 중복된 파일 ID 전송 */
        // Given: UserA 토큰, files=[ {fileId: FileA_1}, {fileId: FileA_1} ]
        // When: POST /api/v1/posts
        // Then: 404 or 400 (로직에 따라 FILE_NOT_FOUND 또는 Bad Request)
    }

    // ========================================================================================
    // [Category 2]. Read Detail (GET /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 게시글 상세 조회")
    class ReadPost {
        // Setup: UserA가 Post 생성 (FileA_1, FileA_2 포함)

        /** SCENE 8. 정상 조회 - 작성자 본인 */
        // Given: UserA 토큰, PostID
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK, 데이터 정합성 확인

        /** SCENE 9. 정상 조회 - 타인 (UserB) */
        // Given: UserB 토큰, PostID
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK (게시글은 기본적으로 Public)

        /** SCENE 10. 정상 조회 - 비회원 (Anonymous) */
        // Given: 토큰 없음, PostID
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK

        /** SCENE 11. 조회수 증가 확인 */
        // Given: 현재 viewCount 확인
        // When: GET /api/v1/posts/{postId} 호출 (UserB 수행)
        // Then: 다시 조회 시 viewCount가 +1 되었는지 확인

        /** SCENE 12. 예외 - 존재하지 않는 게시글 */
        // When: GET /api/v1/posts/99999
        // Then: 404 Not Found (POST_NOT_FOUND)
    }

    // ========================================================================================
    // [Category 3]. Update Post (PATCH /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 게시글 수정 (정보 및 파일)")
    class UpdatePost {
        // Setup: UserA가 Post 생성 (기존 파일: FileA_1)

        /** SCENE 13. 정상 수정 - 텍스트 정보만 변경 */
        // Given: UserA 토큰, title="Updated", content="Updated", files=null (유지)
        // When: PATCH /api/v1/posts/{postId}
        // Then: 200 OK, 제목/내용 변경 확인, 기존 파일 유지 확인

        /** SCENE 14. 정상 수정 - 파일 전체 교체 (Replace) */
        // Given: UserA 토큰, files=[ {fileId: FileA_3, role: PREVIEW} ] (새 파일)
        // When: PATCH /api/v1/posts/{postId}
        // Then: 200 OK
        // Then: 기존 FileA_1 연결은 끊어지고, FileA_3만 연결되었는지 확인
        // Then: 썸네일이 FileA_3 기반으로 변경되었는지 확인

        /** SCENE 15. 정상 수정 - 파일 전체 삭제 */
        // Given: UserA 토큰, files=[] (빈 리스트)
        // When: PATCH /api/v1/posts/{postId}
        // Then: 200 OK
        // Then: 연결된 파일이 0개인지 확인, 썸네일이 null인지 확인

        /** SCENE 16. 예외 - 타인(UserB)이 수정 시도 */
        // Given: UserB 토큰, UserA의 PostID
        // When: PATCH /api/v1/posts/{postId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN)

        /** SCENE 17. 예외 - IDOR (수정 시 타인의 파일로 교체 시도) */
        // Given: UserA 토큰, files=[ {fileId: FileB_1} ]
        // When: PATCH /api/v1/posts/{postId}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 4]. Delete Post (DELETE /api/v1/posts/{postId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 게시글 삭제")
    class DeletePost {
        // Setup: Post 생성

        /** SCENE 18. 정상 삭제 - 본인 */
        // Given: UserA 토큰
        // When: DELETE /api/v1/posts/{postId}
        // Then: 204 No Content
        // Then: 재조회 시 404 확인

        /** SCENE 19. 예외 - 타인(UserB)이 삭제 시도 */
        // Given: UserB 토큰
        // When: DELETE /api/v1/posts/{postId}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 5]. Feed & Pagination (GET /api/v1/posts)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 게시글 피드 조회 (필터/정렬)")
    class PostFeed {
        // Setup:
        // 1. Post_Idol (Category=IDOL, View=10, Like=5, Hot=100)
        // 2. Post_Actor (Category=ACTOR, View=100, Like=1, Hot=50)
        // 3. Post_Recent (Category=IDOL, View=0, Like=0, Hot=0, Created=Now)

        /** SCENE 20. 전체 조회 (필터 없음) + 최신순 정렬 */
        // Given: sort=createdAt, direction=DESC
        // When: GET /api/v1/posts
        // Then: 200 OK, 3개 모두 조회됨, Post_Recent가 가장 상위 노출

        /** SCENE 21. 카테고리 필터링 (Category=IDOL) */
        // Given: category=IDOL
        // When: GET /api/v1/posts
        // Then: 200 OK, Post_Idol, Post_Recent만 조회됨 (Post_Actor 제외)

        /** SCENE 22. 인기순 정렬 (Hot Score) */
        // Given: sort=hotScore, direction=DESC
        // When: GET /api/v1/posts
        // Then: Post_Idol(100) -> Post_Actor(50) -> Post_Recent(0) 순서 확인

        /** SCENE 23. 조회수 정렬 */
        // Given: sort=viewCount, direction=DESC
        // Then: Post_Actor(100) -> Post_Idol(10) -> Post_Recent(0) 순서 확인

        /** SCENE 24. 빈 결과 조회 */
        // Given: category=ANIMATION (데이터 없음)
        // When: GET /api/v1/posts
        // Then: 200 OK, content 비어있음

        /** SCENE 25. 페이지 범위 초과 */
        // Given: page=100
        // When: GET /api/v1/posts
        // Then: 404 Not Found (PAGE_NOT_FOUND) -> Service 설정에 따라 404 혹은 빈 리스트
    }
}