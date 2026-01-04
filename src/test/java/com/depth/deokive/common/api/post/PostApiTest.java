package com.depth.deokive.common.api.post;

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
        // Then: 응답의 files[0].cdnUrl이 CDN URL 형식인지 검증 (https://cdn... 형식)
        // Then: 응답의 thumbnailUrl이 FileA_1의 CDN URL인지 확인 (PREVIEW 파일이 썸네일)
        // Then: **URL 형식 검증** - thumbnailUrl이 CDN URL 형식인지 확인 (https://cdn... 형식)
        // Then: DB에서 Post 엔티티의 thumbnailKey가 FileA_1의 s3ObjectKey와 일치하는지 확인
        // Then: DB에서 PostFileMap이 2개 생성되었고 올바른 sequence로 저장되었는지 확인

        /** SCENE 2. 정상 생성 - 파일 없이 생성 */
        // Given: UserA 토큰
        // Given: Request Body (title="스포츠 게시글", content="내용", category=SPORT, files=null or empty)
        // When: POST /api/v1/posts
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.id가 null이 아니고 유효한 Long 값인지 확인
        // Then: **응답 Body 검증** - response.title == "스포츠 게시글" 확인
        // Then: **응답 Body 검증** - response.category == SPORT 확인
        // Then: **응답 Body 검증** - response.files가 null이거나 빈 리스트인지 확인
        // Then: **응답 Body 검증** - response.thumbnailUrl == null 확인
        // Then: **DB 검증** - postRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Post 엔티티의 title == "스포츠 게시글" 확인
        // Then: **DB 검증** - DB의 Post 엔티티의 category == SPORT 확인
        // Then: **DB 검증** - DB의 Post 엔티티의 thumbnailKey == null 확인
        // Then: **DB 검증** - postFileMapRepository.findAllByPostId(response.getId())가 빈 리스트인지 확인

        /** SCENE 3. 정상 생성 - 모든 카테고리 지원 확인 */
        // Given: UserA 토큰
        // When: 각 Category Enum(ACTOR, MUSICIAN, IDOL, SPORT, ANIMATION 등) 별로 생성 요청
        // Then: 모두 201 Created
        // Then: 각 응답의 category 필드가 올바른 값인지 확인
        // Then: DB에서 각 Post 엔티티의 category 필드가 올바르게 저장되었는지 확인

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
        // Given: UserA 토큰, PostID (파일 포함)
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK
        // Then: 응답의 모든 필드(title, content, category, viewCount, likeCount, hotScore 등) 검증
        // Then: 응답의 files 리스트가 올바르게 포함되었는지 확인
        // Then: 응답의 files 각 항목의 cdnUrl이 CDN URL 형식인지 검증 (https://cdn... 형식)
        // Then: 응답의 thumbnailUrl이 CDN URL 형식인지 검증 (파일이 있는 경우, https://cdn... 형식)

        /** SCENE 9. 정상 조회 - 타인 (UserB) */
        // Given: UserB 토큰, PostID
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK (게시글은 기본적으로 Public)

        /** SCENE 10. 정상 조회 - 비회원 (Anonymous) */
        // Given: 토큰 없음, PostID
        // When: GET /api/v1/posts/{postId}
        // Then: 200 OK

        /** SCENE 11. 조회수 증가 확인 */
        // Given: Post ID, 초기 viewCount 확인
        // When: GET /api/v1/posts/{postId} 호출 (UserB 수행)
        // Then: 200 OK
        // Then: DB에서 Post 엔티티의 viewCount가 1 증가했는지 직접 확인
        // Then: 다시 조회 API 호출 시 응답의 viewCount도 증가했는지 확인

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
        // Given: UserA 토큰, files=[ {fileId: FileA_3, role: PREVIEW} ] (새 파일, 기존 FileA_1 있음)
        // When: PATCH /api/v1/posts/{postId}
        // Then: 200 OK
        // Then: 응답 내 files 리스트가 [FileA_3]로 교체되었는지 확인
        // Then: 응답의 thumbnailUrl이 FileA_3의 CDN URL로 변경되었는지 확인
        // Then: DB에서 기존 PostFileMap(FileA_1)이 삭제되었는지 확인
        // Then: DB에서 새로운 PostFileMap(FileA_3)만 생성되었는지 확인
        // Then: DB에서 Post 엔티티의 thumbnailKey가 FileA_3의 s3ObjectKey로 변경되었는지 확인

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
        // Given: UserA 토큰, Post ID (파일 포함)
        // When: DELETE /api/v1/posts/{postId}
        // Then: 204 No Content (응답 Body 없음)
        // Then: **재조회 검증** - GET /api/v1/posts/{postId} 호출 시 404 Not Found 확인
        // Then: **DB 검증** - postRepository.findById(postId).isPresent() == false 확인
        // Then: **DB 검증** - postFileMapRepository.findAllByPostId(postId)가 빈 리스트인지 확인
        // Then: **DB 검증** - fileRepository.findById(File1.getId())로 조회 시 File1 엔티티가 여전히 존재하는지 확인 (재사용 가능)

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
        // Setup: Post 3개 생성 (createdAt 다르게 설정)
        //   - Post_Recent: createdAt = Now (가장 최신)
        //   - Post_Idol: createdAt = Now - 1일
        //   - Post_Actor: createdAt = Now - 2일
        // Given: sort=createdAt, direction=DESC
        // When: GET /api/v1/posts?sort=createdAt&direction=DESC
        // Then: 200 OK
        // Then: 응답의 totalElements=3 확인
        // Then: **실제 데이터 순서 검증** - content.get(0).getCreatedAt()가 content.get(1).getCreatedAt()보다 이후인지 확인
        // Then: content.get(0).getPostId() == Post_Recent.getId() 확인 (가장 최신)
        // Then: Post_Recent가 가장 상위 노출 확인
        // Then: **URL 형식 검증** - content의 각 Post의 thumbnailUrl이 CDN URL 형식인지 확인 (null이 아닌 경우, https://cdn... 형식)

        /** SCENE 21. 카테고리 필터링 (Category=IDOL) */
        // Given: category=IDOL
        // When: GET /api/v1/posts
        // Then: 200 OK, Post_Idol, Post_Recent만 조회됨 (Post_Actor 제외)

        /** SCENE 22. 인기순 정렬 (Hot Score) */
        // Setup: Post 3개 생성 (hotScore 다르게 설정)
        //   - Post_Idol: hotScore=100
        //   - Post_Actor: hotScore=50
        //   - Post_Recent: hotScore=0
        // Given: sort=hotScore, direction=DESC
        // When: GET /api/v1/posts?sort=hotScore&direction=DESC
        // Then: 200 OK
        // Then: **실제 데이터 순서 검증** - content.get(0).getHotScore() > content.get(1).getHotScore() 확인
        // Then: content.get(0).getPostId() == Post_Idol.getId() 확인
        // Then: content.get(0).getHotScore() == 100.0 확인
        // Then: content.get(1).getHotScore() == 50.0 확인
        // Then: content.get(2).getHotScore() == 0.0 확인
        // Then: Post_Idol(100) -> Post_Actor(50) -> Post_Recent(0) 순서 확인

        /** SCENE 23. 조회수 정렬 */
        // Setup: Post 3개 생성 (viewCount 다르게 설정)
        //   - Post_Actor: viewCount=100
        //   - Post_Idol: viewCount=10
        //   - Post_Recent: viewCount=0
        // Given: sort=viewCount, direction=DESC
        // When: GET /api/v1/posts?sort=viewCount&direction=DESC
        // Then: 200 OK
        // Then: **실제 데이터 순서 검증** - content.get(0).getViewCount() > content.get(1).getViewCount() 확인
        // Then: content.get(0).getPostId() == Post_Actor.getId() 확인
        // Then: content.get(0).getViewCount() == 100 확인
        // Then: content.get(1).getViewCount() == 10 확인
        // Then: content.get(2).getViewCount() == 0 확인
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