package com.depth.deokive.common.api.repost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Repost 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis, MailHog
 * 2. 공통 유틸 (AuthSteps, ArchiveSteps, FriendSteps, PostSteps):
 * - 회원가입, 아카이브 생성, 친구 맺기, '원본 게시글' 생성 선행 필요
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Me): 리포스트북 주인
 * - UserB (Friend): UserA의 친구 (Restricted 접근 가능) & 원본 게시글 작성자
 * - UserC (Stranger): 타인 & 원본 게시글 작성자
 * - Anonymous: 비회원
 *
 * ■ 주요 검증 포인트
 * - 탭(Tab) 생명주기: 생성(최대 10개 제한), 수정, 삭제(하위 리포스트 일괄 삭제)
 * - 리포스트 생성: 원본 Post의 정보(제목, 썸네일)가 스냅샷으로 잘 저장되는지
 * - 중복 방지: 같은 탭에 같은 Post를 중복 스크랩 방지
 * - 접근 제어: Archive Visibility에 따른 리포스트 목록 조회 권한
 */
@DisplayName("Repost API 통합 테스트 시나리오")
class RepostApiTest {

    /**
     * [Setup]
     * 1. AuthSteps: UserA, UserB, UserC 토큰 확보
     * 2. FriendSteps: UserA <-> UserB 친구 설정
     * 3. ArchiveSteps: UserA의 Public, Restricted, Private 아카이브 생성 -> ID 확보
     * 4. PostSteps:
     * - UserB가 게시글 생성 (Post_B_1: 썸네일 있음)
     * - UserC가 게시글 생성 (Post_C_1: 썸네일 없음)
     * - UserA가 게시글 생성 (Post_A_1)
     */

    // ========================================================================================
    // [Category 1]. RepostTab Management (탭 관리)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 리포스트 탭 관리")
    class TabLifecycle {

        /** SCENE 1. 탭 생성 - 정상 케이스 */
        // Given: UserA 토큰, Public Archive ID
        // When: POST /api/v1/repost/tabs/{archiveId}
        // Then: 201 Created
        // Then: 응답에 title="1번째 탭"(자동생성 이름) 확인

        /** SCENE 2. 탭 생성 - 이름 자동 증가 확인 */
        // Given: 이미 탭이 1개 있는 상태
        // When: POST /api/v1/repost/tabs/{archiveId}
        // Then: 201 Created, title="2번째 탭" 확인

        /** SCENE 3. 탭 수정 - 이름 변경 */
        // Given: UserA 토큰, TabID
        // Given: Request Body { "title": "맛집 모음" }
        // When: PATCH /api/v1/repost/tabs/{tabId}
        // Then: 200 OK, title="맛집 모음" 확인

        /** SCENE 4. 탭 삭제 - 빈 탭 삭제 */
        // Given: UserA 토큰, TabID (내용물 없음)
        // When: DELETE /api/v1/repost/tabs/{tabId}
        // Then: 204 No Content

        /** SCENE 5. 예외 - 탭 생성 개수 초과 (Limit 10) */
        // Given: 이미 10개의 탭이 생성된 Archive
        // When: POST /api/v1/repost/tabs/{archiveId}
        // Then: 500 Internal Server Error (REPOST_TAB_LIMIT_EXCEED)

        /** SCENE 6. 예외 - 타인이 탭 생성/수정/삭제 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // When: POST, PATCH, DELETE 시도
        // Then: 403 Forbidden (AUTH_FORBIDDEN)
    }

    // ========================================================================================
    // [Category 2]. Repost Lifecycle (리포스트 CRUD)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 리포스트(스크랩) 생명주기")
    class RepostLifecycle {
        // Setup: UserA의 Archive에 Tab 생성 완료 (Tab_A)

        /** SCENE 7. 리포스트 생성 - 정상 (썸네일 있는 게시글) */
        // Given: UserA 토큰, Tab_A ID
        // Given: Request Body { "postId": Post_B_1_ID } (UserB의 글)
        // When: POST /api/v1/repost/{tabId}
        // Then: 201 Created
        // Then: 응답 데이터 검증
        //       - title == Post_B_1의 제목 (스냅샷)
        //       - thumbnailUrl == Post_B_1의 썸네일 (스냅샷)

        /** SCENE 8. 리포스트 생성 - 정상 (썸네일 없는 게시글) */
        // Given: UserA 토큰, Tab_A ID
        // Given: Request Body { "postId": Post_C_1_ID }
        // When: POST /api/v1/repost/{tabId}
        // Then: 201 Created
        // Then: thumbnailUrl is null 확인

