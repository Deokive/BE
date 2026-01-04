package com.depth.deokive.common.api.diary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Diary 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis, MailHog, S3(Mock/Local)
 * 2. 공통 유틸 (AuthSteps, ArchiveSteps, FileSteps, FriendSteps):
 * - 회원가입/로그인, 아카이브 생성, 파일 업로드, 친구 맺기 등의 선행 작업 수행
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Me): 다이어리 주인
 * - UserB (Friend): UserA의 친구 (Restricted 조회 가능)
 * - UserC (Stranger): 타인 (Public만 조회 가능)
 * - Anonymous: 비회원
 *
 * ■ 테스트 데이터 (Fixtures)
 * - Archives: UserA가 생성한 Public, Restricted, Private 아카이브
 * - Files: UserA가 미리 업로드해 둔 이미지 파일 ID들 (썸네일/본문용)
 */
@DisplayName("Diary API 통합 테스트 시나리오")
class DiaryApiTest {

    /**
     * [Setup]
     * 1. AuthSteps: UserA, UserB, UserC 회원가입 및 로그인 (Token 확보)
     * 2. FriendSteps: UserA <-> UserB 친구 맺기
     * 3. ArchiveSteps: UserA의 Archive 생성 (Public, Restricted, Private) -> ID 확보
     * 4. FileSteps: UserA가 파일 3개 업로드 (File1, File2, File3) -> ID 확보
     */

    // ========================================================================================
    // [Category 1]. Create Diary (POST /api/v1/diary/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 다이어리 생성")
    class CreateDiary {

        /** SCENE 1. 정상 생성 - 파일 포함 (썸네일 지정) */
        // Given: UserA 토큰, Public Archive ID
        // Given: Request Body (title="테스트 일기", content="내용", date=2024-01-01, color="#FF5733", visibility=PUBLIC)
        // Given: Files [ {fileId: File1, role: PREVIEW, seq:0}, {fileId: File2, role: CONTENT, seq:1} ]
        // When: POST /api/v1/diary/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.id가 null이 아니고 유효한 Long 값인지 확인
        // Then: **응답 Body 검증** - response.title == "테스트 일기" 확인
        // Then: **응답 Body 검증** - response.content == "내용" 확인
        // Then: **응답 Body 검증** - response.visibility == PUBLIC 확인
        // Then: **응답 Body 검증** - response.files 리스트가 2개인지 확인
        // Then: **응답 Body 검증** - response.files[0].fileId == File1.getId() 확인
        // Then: **응답 Body 검증** - response.files[0].mediaRole == PREVIEW 확인
        // Then: **응답 Body 검증** - response.files[0].sequence == 0 확인
        // Then: **응답 Body 검증** - response.files[0].cdnUrl이 CDN URL 형식인지 검증 (https://cdn... 형식)
        // Then: **응답 Body 검증** - response.files[1].fileId == File2.getId() 확인
        // Then: **응답 Body 검증** - response.files[1].mediaRole == CONTENT 확인
        // Then: **응답 Body 검증** - response.thumbnailUrl이 File1의 CDN URL인지 확인 (PREVIEW 파일이 썸네일)
        // Then: **DB 검증** - diaryRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 title == "테스트 일기" 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 visibility == PUBLIC 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 thumbnailKey == File1.getS3ObjectKey() 확인
        // Then: **DB 검증** - diaryFileMapRepository.findAllByDiaryId(response.getId())가 2개인지 확인
        // Then: **DB 검증** - DB의 DiaryFileMap[0].sequence == 0, mediaRole == PREVIEW 확인
        // Then: **DB 검증** - DB의 DiaryFileMap[1].sequence == 1, mediaRole == CONTENT 확인

