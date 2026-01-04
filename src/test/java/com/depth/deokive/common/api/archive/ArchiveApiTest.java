package com.depth.deokive.common.api.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Archive 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis Container, MailHog Container 동작 중.
 * 2. 공통 유틸 (AuthSteps):
 * - 회원가입 프로세스 자동화 필수
 * 1) POST /api/v1/auth/email/send (이메일 발송 요청)
 * 2) MailHog API(/api/v2/messages)를 조회하여 최신 메일의 '인증코드' 파싱
 * 3) POST /api/v1/auth/email/verify (Redis 기반 인증코드 검증)
 * 4) POST /api/v1/auth/register (검증 완료된 이메일로 가입)
 * 5) POST /api/v1/auth/login (ATK, RTK 쿠키 발급)
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Me): 모든 권한을 가진 주인
 * - UserB (Friend): UserA와 친구 관계 (Restricted 접근 가능)
 * - UserC (Stranger): UserA와 아무 관계 없음 (Public만 접근 가능)
 * - Anonymous: 비로그인 유저 (Token 없음)
 *
 * ■ 테스트 데이터 (Fixtures) - API로 직접 생성
 * - Files: Banner로 사용할 이미지 파일 업로드 (UserA 소유, UserC 소유)
 * - FriendShip: UserA <-> UserB 친구 요청 및 수락
 */
@DisplayName("Archive API 통합 테스트 시나리오")
class ArchiveApiTest {

    /**
     * [Setup]
     * 1. AuthSteps.registerAndLogin()을 통해 UserA, UserB, UserC 생성 및 토큰(Cookie) 확보
     * 2. FriendSteps.connect(UserA, UserB)를 통해 친구 관계 형성
     * 3. FileSteps.upload()를 통해 배너용 이미지 업로드 및 fileId 확보 (UserA_FileId, UserC_FileId)
     */

    // ========================================================================================
    // [Category 1]. Create Archive (POST /api/v1/archives)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 아카이브 생성")
    class CreateArchive {

        /** SCENE 1. 정상 생성 - PUBLIC + 배너 이미지 포함 */
        // Given: UserA 토큰, 유효한 title, visibility=PUBLIC, 유효한 bannerImageId
        // When: POST /api/v1/archives 요청
        // Then: 201 Created 응답 확인
        // Then: 응답 Body에 id, title, bannerUrl, badge(NEWBIE), isOwner(true) 확인
        // Then: DB(혹은 조회 API)를 통해 하위 도메인 북(Diary, Gallery 등)이 자동 생성되었는지 확인

        /** SCENE 2. 정상 생성 - RESTRICTED + 배너 없음 */
        // Given: UserA 토큰, visibility=RESTRICTED, bannerImageId=null
        // When: POST /api/v1/archives 요청
        // Then: 201 Created 응답 확인
        // Then: bannerUrl이 null인지 확인

        /** SCENE 3. 정상 생성 - PRIVATE */
        // Given: UserA 토큰, visibility=PRIVATE
        // When: POST /api/v1/archives 요청
        // Then: 201 Created 응답 확인

        /** SCENE 4. 예외 - 필수값 누락 (제목 없음) */
        // Given: UserA 토큰, title="", visibility=PUBLIC
        // When: POST /api/v1/archives 요청
        // Then: 400 Bad Request (Validation Error) 확인

        /** SCENE 5. 예외 - IDOR (타인의 파일로 배너 설정 시도) */
        // Given: UserA 토큰, UserC가 업로드한 fileId를 bannerImageId로 설정
        // When: POST /api/v1/archives 요청
        // Then: 403 Forbidden (AUTH_FORBIDDEN) 확인 - FileService.validateFileOwner 로직 검증

        /** SCENE 6. 예외 - 존재하지 않는 파일 ID */
        // Given: UserA 토큰, 존재하지 않는 fileId(99999)
        // When: POST /api/v1/archives 요청
        // Then: 404 Not Found (FILE_NOT_FOUND) 확인
    }

    // ========================================================================================
    // [Category 2]. Read Detail (GET /api/v1/archives/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 아카이브 상세 조회 (권한 검증)")
    class ReadArchive {

        // Setup: UserA가 PUBLIC, RESTRICTED, PRIVATE 아카이브를 각각 1개씩 생성해둠

        // === [ PUBLIC Archive ] ===
        /** SCENE 7. PUBLIC 조회 - 본인(UserA) */
        // Given: UserA 토큰, ArchiveA_Public ID
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK, isOwner=true, isLiked 정보 확인

        /** SCENE 8. PUBLIC 조회 - 친구(UserB) */
        // Given: UserB 토큰, ArchiveA_Public ID
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK, isOwner=false

        /** SCENE 9. PUBLIC 조회 - 타인(UserC) */
        // Given: UserC 토큰, ArchiveA_Public ID
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK

        /** SCENE 10. PUBLIC 조회 - 비회원(No Token) */
        // Given: 토큰 없음 (헤더/쿠키 없음), ArchiveA_Public ID
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK (SecurityConfig permitAll 확인)

        // === [ RESTRICTED Archive ] ===
        /** SCENE 11. RESTRICTED 조회 - 본인(UserA) */
        // Then: 200 OK

        /** SCENE 12. RESTRICTED 조회 - 친구(UserB) */
        // Then: 200 OK (친구 관계 확인)

        /** SCENE 13. RESTRICTED 조회 - 타인(UserC) */
        // Given: UserC 토큰 (친구 아님)
        // When: GET /api/v1/archives/{id}
        // Then: 403 Forbidden (AUTH_FORBIDDEN) - ArchiveGuard 로직 검증

