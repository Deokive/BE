package com.depth.deokive.common.api.gallery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * [ Gallery 도메인 API 레벨 E2E 테스트 시나리오 ]
 *
 * ■ 사전 조건 (Prerequisites)
 * 1. 환경: SpringBootTest (RandomPort), Redis, MailHog, S3(Mock)
 * 2. 공통 유틸 (AuthSteps, ArchiveSteps, FileSteps, FriendSteps):
 * - 회원가입/로그인, 아카이브 생성, 파일 업로드(S3 Multipart), 친구 맺기
 *
 * ■ 테스트 액터 (Actors)
 * - UserA (Me): 갤러리 주인
 * - UserB (Friend): UserA의 친구
 * - UserC (Stranger): 타인
 * - Anonymous: 비회원
 *
 * ■ 주요 검증 포인트
 * - 다중 파일 업로드 시 정합성 및 IDOR (내 파일만 업로드 가능한지) 검증
 * - 다중 삭제 시 Cross-Archive 삭제 방지 검증 (내 아카이브에 속한 것만 삭제되는지)
 * - 아카이브 Visibility(Public/Restricted/Private)에 따른 조회 권한 검증
 */
@DisplayName("Gallery API 통합 테스트 시나리오")
class GalleryApiTest {

    /**
     * [Setup]
     * 1. AuthSteps: UserA, UserB, UserC 토큰 확보
     * 2. FriendSteps: UserA <-> UserB 친구 설정
     * 3. ArchiveSteps: UserA의 Public, Restricted, Private 아카이브 생성 -> ID 확보
     * 4. FileSteps:
     * - UserA가 파일 5개 업로드 (FileA_1 ~ FileA_5)
     * - UserC가 파일 1개 업로드 (FileC_1) -> IDOR 테스트용
     */

    // ========================================================================================
    // [Category 1]. Create Gallery (POST /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] 갤러리 이미지 등록 (Bulk Create)")
    class CreateGallery {

        /** SCENE 1. 정상 등록 - 단일 파일 */
        // Given: UserA 토큰, Public Archive ID
        // Given: Request Body { "fileIds": [FileA_1] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.createdCount == 1 확인
        // Then: **DB 검증** - galleryRepository.countByArchiveId(archiveId)가 1 증가했는지 확인
        // Then: **DB 검증** - galleryRepository.findByArchiveId(archiveId)로 조회 시 Gallery 엔티티가 1개 존재하는지 확인
        // Then: **DB 검증** - DB의 Gallery 엔티티의 file.id == FileA_1.getId() 확인
        // Then: **DB 검증** - DB의 Gallery 엔티티의 archive.id == archiveId 확인

        /** SCENE 2. 정상 등록 - 다중 파일 */
        // Given: UserA 토큰, Public Archive ID
        // Given: Request Body { "fileIds": [FileA_2, FileA_3, FileA_4] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 201 Created
        // Then: **응답 Body 검증** - response.createdCount == 3 확인
        // Then: **DB 검증** - galleryRepository.countByArchiveId(archiveId)가 3 증가했는지 확인
        // Then: **DB 검증** - galleryRepository.findByArchiveId(archiveId)로 조회 시 Gallery 엔티티가 3개 존재하는지 확인
        // Then: **DB 검증** - DB의 Gallery 엔티티들의 file.id가 [FileA_2, FileA_3, FileA_4]와 일치하는지 확인
        // Then: **DB 검증** - DB의 Gallery 엔티티들이 올바른 순서로 저장되었는지 확인

        /** SCENE 3. 정상 등록 - Private 아카이브 */
        // Given: UserA 토큰, Private Archive ID
        // Given: Request Body { "fileIds": [FileA_5] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 201 Created

        /** SCENE 4. 예외 - IDOR (타인의 파일로 등록 시도) */
        // Given: UserA 토큰, UserC가 업로드한 FileC_1 ID 사용
        // Given: Request Body { "fileIds": [FileC_1] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN) - FileService.validateFileOwners 검증

        /** SCENE 5. 예외 - 타인의 아카이브에 등록 시도 */
        // Given: UserC 토큰, UserA의 Archive ID
        // Given: Request Body { "fileIds": [FileC_1] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 403 Forbidden (AUTH_FORBIDDEN)

        /** SCENE 6. 예외 - 존재하지 않는 파일 ID */
        // Given: UserA 토큰
        // Given: Request Body { "fileIds": [99999] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 404 Not Found (FILE_NOT_FOUND)

        /** SCENE 7. 예외 - 빈 리스트 요청 */
        // Given: Request Body { "fileIds": [] }
        // When: POST /api/v1/gallery/{archiveId}
        // Then: 400 Bad Request (@NotEmpty 검증)
    }

    // ========================================================================================
    // [Category 2]. Read Gallery List (GET /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] 갤러리 목록 조회 (권한/페이지네이션)")
    class ReadGallery {
        // Setup: UserA가 Public/Restricted/Private 아카이브에 각각 이미지를 등록해 둠

        /** SCENE 8. PUBLIC 아카이브 조회 - 누구나 가능 */
        // Given: UserC(타인) 혹은 Anonymous, Public Archive ID (Gallery 5개 포함)
        // When: GET /api/v1/gallery/{publicArchiveId}?page=0&size=10
        // Then: 200 OK
        // Then: **응답 Body 검증** - response.page.totalElements == 5 확인
        // Then: **응답 Body 검증** - response.content 리스트가 5개인지 확인
        // Then: **응답 Body 검증** - response.content[0].id가 null이 아니고 유효한 Long 값인지 확인
        // Then: **응답 Body 검증** - response.content[0].thumbnailUrl이 CDN URL 형식인지 확인 (https://cdn... 형식)
        // Then: **응답 Body 검증** - response.content[0].originalUrl이 CDN URL 형식인지 확인 (https://cdn... 형식)
        // Then: **응답 Body 검증** - response.content[0].thumbnailUrl != response.content[0].originalUrl 확인 (썸네일과 원본 구분)
        // Then: **응답 Body 검증** - response.content[0].thumbnailUrl에 "thumbnails/medium/" 경로가 포함되어 있는지 확인
        // Then: **응답 Body 검증** - response.content[0].createdAt이 null이 아니고 올바른 DateTime 형식인지 확인