        /** SCENE 2. 정상 생성 - 파일 없음 */
        // Given: UserA 토큰, Restricted Archive ID
        // Given: Request Body (title="파일 없는 일기", files=null 혹은 empty)
        // When: POST /api/v1/diary/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.title == "파일 없는 일기" 확인
        // Then: **응답 Body 검증** - response.files가 null이거나 빈 리스트인지 확인
        // Then: **응답 Body 검증** - response.thumbnailUrl == null 확인
        // Then: **DB 검증** - diaryRepository.findById(response.getId())로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 thumbnailKey == null 확인
        // Then: **DB 검증** - diaryFileMapRepository.findAllByDiaryId(response.getId())가 빈 리스트인지 확인

        /** SCENE 3. 정상 생성 - Private 다이어리 */
        // Given: UserA 토큰, Private Archive ID, visibility=PRIVATE
        // When: POST /api/v1/diary/{archiveId}
        // Then: 201 Created
        // Then: 응답의 visibility=PRIVATE 확인
        // Then: DB에서 Diary 엔티티의 visibility 필드가 PRIVATE인지 확인

        /** SCENE 4. 예외 - 존재하지 않는 아카이브 */
        // Given: UserA 토큰, random archiveId
        // When: POST /api/v1/diary/{archiveId}
        // Then: 404 Not Found (ARCHIVE_NOT_FOUND)

        /** SCENE 5. 예외 - 타인의 아카이브에 생성 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // When: POST /api/v1/diary/{archiveId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN)

        /** SCENE 6. 예외 - IDOR (내 다이어리에 남의 파일 첨부) */
        // Given: UserA 토큰, UserC가 업로드한 File ID 사용
        // When: POST /api/v1/diary/{archiveId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN) - FileService 검증 로직 동작 확인

        /** SCENE 7. 예외 - 필수값 누락 (Validation) */
        // Given: title="", recordedAt=null 등
        // When: POST /api/v1/diary/{archiveId}
        // Then: 400 Bad Request
    }

    // ========================================================================================
    // [Category 2]. Read Detail (GET /api/v1/diary/{diaryId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 다이어리 상세 조회 (권한/공개범위)")
    class ReadDiary {
        // Setup: UserA가 Archive Visibility × Diary Visibility 조합별로 다이어리 생성
        // - Diary_PubArchive_PubDiary (Public Archive + Public Diary)
        // - Diary_PubArchive_ResDiary (Public Archive + Restricted Diary)
        // - Diary_PubArchive_PriDiary (Public Archive + Private Diary)
        // - Diary_ResArchive_PubDiary (Restricted Archive + Public Diary)
        // - Diary_ResArchive_ResDiary (Restricted Archive + Restricted Diary)
        // - Diary_ResArchive_PriDiary (Restricted Archive + Private Diary)
        // - Diary_PriArchive_PubDiary (Private Archive + Public Diary)
        // - Diary_PriArchive_ResDiary (Private Archive + Restricted Diary)
        // - Diary_PriArchive_PriDiary (Private Archive + Private Diary)

        // === [ PUBLIC Archive + PUBLIC Diary ] ===
        /** SCENE 8. PUBLIC Archive + PUBLIC Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PubArchive_PubDiary ID (파일 포함)
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK
        // Then: 응답의 모든 필드(id, title, content, date, color, visibility 등) 검증
        // Then: 응답의 files 리스트가 올바르게 포함되었는지 확인
        // Then: 응답의 files 각 항목의 cdnUrl이 CDN URL 형식인지 검증 (https://cdn... 형식)
        // Then: 응답의 thumbnailUrl이 CDN URL 형식인지 검증 (파일이 있는 경우, https://cdn... 형식)

        /** SCENE 9. PUBLIC Archive + PUBLIC Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_PubArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 10. PUBLIC Archive + PUBLIC Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PubArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 11. PUBLIC Archive + PUBLIC Diary - 비회원 */
        // Given: 토큰 없음, Diary_PubArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        // === [ PUBLIC Archive + RESTRICTED Diary ] ===
        /** SCENE 12. PUBLIC Archive + RESTRICTED Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PubArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 13. PUBLIC Archive + RESTRICTED Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PubArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 14. PUBLIC Archive + RESTRICTED Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_PubArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Diary 레벨 권한 체크)

        /** SCENE 15. PUBLIC Archive + RESTRICTED Diary - 비회원 */
        // Given: 토큰 없음, Diary_PubArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden

