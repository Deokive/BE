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
        // Given: UserA 토큰, 유효한 title="테스트 아카이브", visibility=PUBLIC, 유효한 bannerImageId
        // When: POST /api/v1/archives 요청
        // Then: 201 Created 응답 확인
        // Then: **응답 Body 검증** - response.id가 null이 아니고 유효한 Long 값인지 확인
        // Then: **응답 Body 검증** - response.title == "테스트 아카이브" 확인
        // Then: **응답 Body 검증** - response.visibility == PUBLIC 확인
        // Then: **응답 Body 검증** - response.badge == NEWBIE 확인
        // Then: **응답 Body 검증** - response.isOwner == true 확인
        // Then: **응답 Body 검증** - response.viewCount == 0 확인
        // Then: **응답 Body 검증** - response.likeCount == 0 확인
        // Then: **응답 Body 검증** - response.bannerUrl이 CDN URL 형식인지 확인 (https://cdn... 형식, null이 아님)
        // Then: **DB 검증** - archiveRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 title == "테스트 아카이브" 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 visibility == PUBLIC 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 badge == NEWBIE 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 bannerFile.id == bannerImageId 확인
        // Then: **DB 검증** - diaryBookRepository.existsById(response.getId()) == true 확인
        // Then: **DB 검증** - galleryBookRepository.existsById(response.getId()) == true 확인
        // Then: **DB 검증** - ticketBookRepository.existsById(response.getId()) == true 확인
        // Then: **DB 검증** - repostBookRepository.existsById(response.getId()) == true 확인

        /** SCENE 2. 정상 생성 - RESTRICTED + 배너 없음 */
        // Given: UserA 토큰, title="제한 아카이브", visibility=RESTRICTED, bannerImageId=null
        // When: POST /api/v1/archives 요청
        // Then: 201 Created 응답 확인
        // Then: **응답 Body 검증** - response.title == "제한 아카이브" 확인
        // Then: **응답 Body 검증** - response.visibility == RESTRICTED 확인
        // Then: **응답 Body 검증** - response.bannerUrl == null 확인
        // Then: **응답 Body 검증** - response.isOwner == true 확인
        // Then: **DB 검증** - archiveRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 bannerFile == null 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 visibility == RESTRICTED 확인
        // Then: **DB 검증** - 하위 도메인 북들이 모두 생성되었는지 확인

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
        // Given: UserA 토큰, ArchiveA_Public ID (배너 이미지 포함, 초기 viewCount=0)
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.id == ArchiveA_Public.getId() 확인
        // Then: **응답 Body 검증** - response.title이 null이 아니고 올바른 값인지 확인
        // Then: **응답 Body 검증** - response.visibility == PUBLIC 확인
        // Then: **응답 Body 검증** - response.isOwner == true 확인
        // Then: **응답 Body 검증** - response.isLiked가 boolean 값인지 확인
        // Then: **응답 Body 검증** - response.viewCount >= 0 확인 (조회 시 증가할 수 있음)
        // Then: **응답 Body 검증** - response.likeCount >= 0 확인
        // Then: **응답 Body 검증** - response.badge가 null이 아니고 올바른 Badge enum 값인지 확인
        // Then: **응답 Body 검증** - response.bannerUrl이 CDN URL 형식인지 검증 (https://cdn... 형식, null이 아닌 경우)
        // Then: **DB 검증** - archiveRepository.findById(ArchiveA_Public.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 title, visibility가 응답과 일치하는지 확인

        /** SCENE 8. PUBLIC 조회 - 친구(UserB) */
        // Given: UserB 토큰, ArchiveA_Public ID
        // When: GET /api/v1/archives/{id}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.id == ArchiveA_Public.getId() 확인
        // Then: **응답 Body 검증** - response.isOwner == false 확인
        // Then: **응답 Body 검증** - response.title, visibility, bannerUrl 등 모든 필드가 올바르게 반환되었는지 확인
        // Then: **DB 검증** - 조회 후 DB의 Archive 엔티티의 viewCount가 증가했는지 확인 (조회수 증가 검증)

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
        // Given: UserA의 아카이브 (초기 viewCount 확인)
        // When: UserB가 조회 (GET 호출)
        // Then: 200 OK
        // Then: DB에서 Archive 엔티티의 viewCount가 1 증가했는지 직접 확인
        // Then: 다시 조회 API 호출 시 응답의 viewCount도 증가했는지 확인
    }

    // ========================================================================================
    // [Category 3]. Update Archive (PATCH /api/v1/archives/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 아카이브 수정")
    class UpdateArchive {

        // Setup: UserA의 Archive 생성

        /** SCENE 20. 정상 수정 - 제목 및 공개범위 변경 */
        // Given: UserA 토큰, Archive ID, 새로운 title="수정된 제목", visibility=PRIVATE
        // When: PATCH /api/v1/archives/{id}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.title == "수정된 제목" 확인
        // Then: **응답 Body 검증** - response.visibility == PRIVATE 확인
        // Then: **응답 Body 검증** - response.id가 변경되지 않았는지 확인 (동일한 ID)
        // Then: **DB 검증** - archiveRepository.findById(archiveId)로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 title == "수정된 제목" 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 visibility == PRIVATE 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 다른 필드들(생성일 등)은 변경되지 않았는지 확인

        /** SCENE 21. 정상 수정 - 배너 이미지 교체 */
        // Given: UserA 토큰, Archive ID (기존 bannerImageId=File1), 새로운 bannerImageId=File2
        // When: PATCH /api/v1/archives/{id} (body: {bannerImageId: File2})
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.bannerUrl이 File2의 CDN URL인지 확인
        // Then: **응답 Body 검증** - response.bannerUrl이 기존 File1의 URL과 다른지 확인
        // Then: **DB 검증** - archiveRepository.findById(archiveId)로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 bannerFile.id == File2.getId() 확인
        // Then: **DB 검증** - DB의 Archive 엔티티의 bannerFile.id != File1.getId() 확인 (교체 확인)
        // Then: **DB 검증** - fileRepository.findById(File1.getId())로 조회 시 File1 엔티티가 여전히 존재하는지 확인 (재사용 가능)

        /** SCENE 22. 정상 수정 - 배너 이미지 삭제 */
        // Given: UserA 토큰, bannerImageId = -1 (삭제 약속) 또는 null
        // When: PATCH /api/v1/archives/{id}
        // Then: 200 OK, 응답의 bannerUrl이 null인지 확인
        // Then: DB에서 Archive 엔티티의 banner 필드가 null인지 확인

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
        // Given: UserA 토큰, Archive ID (하위 도메인 데이터 포함: Event 5개, Diary 3개 등)
        // When: DELETE /api/v1/archives/{archiveId}
        // Then: 204 No Content (응답 Body 없음)
        // Then: **재조회 검증** - GET /api/v1/archives/{archiveId} 호출 시 404 Not Found 확인
        // Then: **DB 검증** - archiveRepository.findById(archiveId).isPresent() == false 확인
        // Then: **DB 검증** - diaryBookRepository.existsById(archiveId) == false 확인 (Cascade 삭제)
        // Then: **DB 검증** - galleryBookRepository.existsById(archiveId) == false 확인
        // Then: **DB 검증** - ticketBookRepository.existsById(archiveId) == false 확인
        // Then: **DB 검증** - repostBookRepository.existsById(archiveId) == false 확인
        // Then: **DB 검증** - eventRepository.findAllByArchiveId(archiveId)가 빈 리스트인지 확인
        // Then: **DB 검증** - diaryRepository.findAllByArchiveId(archiveId)가 빈 리스트인지 확인
        // Then: **DB 검증** - 하위 도메인 데이터가 모두 삭제되었는지 확인

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
        // Setup: UserA Public(2), UserB Restricted(1), UserC Public(1) 생성
        // Given: 비회원 혹은 로그인 유저
        // When: GET /api/v1/archives/feed?sort=createdAt&direction=DESC
        // Then: 200 OK
        // Then: 응답의 totalElements=3 확인 (UserA Public 2 + UserC Public 1)
        // Then: content 리스트에 UserA의 Public(2) + UserC의 Public(1)만 포함되어야 함
        // Then: UserA의 Private, UserB의 Restricted는 포함되지 않아야 함
        // Then: **실제 데이터 순서 검증** - content.get(0).getCreatedAt()가 content.get(1).getCreatedAt()보다 이후인지 확인 (최신순)
        // Then: **URL 형식 검증** - content의 각 Archive의 bannerUrl이 CDN URL 형식인지 확인 (null이 아닌 경우, https://cdn... 형식)
        // When: GET /api/v1/archives/feed?sort=hotScore&direction=DESC
        // Then: **실제 데이터 순서 검증** - content.get(0).getHotScore() > content.get(1).getHotScore() 확인 (핫스코어 높은 순)

        /** SCENE 30. 유저별 조회 (GET /api/v1/archives/users/{userId}) - 본인 */
        // Given: UserA 토큰, PathVariable=UserA_ID
        // When: GET /api/v1/archives/users/{userAId}?page=0&size=10
        // Then: 200 OK
        // Then: 응답의 totalElements=3 확인 (Public 2 + Private 1)
        // Then: content 리스트에 UserA의 모든 아카이브(Public, Private) 포함 확인
        // Then: DB 직접 조회로 Public, Private 아카이브가 모두 조회되었는지 확인

        /** SCENE 31. 유저별 조회 - 친구가 조회 */
        // Given: UserB 토큰(친구), PathVariable=UserA_ID
        // When: GET /api/v1/archives/users/{userAId}
        // Then: 200 OK
        // Then: 응답의 totalElements=2 확인 (Public 2개)
        // Then: content 리스트에 UserA의 Public 아카이브만 포함 확인
        // Then: Private 아카이브는 포함되지 않아야 함
        // Then: DB 직접 조회로 PUBLIC만 필터링되었는지 확인
        // (만약 UserA에게 Restricted가 있었다면 그것도 보였을 것)

        /** SCENE 32. 유저별 조회 - 친구가 조회 (Restricted 포함 케이스) */
        // Given: UserA 토큰(친구), PathVariable=UserB_ID (Restricted 보유)
        // When: GET /api/v1/archives/users/{userBId}
        // Then: 200 OK
        // Then: 응답의 totalElements 확인 (Public + Restricted 포함)
        // Then: content 리스트에 UserB의 Restricted 아카이브가 포함되어야 함
        // Then: DB 직접 조회로 PUBLIC, RESTRICTED 모두 포함되었는지 확인

        /** SCENE 33. 유저별 조회 - 타인이 조회 */
        // Given: UserC 토큰, PathVariable=UserA_ID
        // When: GET /api/v1/archives/users/{userAId}
        // Then: 200 OK
        // Then: 응답의 totalElements=2 확인
        // Then: content 리스트에 UserA의 Public 아카이브(2개)만 포함 확인
        // Then: Private, Restricted 아카이브는 포함되지 않아야 함
        // Then: DB 직접 조회로 PUBLIC만 필터링되었는지 확인

        /** SCENE 34. 유저별 조회 - 타인이 조회 (친구공개 숨김 확인) */
        // Given: UserC 토큰, PathVariable=UserB_ID (Restricted만 보유, Public 없음)
        // When: GET /api/v1/archives/users/{userBId}
        // Then: 200 OK
        // Then: 응답의 totalElements=0 확인 (또는 content가 빈 리스트)
        // Then: UserB의 Restricted 아카이브는 조회되지 않아야 함
        // Then: DB 직접 조회로 PUBLIC만 필터링되어 결과가 0개인지 확인

        /** SCENE 35. 페이지네이션 및 정렬 확인 */
        // Setup: UserA가 Public Archive 2개 생성 (hotScore 다르게 설정)
        //   - Archive1: hotScore=50
        //   - Archive2: hotScore=100
        // Given: UserA 토큰, sort=hotScore, direction=DESC, page=0, size=2
        // When: GET /api/v1/archives/users/{userAId}?sort=hotScore&direction=DESC&page=0&size=2
        // Then: 200 OK
        // Then: 응답의 totalElements, totalPages, hasNext, hasPrevious 확인
        // Then: content.size=2 확인
        // Then: **실제 데이터 순서 검증** - content.get(0).getHotScore() > content.get(1).getHotScore() 확인
        // Then: content.get(0).getArchiveId() == Archive2.getId() 확인 (hotScore 높은 것)
        // Then: content.get(0).getHotScore() == 100 확인
        // Then: content.get(1).getHotScore() == 50 확인
        // When: GET ...?sort=hotScore&direction=ASC&page=0&size=2
        // Then: content.get(0).getHotScore() < content.get(1).getHotScore() 확인 (오름차순)
        // When: GET ...?sort=viewCount&direction=DESC&page=0&size=2
        // Then: **실제 데이터 순서 검증** - content.get(0).getViewCount() > content.get(1).getViewCount() 확인
        // When: GET ...?page=1&size=2
        // Then: 다음 페이지 데이터가 올바르게 반환되는지 확인
    }
}