        /** SCENE 14. RESTRICTED 조회 - 비회원 */
        // Given: 토큰 없음
        // Then: 403 Forbidden (AUTH_FORBIDDEN) - 비회원은 친구일 수 없음

        // === [ PRIVATE Archive ] ===
        /** SCENE 15. PRIVATE 조회 - 본인(UserA) */
        // Then: 200 OK

        /** SCENE 16. PRIVATE 조회 - 친구(UserB) */
        // Then: 403 Forbidden (비공개는 친구도 불가)

        /** SCENE 17. PRIVATE 조회 - 타인(UserC) */
        // Then: 403 Forbidden

        /** SCENE 18. 존재하지 않는 아카이브 조회 */
        // Given: Random ID
        // Then: 404 Not Found (ARCHIVE_NOT_FOUND)

        /** SCENE 19. 조회수 증가 확인 */
        // Given: UserA의 아카이브
        // When: UserB가 조회 (GET 호출)
        // Then: 다시 조회했을 때 viewCount가 1 증가했는지 확인
    }

    // ========================================================================================
    // [Category 3]. Update Archive (PATCH /api/v1/archives/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 아카이브 수정")
    class UpdateArchive {

        // Setup: UserA의 Archive 생성

        /** SCENE 20. 정상 수정 - 제목 및 공개범위 변경 */
        // Given: UserA 토큰, 새로운 title, visibility=PRIVATE
        // When: PATCH /api/v1/archives/{id}
        // Then: 200 OK, 응답 데이터에 변경된 정보 반영 확인

        /** SCENE 21. 정상 수정 - 배너 이미지 교체 */
        // Given: UserA 토큰, 새로운 bannerImageId
        // When: PATCH /api/v1/archives/{id}
        // Then: 200 OK, bannerUrl 변경 확인

        /** SCENE 22. 정상 수정 - 배너 이미지 삭제 */
        // Given: UserA 토큰, bannerImageId = -1 (삭제 약속)
        // When: PATCH /api/v1/archives/{id}
        // Then: 200 OK, bannerUrl이 null인지 확인

        /** SCENE 23. 예외 - 타인(UserC)이 수정 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // When: PATCH /api/v1/archives/{id}
        // Then: 403 Forbidden (소유자만 수정 가능)

        /** SCENE 24. 예외 - 친구(UserB)가 수정 시도 */
        // Given: UserB 토큰 (친구여도 수정 불가)
        // Then: 403 Forbidden

        /** SCENE 25. 예외 - IDOR (타인의 파일로 배너 교체 시도) */
        // Given: UserA 토큰, UserC 소유의 fileId
        // When: PATCH /api/v1/archives/{id}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 4]. Delete Archive (DELETE /api/v1/archives/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 아카이브 삭제")
    class DeleteArchive {

        // Setup: UserA의 Archive 생성

        /** SCENE 26. 정상 삭제 - 본인 요청 */
        // Given: UserA 토큰
        // When: DELETE /api/v1/archives/{id}
        // Then: 204 No Content
        // Then: 해당 ID로 재조회 시 404 Not Found 확인

        /** SCENE 27. 예외 - 타인(UserC)이 삭제 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // When: DELETE /api/v1/archives/{id}
        // Then: 403 Forbidden

        /** SCENE 28. 예외 - 존재하지 않는 아카이브 삭제 */
        // Given: Random ID
        // Then: 404 Not Found
    }

    // ========================================================================================
    // [Category 5]. Feed & List (Pagination)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 피드 및 목록 조회")
    class FeedAndList {

        // Setup:
        // UserA: Public(2), Private(1)
        // UserB: Restricted(1)
        // UserC: Public(1)

        /** SCENE 29. 전역 피드 조회 (GET /api/v1/archives/feed) */
        // Given: 비회원 혹은 로그인 유저
        // When: GET /api/v1/archives/feed?sort=createdAt
        // Then: 200 OK
        // Then: UserA의 Public(2) + UserC의 Public(1)만 조회되어야 함 (총 3개)
        // Then: UserA의 Private, UserB의 Restricted는 포함되지 않아야 함

        /** SCENE 30. 유저별 조회 (GET /api/v1/archives/users/{userId}) - 본인 */
        // Given: UserA 토큰, PathVariable=UserA_ID
        // Then: UserA의 모든 아카이브(Public, Private) 조회 확인 (총 3개)

        /** SCENE 31. 유저별 조회 - 친구가 조회 */
        // Given: UserB 토큰(친구), PathVariable=UserA_ID
        // Then: UserA의 Public만 조회됨 (UserA는 Restricted가 없음) -> 총 2개
        // (만약 UserA에게 Restricted가 있었다면 그것도 보였을 것)

        /** SCENE 32. 유저별 조회 - 친구가 조회 (Restricted 포함 케이스) */
        // Given: UserA 토큰(친구), PathVariable=UserB_ID (Restricted 보유)
        // Then: UserB의 Restricted 아카이브가 조회 결과에 포함되어야 함

        /** SCENE 33. 유저별 조회 - 타인이 조회 */
        // Given: UserC 토큰, PathVariable=UserA_ID
        // Then: UserA의 Public 아카이브(2개)만 조회됨

        /** SCENE 34. 유저별 조회 - 타인이 조회 (친구공개 숨김 확인) */
        // Given: UserC 토큰, PathVariable=UserB_ID
        // Then: UserB의 Restricted 아카이브는 조회되지 않아야 함 (결과 0개)

        /** SCENE 35. 페이지네이션 및 정렬 확인 */
        // Given: sort=hotScore, direction=DESC
        // Then: 핫스코어 기준으로 정렬되어 반환되는지 확인
    }
}