        // === [ PUBLIC Archive + PRIVATE Diary ] ===
        /** SCENE 16. PUBLIC Archive + PRIVATE Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PubArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 17. PUBLIC Archive + PRIVATE Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PubArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Diary 레벨 권한 체크)

        /** SCENE 18. PUBLIC Archive + PRIVATE Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_PubArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden

        // === [ RESTRICTED Archive + PUBLIC Diary ] ===
        /** SCENE 19. RESTRICTED Archive + PUBLIC Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_ResArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 20. RESTRICTED Archive + PUBLIC Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_ResArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK (Archive 레벨 통과, Diary는 PUBLIC이므로 통과)

        /** SCENE 21. RESTRICTED Archive + PUBLIC Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_ResArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        // === [ RESTRICTED Archive + RESTRICTED Diary ] ===
        /** SCENE 22. RESTRICTED Archive + RESTRICTED Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_ResArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 23. RESTRICTED Archive + RESTRICTED Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_ResArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK (Archive 레벨 통과, Diary도 RESTRICTED이므로 친구 통과)

        /** SCENE 24. RESTRICTED Archive + RESTRICTED Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_ResArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        // === [ RESTRICTED Archive + PRIVATE Diary ] ===
        /** SCENE 25. RESTRICTED Archive + PRIVATE Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_ResArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 26. RESTRICTED Archive + PRIVATE Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_ResArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨은 통과하지만 Diary 레벨에서 차단)

        // === [ PRIVATE Archive + PUBLIC Diary ] ===
        /** SCENE 27. PRIVATE Archive + PUBLIC Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PriArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 28. PRIVATE Archive + PUBLIC Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PriArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크 - PRIVATE은 친구도 불가)

        /** SCENE 29. PRIVATE Archive + PUBLIC Diary - 타인(UserC) */
        // Given: UserC 토큰, Diary_PriArchive_PubDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        // === [ PRIVATE Archive + RESTRICTED Diary ] ===
        /** SCENE 30. PRIVATE Archive + RESTRICTED Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PriArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 31. PRIVATE Archive + RESTRICTED Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PriArchive_ResDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        // === [ PRIVATE Archive + PRIVATE Diary ] ===
        /** SCENE 32. PRIVATE Archive + PRIVATE Diary - 본인(UserA) */
        // Given: UserA 토큰, Diary_PriArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 200 OK

        /** SCENE 33. PRIVATE Archive + PRIVATE Diary - 친구(UserB) */
        // Given: UserB 토큰, Diary_PriArchive_PriDiary ID
        // When: GET /api/v1/diary/{id}
        // Then: 403 Forbidden (Archive 레벨 권한 체크)

        /** SCENE 34. 존재하지 않는 다이어리 */
        // When: GET /api/v1/diary/99999
        // Then: 404 Not Found (DIARY_NOT_FOUND)

        /** SCENE 13. 존재하지 않는 다이어리 */
        // When: GET /api/v1/diary/99999
        // Then: 404 Not Found (DIARY_NOT_FOUND)
    }

    // ========================================================================================
    // [Category 3]. Update Diary (PATCH /api/v1/diary/{diaryId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 다이어리 수정")
    class UpdateDiary {
        // Setup: Diary 생성 (파일 포함)

        /** SCENE 14. 정상 수정 - 내용 및 공개범위 변경 */
        // Given: UserA 토큰, Diary ID (기존 title="원본", visibility=PUBLIC)
        // Given: Request Body (title="수정된 제목", visibility=PRIVATE)
        // When: PATCH /api/v1/diary/{diaryId}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.title == "수정된 제목" 확인
        // Then: **응답 Body 검증** - response.visibility == PRIVATE 확인
        // Then: **응답 Body 검증** - response.id가 변경되지 않았는지 확인 (동일한 ID)
        // Then: **DB 검증** - diaryRepository.findById(diaryId)로 조회 시 엔티티가 존재하는지 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 title == "수정된 제목" 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 visibility == PRIVATE 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 다른 필드들(생성일 등)은 변경되지 않았는지 확인

