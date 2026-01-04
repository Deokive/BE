package com.depth.deokive.domain.archive.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.ArchiveLike;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.repository.ArchiveLikeRepository;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.post.repository.RepostBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArchiveService 통합 테스트")
class ArchiveServiceTest extends IntegrationTestSupport {

    @Autowired ArchiveService archiveService;

    // Core Repositories
    @Autowired ArchiveRepository archiveRepository;
    @Autowired ArchiveLikeRepository archiveLikeRepository;
    @Autowired FileRepository fileRepository;
    @Autowired FriendMapRepository friendMapRepository;

    // Sub-Domain Repositories (For Cascade/Delete Verification)
    @Autowired DiaryBookRepository diaryBookRepository;
    @Autowired GalleryBookRepository galleryBookRepository;
    @Autowired TicketBookRepository ticketBookRepository;
    @Autowired RepostBookRepository repostBookRepository;

    // Test Data
    private User userA; // Me
    private User userB; // Friend
    private User userC; // Stranger

    private File bannerFileA; // Owned by A
    private File bannerFileC; // Owned by C

    @BeforeEach
    void setUp() {
        // 1. Users Setup
        userA = createTestUser("usera@test.com", "UserA");
        userB = createTestUser("userb@test.com", "UserB");
        userC = createTestUser("userc@test.com", "UserC");

        // 2. Friend Setup (A <-> B)
        friendMapRepository.save(FriendMap.builder().user(userA).friend(userB).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());
        friendMapRepository.save(FriendMap.builder().user(userB).friend(userA).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());

        // 3. File Setup (With Auditing)
        setupMockUser(userA);
        bannerFileA = fileRepository.save(File.builder().filename("bannerA.jpg").s3ObjectKey("files/bannerA.jpg").fileSize(100L).mediaType(MediaType.IMAGE).build());

        setupMockUser(userC);
        bannerFileC = fileRepository.save(File.builder().filename("bannerC.jpg").s3ObjectKey("files/bannerC.jpg").fileSize(100L).mediaType(MediaType.IMAGE).build());

        // Context Clear
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

    // ========================================================================================
    // [Category 1]: Create
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Archive")
    class Create {

        @ParameterizedTest
        @EnumSource(Visibility.class)
        @DisplayName("SCENE 1~3: 정상 케이스 (모든 Visibility, 배너 포함)")
        void createArchive_WithBanner(Visibility visibility) {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            request.setTitle("Archive " + visibility);
            request.setVisibility(visibility);
            request.setBannerImageId(bannerFileA.getId());

            // When
            ArchiveDto.Response response = archiveService.createArchive(principal, request);

            // Then
            Archive savedArchive = archiveRepository.findById(response.getId()).orElseThrow();

            // Entity Validation
            assertThat(savedArchive.getTitle()).isEqualTo(request.getTitle());
            assertThat(savedArchive.getVisibility()).isEqualTo(visibility);
            assertThat(savedArchive.getBadge()).isEqualTo(Badge.NEWBIE);
            assertThat(savedArchive.getBannerFile()).isNotNull();
            assertThat(savedArchive.getBannerFile().getId()).isEqualTo(bannerFileA.getId());

            // Sub-Books Cascade Validation
            assertThat(diaryBookRepository.existsById(savedArchive.getId())).isTrue();
            assertThat(galleryBookRepository.existsById(savedArchive.getId())).isTrue();
            assertThat(ticketBookRepository.existsById(savedArchive.getId())).isTrue();
            assertThat(repostBookRepository.existsById(savedArchive.getId())).isTrue();
            assertThat(diaryBookRepository.findById(savedArchive.getId()).get().getTitle()).contains(request.getTitle());

            // Response Validation
            assertThat(response.isOwner()).isTrue();
            assertThat(response.getViewCount()).isZero();
            assertThat(response.getLikeCount()).isZero();
            assertThat(response.getBannerUrl()).contains("bannerA.jpg");
        }

        @ParameterizedTest
        @EnumSource(Visibility.class)
        @DisplayName("SCENE 4~6: 배너 없이 생성")
        void createArchive_NoBanner(Visibility visibility) {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            request.setTitle("No Banner " + visibility);
            request.setVisibility(visibility);
            request.setBannerImageId(null);

            // When
            ArchiveDto.Response response = archiveService.createArchive(principal, request);

            // Then
            Archive savedArchive = archiveRepository.findById(response.getId()).orElseThrow();
            assertThat(savedArchive.getBannerFile()).isNull();
            assertThat(response.getBannerUrl()).isNull();
            assertThat(diaryBookRepository.existsById(savedArchive.getId())).isTrue();
        }

        @Test
        @DisplayName("SCENE 7: 존재하지 않는 사용자 (System Error or Context issue)")
        void createArchive_UserNotFound() {
            // Given: Bogus UserPrincipal (DB에 없는 ID)
            UserPrincipal bogusPrincipal = UserPrincipal.builder().userId(99999L).role(Role.USER).build();
            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            request.setTitle("Ghost");
            request.setVisibility(Visibility.PUBLIC);

            // When & Then
            assertThatThrownBy(() -> archiveService.createArchive(bogusPrincipal, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 8: IDOR - 다른 사용자의 파일을 배너로 사용 시도")
        void createArchive_IDOR() {
            // Given: UserA trying to use UserC's file
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            request.setTitle("Hacked Banner");
            request.setVisibility(Visibility.PUBLIC);
            request.setBannerImageId(bannerFileC.getId());

            // When & Then
            assertThatThrownBy(() -> archiveService.createArchive(principal, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 9: 존재하지 않는 파일 ID")
        void createArchive_FileNotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            request.setTitle("Invalid File");
            request.setVisibility(Visibility.PUBLIC);
            request.setBannerImageId(123456789L);

            // When & Then
            assertThatThrownBy(() -> archiveService.createArchive(principal, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 2]: Read
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Detail")
    class ReadDetail {
        private Archive archivePublic, archiveRestricted, archivePrivate;

        @BeforeEach
        void initArchives() {
            // Helper method uses Service logic
            archivePublic = createArchiveByService(userA, Visibility.PUBLIC, bannerFileA);
            archiveRestricted = createArchiveByService(userA, Visibility.RESTRICTED, null);
            archivePrivate = createArchiveByService(userA, Visibility.PRIVATE, null);
        }

        @Test
        @DisplayName("SCENE 10~13: 본인 조회 (PUBLIC, RESTRICTED, PRIVATE, NO_BANNER)")
        void getArchiveDetail_Owner() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // Scene 10: Public + Banner
            ArchiveDto.Response resPub = archiveService.getArchiveDetail(principal, archivePublic.getId());
            assertThat(resPub.isOwner()).isTrue();
            assertThat(resPub.getBannerUrl()).contains("bannerA");
            assertThat(resPub.getViewCount()).isEqualTo(1); // Read increases view count

            // Scene 11: Restricted
            ArchiveDto.Response resRes = archiveService.getArchiveDetail(principal, archiveRestricted.getId());
            assertThat(resRes.isOwner()).isTrue();

            // Scene 12: Private
            ArchiveDto.Response resPri = archiveService.getArchiveDetail(principal, archivePrivate.getId());
            assertThat(resPri.isOwner()).isTrue();

            // Scene 13: No Banner Check
            assertThat(resRes.getBannerUrl()).isNull();
        }

        @Test
        @DisplayName("SCENE 14~16: PUBLIC Archive (타인, 친구, 비회원)")
        void getArchiveDetail_Public() {
            // Scene 14: Stranger (UserC)
            ArchiveDto.Response resStranger = archiveService.getArchiveDetail(UserPrincipal.from(userC), archivePublic.getId());
            assertThat(resStranger.isOwner()).isFalse();
            assertThat(resStranger.getBannerUrl()).isNotNull();

            // Scene 15: Friend (UserB)
            ArchiveDto.Response resFriend = archiveService.getArchiveDetail(UserPrincipal.from(userB), archivePublic.getId());
            assertThat(resFriend.isOwner()).isFalse();

            // Scene 16: Anonymous
            ArchiveDto.Response resAnon = archiveService.getArchiveDetail(null, archivePublic.getId());
            assertThat(resAnon.isOwner()).isFalse();
        }

        @Test
        @DisplayName("SCENE 17~19: RESTRICTED Archive (친구O, 타인X, 비회원X)")
        void getArchiveDetail_Restricted() {
            // Scene 17: Friend (UserB) -> OK
            ArchiveDto.Response resFriend = archiveService.getArchiveDetail(UserPrincipal.from(userB), archiveRestricted.getId());
            assertThat(resFriend).isNotNull();

            // Scene 18: Stranger (UserC) -> Fail
            assertThatThrownBy(() -> archiveService.getArchiveDetail(UserPrincipal.from(userC), archiveRestricted.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 19: Anonymous -> Fail
            assertThatThrownBy(() -> archiveService.getArchiveDetail(null, archiveRestricted.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 20~22: PRIVATE Archive (타인X, 친구X, 비회원X)")
        void getArchiveDetail_Private() {
            // Scene 20: Stranger -> Fail
            assertThatThrownBy(() -> archiveService.getArchiveDetail(UserPrincipal.from(userC), archivePrivate.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 21: Friend -> Fail
            assertThatThrownBy(() -> archiveService.getArchiveDetail(UserPrincipal.from(userB), archivePrivate.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 22: Anonymous -> Fail
            assertThatThrownBy(() -> archiveService.getArchiveDetail(null, archivePrivate.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 23: 존재하지 않는 Archive")
        void getArchiveDetail_NotFound() {
            assertThatThrownBy(() -> archiveService.getArchiveDetail(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 24~25: 좋아요 상태 확인")
        void getArchiveDetail_LikeStatus() {
            // Setup: UserB likes UserA's Public Archive
            archiveLikeRepository.save(ArchiveLike.builder().archive(archivePublic).user(userB).build());

            // Scene 24: UserB (Liked)
            ArchiveDto.Response resLiked = archiveService.getArchiveDetail(UserPrincipal.from(userB), archivePublic.getId());
            assertThat(resLiked.isLiked()).isTrue();

            // Scene 25: UserC (Not Liked)
            ArchiveDto.Response resNotLiked = archiveService.getArchiveDetail(UserPrincipal.from(userC), archivePublic.getId());
            assertThat(resNotLiked.isLiked()).isFalse();
        }

        @Test
        @DisplayName("SCENE 26: 조회수 증가 확인")
        void getArchiveDetail_ViewCount() {
            long initialView = archivePublic.getViewCount(); // 0

            archiveService.getArchiveDetail(UserPrincipal.from(userA), archivePublic.getId());
            archiveService.getArchiveDetail(UserPrincipal.from(userB), archivePublic.getId());
            archiveService.getArchiveDetail(UserPrincipal.from(userC), archivePublic.getId());

            Archive updated = archiveRepository.findById(archivePublic.getId()).orElseThrow();
            assertThat(updated.getViewCount()).isEqualTo(initialView + 3);
        }
    }

    // ========================================================================================
    // [Category 3]: Update
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Update Archive")
    class Update {
        private Archive archive;

        @BeforeEach
        void init() {
            archive = createArchiveByService(userA, Visibility.PUBLIC, bannerFileA);
        }

        @Test
        @DisplayName("SCENE 27~29: 정상 케이스 (제목, 공개범위 수정)")
        void updateArchive_Normal() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // Scene 27: Both
            ArchiveDto.UpdateRequest req1 = new ArchiveDto.UpdateRequest();
            req1.setTitle("Updated Title");
            req1.setVisibility(Visibility.PRIVATE);
            archiveService.updateArchive(principal, archive.getId(), req1);

            Archive res1 = archiveRepository.findById(archive.getId()).get();
            assertThat(res1.getTitle()).isEqualTo("Updated Title");
            assertThat(res1.getVisibility()).isEqualTo(Visibility.PRIVATE);

            // Scene 28: Title Only
            ArchiveDto.UpdateRequest req2 = new ArchiveDto.UpdateRequest();
            req2.setTitle("Only Title");
            archiveService.updateArchive(principal, archive.getId(), req2);
            assertThat(archiveRepository.findById(archive.getId()).get().getTitle()).isEqualTo("Only Title");
            assertThat(archiveRepository.findById(archive.getId()).get().getVisibility()).isEqualTo(Visibility.PRIVATE); // Keep previous

            // Scene 29: Visibility Only
            ArchiveDto.UpdateRequest req3 = new ArchiveDto.UpdateRequest();
            req3.setVisibility(Visibility.PUBLIC);
            archiveService.updateArchive(principal, archive.getId(), req3);
            assertThat(archiveRepository.findById(archive.getId()).get().getVisibility()).isEqualTo(Visibility.PUBLIC);
        }

        @Test
        @DisplayName("SCENE 30~32: 공개범위 변경 순환")
        void updateArchive_VisibilityCycle() {
            UserPrincipal principal = UserPrincipal.from(userA);
            ArchiveDto.UpdateRequest req = new ArchiveDto.UpdateRequest();

            // 30: Public -> Restricted
            req.setVisibility(Visibility.RESTRICTED);
            archiveService.updateArchive(principal, archive.getId(), req);
            assertThat(archiveRepository.findById(archive.getId()).get().getVisibility()).isEqualTo(Visibility.RESTRICTED);

            // 31: Restricted -> Private
            req.setVisibility(Visibility.PRIVATE);
            archiveService.updateArchive(principal, archive.getId(), req);
            assertThat(archiveRepository.findById(archive.getId()).get().getVisibility()).isEqualTo(Visibility.PRIVATE);

            // 32: Private -> Public
            req.setVisibility(Visibility.PUBLIC);
            archiveService.updateArchive(principal, archive.getId(), req);
            assertThat(archiveRepository.findById(archive.getId()).get().getVisibility()).isEqualTo(Visibility.PUBLIC);
        }

        @Test
        @DisplayName("SCENE 33~36: 배너 이미지 조작")
        void updateArchive_Banner() {
            UserPrincipal principal = UserPrincipal.from(userA);

            // 35: 배너 삭제 (-1)
            ArchiveDto.UpdateRequest delReq = new ArchiveDto.UpdateRequest();
            delReq.setBannerImageId(-1L);
            archiveService.updateArchive(principal, archive.getId(), delReq);
            assertThat(archiveRepository.findById(archive.getId()).get().getBannerFile()).isNull();

            // 34: 배너 추가 (없음 -> 있음)
            ArchiveDto.UpdateRequest addReq = new ArchiveDto.UpdateRequest();
            addReq.setBannerImageId(bannerFileA.getId());
            archiveService.updateArchive(principal, archive.getId(), addReq);
            assertThat(archiveRepository.findById(archive.getId()).get().getBannerFile().getId()).isEqualTo(bannerFileA.getId());

            // 36: 배너 유지 (null)
            ArchiveDto.UpdateRequest keepReq = new ArchiveDto.UpdateRequest();
            keepReq.setBannerImageId(null);
            archiveService.updateArchive(principal, archive.getId(), keepReq);
            assertThat(archiveRepository.findById(archive.getId()).get().getBannerFile()).isNotNull();

            // 33: 배너 변경 (A -> C 파일은 IDOR라 못함, 테스트용으로 A소유 새 파일 생성 필요)
            setupMockUser(userA);
            File newFile = fileRepository.save(File.builder().filename("new.jpg").s3ObjectKey("new.jpg").fileSize(10L).mediaType(MediaType.IMAGE).build());

            ArchiveDto.UpdateRequest changeReq = new ArchiveDto.UpdateRequest();
            changeReq.setBannerImageId(newFile.getId());
            archiveService.updateArchive(principal, archive.getId(), changeReq);
            assertThat(archiveRepository.findById(archive.getId()).get().getBannerFile().getId()).isEqualTo(newFile.getId());
        }

        @Test
        @DisplayName("SCENE 37~40: 타인/친구 수정 시도 (권한 없음)")
        void updateArchive_Forbidden() {
            ArchiveDto.UpdateRequest req = new ArchiveDto.UpdateRequest();
            req.setTitle("Hacked");

            // 37~39: Stranger attempts (All visibility) -> Forbidden
            assertThatThrownBy(() -> archiveService.updateArchive(UserPrincipal.from(userC), archive.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 40: Friend attempts -> Forbidden (Friend can only read)
            assertThatThrownBy(() -> archiveService.updateArchive(UserPrincipal.from(userB), archive.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 41~43: 엣지 케이스 (존재하지 않는 리소스, IDOR)")
        void updateArchive_EdgeCases() {
            UserPrincipal principal = UserPrincipal.from(userA);
            ArchiveDto.UpdateRequest req = new ArchiveDto.UpdateRequest();

            // 41: No Archive
            assertThatThrownBy(() -> archiveService.updateArchive(principal, 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);

            // 42: IDOR (UserA tries to use UserC's file)
            req.setBannerImageId(bannerFileC.getId());
            assertThatThrownBy(() -> archiveService.updateArchive(principal, archive.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 43: File Not Found
            req.setBannerImageId(999999L);
            assertThatThrownBy(() -> archiveService.updateArchive(principal, archive.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 4]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Delete Archive")
    class Delete {
        @Test
        @DisplayName("SCENE 44: 정상 케이스 (Cascade Delete 확인 - 모든 하위 데이터 포함)")
        void deleteArchive_CascadeFull() {
            // Given: Archive with SubBooks
            Archive archive = createArchiveByService(userA, Visibility.PUBLIC, null);
            Long id = archive.getId();

            // Verify SubBooks exist
            assertThat(diaryBookRepository.existsById(id)).isTrue();
            assertThat(ticketBookRepository.existsById(id)).isTrue();
            assertThat(galleryBookRepository.existsById(id)).isTrue();
            assertThat(repostBookRepository.existsById(id)).isTrue();

            // When
            archiveService.deleteArchive(UserPrincipal.from(userA), id);

            // Then: Root Deleted
            assertThat(archiveRepository.existsById(id)).isFalse();

            // Then: Cascade Deleted
            assertThat(diaryBookRepository.existsById(id)).isFalse();
            assertThat(ticketBookRepository.existsById(id)).isFalse();
            assertThat(galleryBookRepository.existsById(id)).isFalse();
            assertThat(repostBookRepository.existsById(id)).isFalse();
        }

        @Test
        @DisplayName("SCENE 45~47: 모든 Visibility 및 하위 데이터 없음 상태 삭제")
        void deleteArchive_Visibilities() {
            Archive a1 = createArchiveByService(userA, Visibility.RESTRICTED, null);
            Archive a2 = createArchiveByService(userA, Visibility.PRIVATE, null);

            archiveService.deleteArchive(UserPrincipal.from(userA), a1.getId());
            assertThat(archiveRepository.existsById(a1.getId())).isFalse();

            archiveService.deleteArchive(UserPrincipal.from(userA), a2.getId());
            assertThat(archiveRepository.existsById(a2.getId())).isFalse();
        }

        @Test
        @DisplayName("SCENE 48~51: 타인/친구 삭제 시도 (권한 없음)")
        void deleteArchive_Forbidden() {
            Archive archive = createArchiveByService(userA, Visibility.PUBLIC, null);

            // 48~50: Stranger
            assertThatThrownBy(() -> archiveService.deleteArchive(UserPrincipal.from(userC), archive.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 51: Friend
            assertThatThrownBy(() -> archiveService.deleteArchive(UserPrincipal.from(userB), archive.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 52: 존재하지 않는 Archive 삭제")
        void deleteArchive_NotFound() {
            assertThatThrownBy(() -> archiveService.deleteArchive(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 5]: Pagination (Feed)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Pagination (Feed & User Archives)")
    class Pagination {

        @BeforeEach
        void setUpFeed() {
            // UserA: 1 Public, 1 Private
            createArchiveByService(userA, Visibility.PUBLIC, null);
            createArchiveByService(userA, Visibility.PRIVATE, null);

            // UserB: 1 Public, 1 Restricted
            createArchiveByService(userB, Visibility.PUBLIC, null);
            createArchiveByService(userB, Visibility.RESTRICTED, null);

            // UserC: 1 Public
            createArchiveByService(userC, Visibility.PUBLIC, null);
        }

        @Test
        @DisplayName("SCENE 53~58: getGlobalFeed (PUBLIC만 조회, 정렬 확인)")
        void getGlobalFeed() {
            // Given
            ArchiveDto.ArchivePageRequest request = new ArchiveDto.ArchivePageRequest();
            request.setSize(10);
            request.setSort("createdAt");

            // When
            PageDto.PageListResponse<ArchiveDto.ArchivePageResponse> response = archiveService.getGlobalFeed(request);

            // Then
            // UserA(1) + UserB(1) + UserC(1) = 3 Public Archives
            assertThat(response.getPage().getTotalElements()).isEqualTo(3);

            // 검증: 반환된 리스트에 PRIVATE이나 RESTRICTED가 없는지
            List<ArchiveDto.ArchivePageResponse> content = response.getContent();
            assertThat(content).allMatch(a -> a.getVisibility() == Visibility.PUBLIC);

            // SCENE 57: Out of range
            request.setPage(100);
            assertThatThrownBy(() -> archiveService.getGlobalFeed(request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 59~66: getUserArchives (관계 기반 필터링)")
        void getUserArchives_RelationFilter() {
            ArchiveDto.ArchivePageRequest req = new ArchiveDto.ArchivePageRequest();

            // 59: 본인(A) -> 본인(A) : Public(1) + Private(1) = 2
            var myPage = archiveService.getUserArchives(UserPrincipal.from(userA), userA.getId(), req);
            assertThat(myPage.getPage().getTotalElements()).isEqualTo(2);

            // 61: 친구(B) -> 친구(A) : Public(1) + (Restricted 없음) = 1 (Private 숨김)
            var friendViewA = archiveService.getUserArchives(UserPrincipal.from(userB), userA.getId(), req);
            assertThat(friendViewA.getPage().getTotalElements()).isEqualTo(1);

            // 61-2: 친구(A) -> 친구(B) : Public(1) + Restricted(1) = 2
            var friendViewB = archiveService.getUserArchives(UserPrincipal.from(userA), userB.getId(), req);
            assertThat(friendViewB.getPage().getTotalElements()).isEqualTo(2);

            // 63: 타인(C) -> 타인(B) : Public(1) only. (Restricted 숨김)
            var strangerView = archiveService.getUserArchives(UserPrincipal.from(userC), userB.getId(), req);
            assertThat(strangerView.getPage().getTotalElements()).isEqualTo(1);
            assertThat(strangerView.getContent().get(0).getVisibility()).isEqualTo(Visibility.PUBLIC);

            // 65: 비회원 -> 타인(B) : Public(1) only
            var anonView = archiveService.getUserArchives(null, userB.getId(), req);
            assertThat(anonView.getPage().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 67: 존재하지 않는 사용자 피드 조회")
        void getUserArchives_UserNotFound() {
            // 존재하지 않는 유저 조회 시 빈 리스트 반환 (예외 아님)
            // Service 로직상 닉네임 조회는 Optional 처리되어 있음
            var res = archiveService.getUserArchives(UserPrincipal.from(userA), 99999L, new ArchiveDto.ArchivePageRequest());

            assertThat(res.getPage().getTotalElements()).isZero();
            assertThat(res.getTitle()).contains("알 수 없는 사용자");
        }

        @Test
        @DisplayName("SCENE 68: getUserArchives - 빈 결과 (PUBLIC Archive 없음)")
        void getUserArchives_EmptyResult() {
            // Given: UserA에게 PRIVATE Archive만 남김 (PUBLIC 삭제)
            // 현재 UserA는 PUBLIC 1, PRIVATE 1 가지고 있음 -> PUBLIC 삭제
            Archive publicArchive = archiveRepository.findAll().stream()
                    .filter(a -> a.getUser().getId().equals(userA.getId()) && a.getVisibility() == Visibility.PUBLIC)
                    .findFirst().orElseThrow();
            archiveRepository.delete(publicArchive);

            // When: 타인(UserC)이 UserA를 조회 (UserA는 이제 PRIVATE만 있음)
            ArchiveDto.ArchivePageRequest req = new ArchiveDto.ArchivePageRequest();
            var response = archiveService.getUserArchives(UserPrincipal.from(userC), userA.getId(), req);

            // Then: 빈 결과 반환 (PRIVATE은 타인에게 안 보이므로)
            assertThat(response.getPage().getTotalElements()).isZero();
            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("SCENE 69: getUserArchives - 페이지 범위 초과")
        void getUserArchives_PageOutOfRange() {
            // Given: UserA 조회 (데이터 있음)
            ArchiveDto.ArchivePageRequest req = new ArchiveDto.ArchivePageRequest();
            req.setPage(1000); // 말도 안 되는 페이지 번호

            // When & Then: PAGE_NOT_FOUND 예외 발생
            assertThatThrownBy(() -> archiveService.getUserArchives(UserPrincipal.from(userA), userA.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 70: getUserArchives - 페이지네이션 정렬 확인 (조회수 정렬)")
        void getUserArchives_SortCheck() {
            // Given: UserA가 Public Archive 2개를 가짐 (조회수 다르게 설정)
            // 1. 기존 Public Archive (ViewCount = 0)
            Archive archive1 = archiveRepository.findAll().stream()
                    .filter(a -> a.getUser().getId().equals(userA.getId()) && a.getVisibility() == Visibility.PUBLIC)
                    .findFirst().orElseThrow();

            // 2. 새 Public Archive 생성 (ViewCount = 100)
            Archive archive2 = createArchiveByService(userA, Visibility.PUBLIC, null);
            // 조회수 강제 주입 (Setter가 없으므로 increaseViewCount 반복 혹은 리플렉션/Repo 쿼리 사용)
            // 여기선 increaseViewCount 10번 호출로 시뮬레이션
            for(int i=0; i<10; i++) archive2.increaseViewCount();
            archiveRepository.save(archive2);

            // When: 조회수 내림차순(DESC) 조회
            ArchiveDto.ArchivePageRequest req = new ArchiveDto.ArchivePageRequest();
            req.setSort("viewCount");
            req.setDirection("DESC");

            var response = archiveService.getUserArchives(UserPrincipal.from(userA), userA.getId(), req);

            // Then:
            // 1. 전체 개수는 3개 (Public(0) + Private(0) + Public(10))
            // 2. 조회수가 가장 높은 archive2(10)가 첫 번째여야 함
            List<ArchiveDto.ArchivePageResponse> content = response.getContent();
            assertThat(content).hasSize(3); // 기존2 + 새거1

            // 1. 조회수 1위 검증: archive2 (조회수 10)
            assertThat(content.get(0).getArchiveId()).isEqualTo(archive2.getId());
            assertThat(content.get(0).getViewCount()).isEqualTo(10);

            // 2. 조회수 동률(0) 처리 검증: ID 내림차순(최신순) 정렬 확인
            // Private(ID 2)가 Public(ID 1)보다 최신이므로 먼저 나와야 함
            assertThat(content.get(1).getArchiveId()).isGreaterThan(content.get(2).getArchiveId());

            assertThat(content.get(2).getArchiveId()).isEqualTo(archive1.getId());
        }
    }

    // --- Helper for creating archives via Service (ensures sub-books creation) ---
    private Archive createArchiveByService(User owner, Visibility visibility, File banner) {
        setupMockUser(owner);
        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);
        req.setBannerImageId(banner != null ? banner.getId() : null);

        ArchiveDto.Response res = archiveService.createArchive(UserPrincipal.from(owner), req);
        SecurityContextHolder.clearContext();
        return archiveRepository.findById(res.getId()).orElseThrow();
    }
}