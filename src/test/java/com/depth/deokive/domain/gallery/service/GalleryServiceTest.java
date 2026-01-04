package com.depth.deokive.domain.gallery.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.entity.Gallery;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GalleryService 통합 테스트")
class GalleryServiceTest extends IntegrationTestSupport {

    @Autowired GalleryService galleryService;
    @Autowired ArchiveService archiveService;

    // Core Repositories
    @Autowired GalleryRepository galleryRepository;
    @Autowired GalleryBookRepository galleryBookRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired FileRepository fileRepository;
    @Autowired FriendMapRepository friendMapRepository;

    // Test Data
    private User userA; // Me
    private User userB; // Friend
    private User userC; // Stranger

    private Archive archiveAPublic;
    private Archive archiveARestricted;
    private Archive archiveAPrivate;
    private Archive archiveBPublic;

    private List<File> userAFiles; // UserA 소유 파일들
    private List<File> userCFiles; // UserC 소유 파일들

    @BeforeEach
    void setUp() {
        // 1. Users Setup
        userA = createTestUser("usera@test.com", "UserA");
        userB = createTestUser("userb@test.com", "UserB");
        userC = createTestUser("userc@test.com", "UserC");

        // 2. Friend Setup (A <-> B)
        friendMapRepository.save(FriendMap.builder().user(userA).friend(userB).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());
        friendMapRepository.save(FriendMap.builder().user(userB).friend(userA).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());

        // 3. Archives Setup
        setupMockUser(userA);
        archiveAPublic = createArchiveByService(userA, Visibility.PUBLIC);
        archiveARestricted = createArchiveByService(userA, Visibility.RESTRICTED);
        archiveAPrivate = createArchiveByService(userA, Visibility.PRIVATE);

        setupMockUser(userB);
        archiveBPublic = createArchiveByService(userB, Visibility.PUBLIC);

        // 4. Files Setup
        setupMockUser(userA);
        userAFiles = createFiles(userA, 20); // 20 files
        setupMockUser(userC);
        userCFiles = createFiles(userC, 5);

        SecurityContextHolder.clearContext();
    }

    private User createTestUser(String email, String nickname) {
        User user = User.builder()
                .email(email)
                .username("user_" + UUID.randomUUID())
                .nickname(nickname)
                .password("password")
                .role(Role.USER)
                .userType(UserType.COMMON)
                .isEmailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private Archive createArchiveByService(User owner, Visibility visibility) {
        setupMockUser(owner);
        UserPrincipal principal = UserPrincipal.from(owner);

        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);

        ArchiveDto.Response response = archiveService.createArchive(principal, req);
        SecurityContextHolder.clearContext();
        return archiveRepository.findById(response.getId()).orElseThrow();
    }