        /** SCENE 15. 정상 수정 - 파일 전체 교체 */
        // Given: UserA 토큰, Diary ID (기존 File1, File2 연결됨, thumbnailKey=File1)
        // Given: Request Body (files=[ {fileId: File3, role: PREVIEW, seq:0} ])
        // When: PATCH /api/v1/diary/{diaryId}
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.files 리스트가 1개인지 확인
        // Then: **응답 Body 검증** - response.files[0].fileId == File3.getId() 확인
        // Then: **응답 Body 검증** - response.files에 File1, File2가 포함되지 않았는지 확인
        // Then: **응답 Body 검증** - response.thumbnailUrl이 File3의 CDN URL인지 확인
        // Then: **DB 검증** - diaryFileMapRepository.findAllByDiaryId(diaryId)가 1개인지 확인
        // Then: **DB 검증** - DB의 DiaryFileMap에 File1, File2가 연결되지 않았는지 확인 (삭제 확인)
        // Then: **DB 검증** - DB의 DiaryFileMap에 File3만 연결되었는지 확인
        // Then: **DB 검증** - DB의 Diary 엔티티의 thumbnailKey == File3.getS3ObjectKey() 확인

        /** SCENE 16. 정상 수정 - 파일 삭제 (빈 리스트 전송) */
        // Given: UserA 토큰, files=[] (기존 파일 있음)
        // When: PATCH /api/v1/diary/{id}
        // Then: 200 OK, 응답의 files 리스트가 비어있는지 확인
        // Then: 응답의 thumbnailUrl이 null인지 확인
        // Then: DB에서 모든 DiaryFileMap이 삭제되었는지 확인
        // Then: DB에서 Diary 엔티티의 thumbnailKey가 null인지 확인

        /** SCENE 17. 예외 - 타인이 수정 시도 */
        // Given: UserB(친구) 토큰
        // When: PATCH /api/v1/diary/{id}
        // Then: 403 Forbidden

        /** SCENE 18. 예외 - 존재하지 않는 다이어리 */
        // Given: Random ID
        // Then: 404 Not Found
    }

    // ========================================================================================
    // [Category 4]. Delete Diary (DELETE /api/v1/diary/{diaryId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 다이어리 삭제")
    class DeleteDiary {
        // Setup: Diary 생성

        /** SCENE 19. 정상 삭제 - 본인 */
        // Given: UserA 토큰, Diary ID (파일 포함: File1, File2)
        // When: DELETE /api/v1/diary/{diaryId}
        // Then: 204 No Content (응답 Body 없음)
        // Then: **재조회 검증** - GET /api/v1/diary/{diaryId} 호출 시 404 Not Found 확인
        // Then: **DB 검증** - diaryRepository.findById(diaryId).isPresent() == false 확인
        // Then: **DB 검증** - diaryFileMapRepository.findAllByDiaryId(diaryId)가 빈 리스트인지 확인
        // Then: **DB 검증** - fileRepository.findById(File1.getId())로 조회 시 File1 엔티티가 여전히 존재하는지 확인 (재사용 가능)
        // Then: **DB 검증** - fileRepository.findById(File2.getId())로 조회 시 File2 엔티티가 여전히 존재하는지 확인

        /** SCENE 20. 예외 - 타인이 삭제 시도 */
        // Given: UserC 토큰
        // When: DELETE /api/v1/diary/{id}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 5]. Update DiaryBook Title (PATCH /api/v1/diary/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] 다이어리북(폴더) 제목 수정")
    class UpdateBookTitle {

        /** SCENE 21. 정상 수정 */
        // Given: UserA 토큰, Archive ID, 새로운 title="2025 기록"
        // When: PATCH /api/v1/diary/{archiveId} (Request Body: title)
        // Then: 200 OK, updatedTitle 확인