        /** SCENE 9. 리포스트 생성 - 중복 생성 방지 */
        // Given: 이미 Tab_A에 Post_B_1이 리포스트 된 상태
        // When: POST /api/v1/repost/{tabId} (Body: Post_B_1)
        // Then: 409 Conflict (REPOST_TAB_AND_POST_DUPLICATED)

        /** SCENE 10. 리포스트 생성 - 존재하지 않는 게시글 */
        // Given: postId = 99999
        // When: POST ...
        // Then: 404 Not Found (POST_NOT_FOUND)

        /** SCENE 11. 리포스트 제목 수정 */
        // Given: UserA 토큰, RepostID
        // Given: Request Body { "title": "내가 바꾼 제목" }
        // When: PATCH /api/v1/repost/{repostId}
        // Then: 200 OK, title 변경 확인 (원본 Post 제목은 변하지 않아야 함)

        /** SCENE 12. 리포스트 삭제 */
        // Given: UserA 토큰, RepostID
        // When: DELETE /api/v1/repost/{repostId}
        // Then: 204 No Content
        // Then: 원본 Post는 삭제되지 않고 남아있어야 함 (데이터 무결성)

        /** SCENE 13. 예외 - 타인이 내 탭에 리포스트 생성 시도 */
        // Given: UserC 토큰, UserA의 Tab ID
        // When: POST /api/v1/repost/{tabId}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 3]. Bulk Delete via Tab (탭 삭제 시 Cascade 검증)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 탭 삭제 시 리포스트 일괄 삭제")
    class TabDeleteCascade {
        // Setup: Tab_B 생성 후, Repost 3개 추가

        /** SCENE 14. 탭 삭제 시 하위 리포스트 삭제 확인 */
        // Given: UserA 토큰, Tab_B ID
        // When: DELETE /api/v1/repost/tabs/{tabId}
        // Then: 204 No Content
        // Then: 해당 탭에 속했던 Repost ID로 상세 조회(혹은 DB 확인) 시 없어야 함
        // (API로는 Repost 단건 조회가 없으므로, 목록 조회 시 0건인지 확인)
    }

    // ========================================================================================
    // [Category 4]. Read List & Pagination (GET /api/v1/repost/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 리포스트 목록 조회 (탭별 조회)")
    class ReadReposts {
        // Setup:
        // UserA Archive (Public): Tab1(Repost 5개), Tab2(Repost 3개)
        // UserA Archive (Restricted): Tab3(Repost 2개)
        // UserA Archive (Private): Tab4(Repost 1개)

        /** SCENE 15. PUBLIC 아카이브 - 특정 탭 조회 */
        // Given: UserC(타인), Public Archive ID, Tab1 ID
        // When: GET /api/v1/repost/{archiveId}?tabId={tab1Id}&page=0&size=10
        // Then: 200 OK
        // Then: content.size=5, currentTabId=Tab1

        /** SCENE 16. PUBLIC 아카이브 - 탭 ID 미지정 (Default Tab) */
        // Given: UserC(타인), Public Archive ID
        // When: GET /api/v1/repost/{archiveId} (No tabId param)
        // Then: 200 OK
        // Then: 가장 ID가 낮은(먼저 생성된) Tab1의 데이터(5개)가 반환되어야 함
        // Then: 응답 내 tabId 필드가 Tab1 ID와 일치하는지 확인

        /** SCENE 17. RESTRICTED 아카이브 - 친구 조회 */
        // Given: UserB(친구), Restricted Archive ID
        // When: GET /api/v1/repost/{archiveId}
        // Then: 200 OK

        /** SCENE 18. RESTRICTED 아카이브 - 타인 조회 */
        // Given: UserC(타인), Restricted Archive ID
        // When: GET ...
        // Then: 403 Forbidden

        /** SCENE 19. PRIVATE 아카이브 - 타인 조회 */
        // Given: UserB(친구) or UserC(타인), Private Archive ID
        // When: GET ...
        // Then: 403 Forbidden

        /** SCENE 20. 존재하지 않는 탭 ID 요청 */
        // Given: Public Archive ID, Random Tab ID
        // When: GET ...?tabId=99999
        // Then: 404 Not Found (REPOST_TAB_NOT_FOUND)

        /** SCENE 21. 다른 아카이브의 탭 ID 요청 (Cross Archive Request) */
        // Given: Archive1 ID 요청, 근데 tabId는 Archive2의 탭 ID
        // When: GET /api/v1/repost/{archive1_Id}?tabId={archive2_Tab_Id}
        // Then: 404 Not Found (REPOST_TAB_NOT_FOUND) - 탭이 해당 아카이브 소속이 아님을 검증
    }
}