        /** SCENE 9. RESTRICTED 아카이브 조회 - 친구(UserB) 가능 */
        // Given: UserB 토큰
        // When: GET /api/v1/gallery/{restrictedArchiveId}
        // Then: 200 OK

        /** SCENE 10. RESTRICTED 아카이브 조회 - 타인(UserC) 불가 */
        // Given: UserC 토큰
        // When: GET /api/v1/gallery/{restrictedArchiveId}
        // Then: 403 Forbidden

        /** SCENE 11. PRIVATE 아카이브 조회 - 본인만 가능 */
        // Given: UserA 토큰 -> 200 OK
        // Given: UserB 토큰 -> 403 Forbidden

        /** SCENE 12. 페이지네이션 검증 */
        // Setup: UserA 토큰, Public Archive (이미지 5개, createdAt 다르게 설정)
        // Given: UserA 토큰
        // When: GET /api/v1/gallery/{archiveId}?page=0&size=2
        // Then: 200 OK
        // Then: 응답의 totalElements=5, totalPages, hasNext=true, hasPrevious=false 확인
        // Then: content.size=2 확인
        // When: GET ...?page=1&size=2
        // Then: content.size=2 확인 (다음 페이지)
        // Then: 응답의 hasNext=true, hasPrevious=true 확인
        // When: GET ...?page=2&size=2
        // Then: content.size=1 확인 (마지막 페이지)
        // Then: 응답의 hasNext=false, hasPrevious=true 확인

        /** SCENE 13. 존재하지 않는 아카이브 조회 */
        // When: GET /api/v1/gallery/99999
        // Then: 404 Not Found (ARCHIVE_NOT_FOUND)
    }

    // ========================================================================================
    // [Category 3]. Update GalleryBook Title (PATCH /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] 갤러리북 제목 수정")
    class UpdateGalleryBook {

        /** SCENE 14. 정상 수정 */
        // Given: UserA 토큰, Archive ID
        // Given: Request Body { "title": "제주도 여행 사진" }
        // When: PATCH /api/v1/gallery/{archiveId}
        // Then: 200 OK
        // Then: 응답 body의 updatedTitle 확인

        /** SCENE 15. 예외 - 타인이 수정 시도 */
        // Given: UserC 토큰, UserA Archive ID
        // When: PATCH /api/v1/gallery/{archiveId}
        // Then: 403 Forbidden

        /** SCENE 16. 예외 - 빈 제목 */
        // Given: Request Body { "title": "" }
        // When: PATCH ...
        // Then: 400 Bad Request
    }

    // ========================================================================================
    // [Category 4]. Delete Gallery (DELETE /api/v1/gallery/{archiveId})
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] 갤러리 이미지 삭제 (Bulk Delete)")
    class DeleteGallery {
        // Setup: 갤러리 아이템 생성 후 ID 목록 확보 (GalleryId_1, GalleryId_2, GalleryId_3)

        /** SCENE 17. 정상 삭제 - 선택 삭제 */
        // Given: UserA 토큰, Archive ID
        // Given: Request Body { "galleryIds": [GalleryId_1, GalleryId_2] }
        // When: DELETE /api/v1/gallery/{archiveId}
        // Then: 204 No Content
        // Then: 목록 조회 시 GalleryId_1, GalleryId_2는 없고 GalleryId_3만 남았는지 확인
        // Then: DB에서 GalleryId_1, GalleryId_2 엔티티가 삭제되었는지 확인
        // Then: DB에서 GalleryId_3 엔티티는 유지되는지 확인
        // Then: File 엔티티는 삭제되지 않고 유지되는지 확인 (File은 재사용 가능)

        /** SCENE 18. 예외 - 타인이 삭제 시도 */
        // Given: UserC 토큰
        // Given: Request Body { "galleryIds": [GalleryId_3] }
        // When: DELETE /api/v1/gallery/{archiveId}
        // Then: 403 Forbidden

        /** SCENE 19. 보안 검증 - Cross Archive Deletion 시도 */
        // Situation: UserA가 Archive1과 Archive2를 가지고 있음.
        //            Archive1의 API 엔드포인트로 Archive2에 속한 GalleryID를 삭제 요청함.
        // Given: UserA 토큰, Archive1 ID
        // Given: Request Body { "galleryIds": [Archive2에_속한_GalleryID] }
        // When: DELETE /api/v1/gallery/{archive1_Id}
        // Then: 204 No Content (성공 응답이지만)
        // Then: **중요 check** -> DB에서 실제 Archive2의 GalleryID가 삭제되지 않고 살아있어야 함.
        // Then: DB에서 Archive2의 GalleryID로 조회 시 엔티티가 존재하는지 확인
        // (Repository 쿼리에서 `WHERE g.id IN :ids AND g.archiveId = :archiveId` 조건 확인)

        /** SCENE 20. 예외 - 빈 리스트 삭제 요청 */
        // Given: Request Body { "galleryIds": [] }
        // When: DELETE ...
        // Then: 400 Bad Request
    }
}