        /** SCENE 22. 예외 - 타인이 수정 시도 */
        // Given: UserB 토큰, UserA Archive ID
        // When: PATCH /api/v1/diary/{archiveId}
        // Then: 403 Forbidden
    }

    // ========================================================================================
    // [Category 6]. Pagination (GET /api/v1/diary/book/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] 다이어리 목록 조회 (페이지네이션)")
    class Pagination {
        // Setup:
        // UserA Archive에 다이어리 10개 생성
        // - 5개 Public, 3개 Restricted, 2개 Private

        /** SCENE 23. 본인 조회 (Owner View) */
        // Given: UserA 토큰
        // When: GET /api/v1/diary/book/{archiveId}?page=0&size=20
        // Then: 200 OK
        // Then: 응답의 totalElements=10 확인 (Public 5 + Restricted 3 + Private 2)
        // Then: content 리스트에 모든 다이어리(10개)가 포함되어야 함
        // Then: DB 직접 조회로 모든 visibility의 다이어리가 조회되었는지 확인

        /** SCENE 24. 친구 조회 (Friend View) */
        // Given: UserB 토큰
        // When: GET /api/v1/diary/book/{archiveId}?page=0&size=20
        // Then: 200 OK
        // Then: 응답의 totalElements=8 확인 (Public 5 + Restricted 3)
        // Then: content 리스트에 Public, Restricted 다이어리만 포함 확인
        // Then: Private 다이어리(2개)는 포함되지 않아야 함
        // Then: DB 직접 조회로 PUBLIC, RESTRICTED만 필터링되었는지 확인

        /** SCENE 25. 타인 조회 (Stranger View) */
        // Given: UserC 토큰
        // When: GET /api/v1/diary/book/{archiveId}?page=0&size=20
        // Then: 200 OK
        // Then: 응답의 totalElements=5 확인
        // Then: content 리스트에 Public 다이어리(5개)만 포함 확인
        // Then: Restricted, Private 다이어리는 포함되지 않아야 함
        // Then: DB 직접 조회로 PUBLIC만 필터링되었는지 확인

        /** SCENE 26. 비회원 조회 (Anonymous View) */
        // Given: 토큰 없음
        // When: GET /api/v1/diary/book/{archiveId}
        // Then: 200 OK, totalElements=5 (Public 5만 조회됨)

        /** SCENE 27. 아카이브 접근 불가 케이스 */
        // Setup: UserA의 Private Archive 생성
        // Given: UserC 토큰
        // When: GET /api/v1/diary/book/{privateArchiveId}
        // Then: 403 Forbidden (목록 조회조차 불가능해야 함)

        /** SCENE 28. 정렬 확인 */
        // Setup: UserA Archive에 다이어리 3개 생성 (recordedAt 다르게 설정)
        //   - Diary1: recordedAt = 2024-01-01
        //   - Diary2: recordedAt = 2024-01-03
        //   - Diary3: recordedAt = 2024-01-02
        // Given: UserA 토큰
        // When: GET /api/v1/diary/book/{archiveId}?sort=recordedAt&direction=DESC
        // Then: 200 OK
        // Then: **실제 데이터 순서 검증** - content.get(0).getRecordedAt() > content.get(1).getRecordedAt() 확인
        // Then: content.get(0).getRecordedAt() == 2024-01-03 확인 (가장 최신)
        // Then: content.get(1).getRecordedAt() == 2024-01-02 확인
        // Then: content.get(2).getRecordedAt() == 2024-01-01 확인 (가장 오래됨)
        // When: GET ...?sort=recordedAt&direction=ASC
        // Then: **실제 데이터 순서 검증** - content.get(0).getRecordedAt() < content.get(1).getRecordedAt() 확인
        // Then: content.get(0).getRecordedAt() == 2024-01-01 확인 (가장 오래됨)
        // Then: content.get(2).getRecordedAt() == 2024-01-03 확인 (가장 최신)
    }
}