    private List<File> createFiles(User owner, int count) {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            File file = fileRepository.save(File.builder()
                    .filename("file_" + owner.getNickname() + "_" + uniqueId + ".jpg")
                    .s3ObjectKey("files/" + owner.getNickname() + "/" + uniqueId + ".jpg")
                    .fileSize(100L)
                    .mediaType(MediaType.IMAGE)
                    .createdBy(owner.getId())
                    .lastModifiedBy(owner.getId())
                    .build());
            files.add(file);
        }
        return files;
    }

    // ========================================================================================
    // [Category 1]: Create
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Gallery")
    class Create {

        @Test
        @DisplayName("SCENE 1: 정상 케이스 (단일 파일, PUBLIC Archive)")
        void createGalleries_Single_Public() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId()));

            // When
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveAPublic.getId(), request);

            // Then
            assertThat(response.getCreatedCount()).isEqualTo(1);
            List<Gallery> galleries = galleryRepository.findAll();
            assertThat(galleries).hasSize(1);
            assertThat(galleries.get(0).getGalleryBook().getArchive().getId()).isEqualTo(archiveAPublic.getId());
            assertThat(galleries.get(0).getOriginalKey()).isEqualTo(userAFiles.get(0).getS3ObjectKey());
        }

        @Test
        @DisplayName("SCENE 2: 정상 케이스 (여러 파일, PUBLIC Archive)")
        void createGalleries_Multiple_Public() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            List<Long> fileIds = userAFiles.subList(0, 5).stream().map(File::getId).toList();
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(fileIds);

            // When
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveAPublic.getId(), request);

            // Then
            assertThat(response.getCreatedCount()).isEqualTo(5);
            List<Gallery> galleries = galleryRepository.findAll();
            assertThat(galleries).hasSize(5);
            assertThat(galleries).allMatch(g -> g.getGalleryBook().getArchive().getId().equals(archiveAPublic.getId()));
        }

        @Test
        @DisplayName("SCENE 3: 정상 케이스 (RESTRICTED Archive)")
        void createGalleries_Restricted() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId()));

            // When
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveARestricted.getId(), request);

            // Then
            assertThat(response.getCreatedCount()).isEqualTo(1);
            assertThat(galleryRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 4: 정상 케이스 (PRIVATE Archive)")
        void createGalleries_Private() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId()));

            // When
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveAPrivate.getId(), request);

            // Then
            assertThat(response.getCreatedCount()).isEqualTo(1);
            assertThat(galleryRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 5~9: 권한 예외 케이스")
        void createGalleries_Forbidden() {
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId()));

            // 5: Stranger -> Public Archive (Forbidden: 생성은 주인만)
            assertThatThrownBy(() -> galleryService.createGalleries(UserPrincipal.from(userC), archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 6: Stranger -> Restricted Archive
            assertThatThrownBy(() -> galleryService.createGalleries(UserPrincipal.from(userC), archiveARestricted.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 7: Stranger -> Private Archive
            assertThatThrownBy(() -> galleryService.createGalleries(UserPrincipal.from(userC), archiveAPrivate.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 8: Friend -> Restricted Archive (생성은 주인만)
            assertThatThrownBy(() -> galleryService.createGalleries(UserPrincipal.from(userB), archiveARestricted.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 9: Anonymous -> Public Archive
            assertThatThrownBy(() -> galleryService.createGalleries(null, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_UNAUTHORIZED);
        }

        @Test
        @DisplayName("SCENE 10: 다른 사용자의 파일 사용 시도")
        void createGalleries_IDOR() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userCFiles.get(0).getId())); // UserC의 파일

            // When & Then (validateFileOwners에서 필터링되어 개수 불일치 -> FILE_NOT_FOUND)
            assertThatThrownBy(() -> galleryService.createGalleries(principal, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 11: 파일 개수 불일치 (존재하지 않는 파일)")
        void createGalleries_FileNotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId(), 99999L));

            // When & Then
            assertThatThrownBy(() -> galleryService.createGalleries(principal, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 12: 중복된 파일 ID")
        void createGalleries_DuplicateFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            // 중복 요청: [File1, File1]
            request.setFileIds(List.of(userAFiles.get(0).getId(), userAFiles.get(0).getId()));

            // When & Then
            // 서비스 로직: files.size() != request.getFileIds().size()
            // validateFileOwners가 중복을 제거하여 1개를 반환한다면, 1 != 2가 되어 에러 발생
            assertThatThrownBy(() -> galleryService.createGalleries(principal, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 13: 빈 파일 목록")
        void createGalleries_EmptyFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(new ArrayList<>());

            // When
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveAPublic.getId(), request);

            // Then
            assertThat(response.getCreatedCount()).isZero();
        }

        @Test
        @DisplayName("SCENE 14: 존재하지 않는 Archive")
        void createGalleries_ArchiveNotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest();
            request.setFileIds(List.of(userAFiles.get(0).getId()));

            // When & Then
            assertThatThrownBy(() -> galleryService.createGalleries(principal, 99999L, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 2]: Read-Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Gallery")
    class Read {
        @BeforeEach
        void initGalleries() {
            // Create 10 galleries for each archive
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            // Public Archive (Files 0~9)
            GalleryDto.CreateRequest reqPublic = new GalleryDto.CreateRequest();
            reqPublic.setFileIds(userAFiles.subList(0, 10).stream().map(File::getId).toList());
            galleryService.createGalleries(principal, archiveAPublic.getId(), reqPublic);

            // Restricted Archive (Files 0~9 재사용 - 갤러리 정책상 허용된다고 가정)
            galleryService.createGalleries(principal, archiveARestricted.getId(), reqPublic);

            // Private Archive (Files 0~9 재사용)
            galleryService.createGalleries(principal, archiveAPrivate.getId(), reqPublic);

            flushAndClear(); // [중요] DB 반영 및 영속성 컨텍스트 초기화
        }

        @Test
        @DisplayName("SCENE 8~11: PUBLIC Archive (본인, 타인, 친구, 비회원)")
        void getGalleries_Public() {
            GalleryDto.GalleryPageRequest req = new GalleryDto.GalleryPageRequest();
            req.setPage(0); req.setSize(20);

            // 8: Owner
            PageDto.PageListResponse<GalleryDto.Response> resOwner = galleryService.getGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable());
            assertThat(resOwner.getPage().getTotalElements()).isEqualTo(10);

            // 9: Stranger
            PageDto.PageListResponse<GalleryDto.Response> resStranger = galleryService.getGalleries(UserPrincipal.from(userC), archiveAPublic.getId(), req.toPageable());
            assertThat(resStranger.getPage().getTotalElements()).isEqualTo(10);

            // 10: Friend
            PageDto.PageListResponse<GalleryDto.Response> resFriend = galleryService.getGalleries(UserPrincipal.from(userB), archiveAPublic.getId(), req.toPageable());
            assertThat(resFriend.getPage().getTotalElements()).isEqualTo(10);

            // 11: Anonymous
            PageDto.PageListResponse<GalleryDto.Response> resAnon = galleryService.getGalleries(null, archiveAPublic.getId(), req.toPageable());
            assertThat(resAnon.getPage().getTotalElements()).isEqualTo(10);
        }

        @Test
        @DisplayName("SCENE 12~15: RESTRICTED Archive")
        void getGalleries_Restricted() {
            GalleryDto.GalleryPageRequest req = new GalleryDto.GalleryPageRequest();

            // 12: Owner -> OK
            assertThat(galleryService.getGalleries(UserPrincipal.from(userA), archiveARestricted.getId(), req.toPageable()).getPage().getTotalElements()).isEqualTo(10);

            // 13: Friend -> OK
            assertThat(galleryService.getGalleries(UserPrincipal.from(userB), archiveARestricted.getId(), req.toPageable()).getPage().getTotalElements()).isEqualTo(10);

            // 14: Stranger -> Fail
            assertThatThrownBy(() -> galleryService.getGalleries(UserPrincipal.from(userC), archiveARestricted.getId(), req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 15: Anonymous -> Fail
            assertThatThrownBy(() -> galleryService.getGalleries(null, archiveARestricted.getId(), req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 16~19: PRIVATE Archive")
        void getGalleries_Private() {
            GalleryDto.GalleryPageRequest req = new GalleryDto.GalleryPageRequest();

            // 16: Owner -> OK
            assertThat(galleryService.getGalleries(UserPrincipal.from(userA), archiveAPrivate.getId(), req.toPageable()).getPage().getTotalElements()).isEqualTo(10);

            // 17~19: Others -> Fail
            assertThatThrownBy(() -> galleryService.getGalleries(UserPrincipal.from(userC), archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> galleryService.getGalleries(UserPrincipal.from(userB), archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> galleryService.getGalleries(null, archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 20~23: Edge Cases")
        void getGalleries_Edge() {
            GalleryDto.GalleryPageRequest req = new GalleryDto.GalleryPageRequest();

            // 20: Not Found Archive
            assertThatThrownBy(() -> galleryService.getGalleries(UserPrincipal.from(userA), 99999L, req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);

            // 21: Empty Result
            Archive newArchive = createArchiveByService(userA, Visibility.PUBLIC);
            assertThat(galleryService.getGalleries(UserPrincipal.from(userA), newArchive.getId(), req.toPageable()).getPage().getTotalElements()).isZero();

            // 22: Page Out of Range
            req.setPage(1000);
            assertThatThrownBy(() -> galleryService.getGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);

            // 23: Pagination Check
            req.setPage(0);
            req.setSize(5);
            PageDto.PageListResponse<GalleryDto.Response> res = galleryService.getGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable());
            assertThat(res.getContent()).hasSize(5);
            assertThat(res.getPage().getTotalElements()).isEqualTo(10);
        }
    }

    // ========================================================================================
    // [Category 3]: Update Book Title
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Update GalleryBook Title")
    class UpdateBookTitle {
        @Test
        @DisplayName("SCENE 24: 정상 케이스")
        void updateGalleryBookTitle_Normal() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.UpdateTitleRequest req = new GalleryDto.UpdateTitleRequest("New Title");

            // When
            GalleryDto.UpdateTitleResponse res = galleryService.updateGalleryBookTitle(principal, archiveAPublic.getId(), req);

            // Then
            assertThat(res.getUpdatedTitle()).isEqualTo("New Title");
            GalleryBook book = galleryBookRepository.findById(archiveAPublic.getId()).orElseThrow();
            assertThat(book.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("SCENE 25: 타인 수정 시도")
        void updateGalleryBookTitle_Forbidden() {
            GalleryDto.UpdateTitleRequest req = new GalleryDto.UpdateTitleRequest("Hacked");

            assertThatThrownBy(() -> galleryService.updateGalleryBookTitle(UserPrincipal.from(userC), archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 26: 존재하지 않는 GalleryBook")
        void updateGalleryBookTitle_NotFound() {
            setupMockUser(userA);
            GalleryDto.UpdateTitleRequest req = new GalleryDto.UpdateTitleRequest("Ghost");

            assertThatThrownBy(() -> galleryService.updateGalleryBookTitle(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 4]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Delete Gallery")
    class Delete {
        private List<Gallery> galleries;

        @BeforeEach
        void init() {
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            GalleryDto.CreateRequest req = new GalleryDto.CreateRequest();
            req.setFileIds(userAFiles.subList(0, 10).stream().map(File::getId).toList());
            galleryService.createGalleries(principal, archiveAPublic.getId(), req);

            flushAndClear(); // DB 반영

            galleries = galleryRepository.findAll();
        }

        @Test
        @DisplayName("SCENE 27: 정상 케이스 (단일 삭제)")
        void deleteGalleries_Single() {
            // Given
            setupMockUser(userA);
            GalleryDto.DeleteRequest req = new GalleryDto.DeleteRequest();
            Long targetId = galleries.get(0).getId();
            req.setGalleryIds(List.of(targetId));

            // When
            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            flushAndClear(); // [중요] 벌크 삭제 후 컨텍스트 정리

            // Then
            assertThat(galleryRepository.existsById(targetId)).isFalse();
            assertThat(galleryRepository.count()).isEqualTo(9);
        }

        @Test
        @DisplayName("SCENE 28: 정상 케이스 (여러 개 삭제)")
        void deleteGalleries_Multiple() {
            // Given
            setupMockUser(userA);
            GalleryDto.DeleteRequest req = new GalleryDto.DeleteRequest();
            List<Long> targetIds = List.of(galleries.get(0).getId(), galleries.get(1).getId(), galleries.get(2).getId());
            req.setGalleryIds(targetIds);

            // When
            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            flushAndClear();

            // Then
            assertThat(galleryRepository.findAllById(targetIds)).isEmpty();
            assertThat(galleryRepository.count()).isEqualTo(7);
        }

        @Test
        @DisplayName("SCENE 29: 정상 케이스 (전체 삭제)")
        void deleteGalleries_All() {
            // Given
            setupMockUser(userA);
            GalleryDto.DeleteRequest req = new GalleryDto.DeleteRequest();
            List<Long> allIds = galleries.stream().map(Gallery::getId).toList();
            req.setGalleryIds(allIds);

            // When
            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            flushAndClear();

            // Then
            assertThat(galleryRepository.count()).isZero();
            assertThat(archiveRepository.existsById(archiveAPublic.getId())).isTrue();
        }

        @Test
        @DisplayName("SCENE 30~34: 권한 예외 케이스")
        void deleteGalleries_Forbidden() {
            GalleryDto.DeleteRequest req = new GalleryDto.DeleteRequest();
            req.setGalleryIds(List.of(galleries.get(0).getId()));

            // 30, 31, 32: Stranger -> Public, Restricted, Private (Forbidden)
            assertThatThrownBy(() -> galleryService.deleteGalleries(UserPrincipal.from(userC), archiveAPublic.getId(), req)).isInstanceOf(RestException.class);

            // 33: Friend -> Restricted (Forbidden)
            assertThatThrownBy(() -> galleryService.deleteGalleries(UserPrincipal.from(userB), archiveAPublic.getId(), req)).isInstanceOf(RestException.class);

            // 34: Anonymous -> Public (Unauthorized)
            assertThatThrownBy(() -> galleryService.deleteGalleries(null, archiveAPublic.getId(), req)).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 35: 존재하지 않는 Archive")
        void deleteGalleries_ArchiveNotFound() {
            setupMockUser(userA);
            GalleryDto.DeleteRequest req = new GalleryDto.DeleteRequest();
            req.setGalleryIds(List.of(galleries.get(0).getId()));

            assertThatThrownBy(() -> galleryService.deleteGalleries(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 36: 다른 Archive의 Gallery 삭제 시도 (Cross Archive)")
        void deleteGalleries_CrossArchive() {
            // Given: UserA creates another archive and gallery
            Archive otherArchive = createArchiveByService(userA, Visibility.PUBLIC);
            File otherFile = createFiles(userA, 1).get(0);

            GalleryDto.CreateRequest createReq = new GalleryDto.CreateRequest();
            createReq.setFileIds(List.of(otherFile.getId()));
            galleryService.createGalleries(UserPrincipal.from(userA), otherArchive.getId(), createReq);
            flushAndClear();

            Long otherGalleryId = galleryRepository.findAll().stream()
                    .filter(g -> g.getGalleryBook().getArchive().getId().equals(otherArchive.getId()))
                    .findFirst().orElseThrow().getId();

            // When: archiveAPublic에 대해 otherGalleryId 삭제 요청
            GalleryDto.DeleteRequest delReq = new GalleryDto.DeleteRequest();
            delReq.setGalleryIds(List.of(otherGalleryId));

            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), delReq);
            flushAndClear();

            // Then: Cross Archive Delete는 발생하지 않아야 함 (WHERE 조건에 archiveId가 포함되어 있으므로)
            assertThat(galleryRepository.existsById(otherGalleryId)).isTrue();
        }

        @Test
        @DisplayName("SCENE 37~38: 존재하지 않는 Gallery ID / 빈 목록")
        void deleteGalleries_Edge() {
            setupMockUser(userA);

            // 37: Non-existent ID -> 예외 없이 무시됨 (delete query 실행되나 affected row 0)
            GalleryDto.DeleteRequest req1 = new GalleryDto.DeleteRequest();
            req1.setGalleryIds(List.of(99999L));
            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req1);

            // 38: Empty List
            GalleryDto.DeleteRequest req2 = new GalleryDto.DeleteRequest();
            req2.setGalleryIds(new ArrayList<>());
            galleryService.deleteGalleries(UserPrincipal.from(userA), archiveAPublic.getId(), req2);
        }
    }
}
