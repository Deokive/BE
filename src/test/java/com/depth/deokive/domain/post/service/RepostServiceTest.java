package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.entity.RepostTab;
import com.depth.deokive.domain.post.repository.RepostRepository;
import com.depth.deokive.domain.post.repository.RepostTabRepository;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RepostService 통합 테스트")
class RepostServiceTest extends IntegrationTestSupport {

    @Autowired RepostService repostService;
    @Autowired ArchiveService archiveService;

    @Autowired RepostRepository repostRepository;
    @Autowired RepostTabRepository repostTabRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired FriendMapRepository friendMapRepository;

    private User userA, userB, userC;
    private Archive archiveAPublic, archiveARestricted, archiveAPrivate;

    // Test URL constants
    private static final String VALID_URL_TWITTER = "https://twitter.com/test/status/123";
    private static final String VALID_URL_INSTAGRAM = "https://instagram.com/p/test123";
    private static final String VALID_URL_YOUTUBE = "https://youtube.com/watch?v=abc123";
    private static final String INVALID_URL = "not-a-valid-url";

    @BeforeEach
    void setUp() {
        // Users Setup
        userA = createTestUser("usera@test.com", "UserA");
        userB = createTestUser("userb@test.com", "UserB");
        userC = createTestUser("userc@test.com", "UserC");

        // Friend Setup (A <-> B)
        friendMapRepository.save(FriendMap.builder().user(userA).friend(userB).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());
        friendMapRepository.save(FriendMap.builder().user(userB).friend(userA).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());

        // Archives Setup
        setupMockUser(userA);
        archiveAPublic = createArchiveByService(userA, Visibility.PUBLIC);
        archiveARestricted = createArchiveByService(userA, Visibility.RESTRICTED);
        archiveAPrivate = createArchiveByService(userA, Visibility.PRIVATE);

        SecurityContextHolder.clearContext();
    }

    // --- Helpers ---
    private User createTestUser(String email, String nickname) {
        User user = User.builder().email(email).username("user_" + UUID.randomUUID()).nickname(nickname).password("password").role(Role.USER).userType(UserType.COMMON).isEmailVerified(true).build();
        return userRepository.save(user);
    }

    private Archive createArchiveByService(User owner, Visibility visibility) {
        setupMockUser(owner);
        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);
        ArchiveDto.Response response = archiveService.createArchive(UserPrincipal.from(owner), req);
        SecurityContextHolder.clearContext();
        return archiveRepository.findById(response.getId()).orElseThrow();
    }

    private RepostTab createTab(User owner, Long archiveId) {
        setupMockUser(owner);
        return repostTabRepository.findById(repostService.createRepostTab(UserPrincipal.from(owner), archiveId).getId()).orElseThrow();
    }

    // ========================================================================================
    // [Category 1]: Create Repost (SCENE 1~13)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Repost")
    class CreateRepost {
        @Test
        @DisplayName("SCENE 1: 정상 케이스 (PUBLIC Archive)")
        void createRepost_Public() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);

            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);

            Repost repost = repostRepository.findById(res.getId()).orElseThrow();
            assertThat(repost.getUrl()).isEqualTo(VALID_URL_TWITTER);
            assertThat(repost.getTitle()).isNotNull();
            // thumbnailUrl is nullable and comes from OG extraction
        }

        @Test
        @DisplayName("SCENE 2: 정상 케이스 (RESTRICTED Archive)")
        void createRepost_Restricted() {
            RepostTab tab = createTab(userA, archiveARestricted.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_INSTAGRAM);

            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);
            assertThat(repostRepository.existsById(res.getId())).isTrue();
            assertThat(res.getUrl()).isEqualTo(VALID_URL_INSTAGRAM);
        }

        @Test
        @DisplayName("SCENE 3: 정상 케이스 (PRIVATE Archive)")
        void createRepost_Private() {
            RepostTab tab = createTab(userA, archiveAPrivate.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_YOUTUBE);

            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);
            assertThat(repostRepository.existsById(res.getId())).isTrue();
            assertThat(res.getUrl()).isEqualTo(VALID_URL_YOUTUBE);
        }

        @Test
        @DisplayName("SCENE 4: 정상 케이스 (여러 URL 형식)")
        void createRepost_MultipleUrlFormats() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());

            String twitterUrl = "https://twitter.com/user/status/456";
            RepostDto.CreateRequest req1 = new RepostDto.CreateRequest();
            req1.setUrl(twitterUrl);
            RepostDto.Response res1 = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req1);

            assertThat(res1.getUrl()).isEqualTo(twitterUrl);
            assertThat(res1.getTitle()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 5: 정상 케이스 (OG 추출 - thumbnailUrl nullable)")
        void createRepost_NullableThumbnail() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);

            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);

            // thumbnailUrl is nullable - comes from OG extraction, may or may not exist
            Repost repost = repostRepository.findById(res.getId()).orElseThrow();
            assertThat(repost.getUrl()).isEqualTo(VALID_URL_TWITTER);
            // No assertion on thumbnailUrl - it's nullable and depends on OG extraction
        }

        @Test
        @DisplayName("SCENE 6~11: 예외 케이스")
        void createRepost_Exceptions() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest validReq = new RepostDto.CreateRequest();
            validReq.setUrl(VALID_URL_TWITTER);

            // 6: Tab Not Found
            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userA), 99999L, validReq))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);

            // 7: Forbidden (Public) - Stranger
            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userC), tab.getId(), validReq))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 8, 9, 10: Forbidden (Restricted) - Stranger/Friend
            RepostTab resTab = createTab(userA, archiveARestricted.getId());
            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userC), resTab.getId(), validReq)).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userB), resTab.getId(), validReq)).isInstanceOf(RestException.class);

            // 11: Invalid URL format
            RepostDto.CreateRequest invalidReq = new RepostDto.CreateRequest();
            invalidReq.setUrl(INVALID_URL);
            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userA), tab.getId(), invalidReq))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_INVALID_URL);
        }

        @Test
        @DisplayName("SCENE 12: URL 중복 생성 시도")
        void createRepost_Duplicate() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);

            repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);

            assertThatThrownBy(() -> repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_URL_DUPLICATED);
        }

        @Test
        @DisplayName("SCENE 13: 같은 URL을 다른 Tab에 생성")
        void createRepost_DifferentTabs() {
            RepostTab tab1 = createTab(userA, archiveAPublic.getId());
            RepostTab tab2 = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);

            repostService.createRepost(UserPrincipal.from(userA), tab1.getId(), req);
            repostService.createRepost(UserPrincipal.from(userA), tab2.getId(), req);

            String urlHash = generateUrlHash(VALID_URL_TWITTER);
            assertThat(repostRepository.existsByRepostTabIdAndUrlHash(tab1.getId(), urlHash)).isTrue();
            assertThat(repostRepository.existsByRepostTabIdAndUrlHash(tab2.getId(), urlHash)).isTrue();
        }
    }

    // ========================================================================================
    // [Category 2]: Update Repost (SCENE 14~22)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Update Repost")
    class UpdateRepost {
        private Repost repost;

        @BeforeEach
        void init() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);
            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);
            repost = repostRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 14~17: 정상 수정 (제목 수정, URL 유지)")
        void updateRepost_Normal() {
            RepostDto.UpdateRequest req = new RepostDto.UpdateRequest();
            req.setTitle("New Title");

            repostService.updateRepost(UserPrincipal.from(userA), repost.getId(), req);

            Repost updated = repostRepository.findById(repost.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getUrl()).isEqualTo(repost.getUrl()); // URL preserved
            // thumbnailUrl is nullable and preserved from original OG extraction
        }

        @Test
        @DisplayName("SCENE 18~22: 예외 케이스")
        void updateRepost_Exceptions() {
            RepostDto.UpdateRequest req = new RepostDto.UpdateRequest(); req.setTitle("Hacked");

            // 18: Forbidden (Stranger)
            assertThatThrownBy(() -> repostService.updateRepost(UserPrincipal.from(userC), repost.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 22: Not Found
            assertThatThrownBy(() -> repostService.updateRepost(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 3]: Delete Repost (SCENE 23~30)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Delete Repost")
    class DeleteRepost {
        private Repost repost;

        @BeforeEach
        void init() {
            RepostTab tab = createTab(userA, archiveAPublic.getId());
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);
            RepostDto.Response res = repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);
            repost = repostRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 23~25: 정상 삭제")
        void deleteRepost_Normal() {
            repostService.deleteRepost(UserPrincipal.from(userA), repost.getId());
            flushAndClear();

            assertThat(repostRepository.existsById(repost.getId())).isFalse();
            // URL-based Repost is independent - no external Post to verify
        }

        @Test
        @DisplayName("SCENE 26~30: 예외 케이스")
        void deleteRepost_Exceptions() {
            // 26: Forbidden
            assertThatThrownBy(() -> repostService.deleteRepost(UserPrincipal.from(userC), repost.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 30: Not Found
            assertThatThrownBy(() -> repostService.deleteRepost(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 4]: Create RepostTab (SCENE 31~40)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Create RepostTab")
    class CreateRepostTab {
        @Test
        @DisplayName("SCENE 31~34: 정상 케이스 (Tab 생성)")
        void createRepostTab_Normal() {
            // 31: 1st Tab
            RepostDto.TabResponse res1 = repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId());
            assertThat(res1.getTitle()).isEqualTo("1번째 탭");

            // 32: Multiple Tabs
            repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId());
            repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId());
            RepostDto.TabResponse res4 = repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId());
            assertThat(res4.getTitle()).isEqualTo("4번째 탭");
        }

        @Test
        @DisplayName("SCENE 35: 10개 제한 초과")
        void createRepostTab_Limit() {
            for(int i=0; i<10; i++) {
                repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId());
            }
            flushAndClear();

            assertThatThrownBy(() -> repostService.createRepostTab(UserPrincipal.from(userA), archiveAPublic.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_TAB_LIMIT_EXCEED);
        }

        @Test
        @DisplayName("SCENE 36: 존재하지 않는 RepostBook")
        void createRepostTab_NotFound() {
            assertThatThrownBy(() -> repostService.createRepostTab(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 37~40: 권한 예외")
        void createRepostTab_Forbidden() {
            assertThatThrownBy(() -> repostService.createRepostTab(UserPrincipal.from(userC), archiveAPublic.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 5]: Update RepostTab (SCENE 41~48)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Update RepostTab")
    class UpdateRepostTab {
        private RepostTab tab;

        @BeforeEach
        void init() {
            tab = createTab(userA, archiveAPublic.getId());
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 41~43: 정상 수정")
        void updateRepostTab_Normal() {
            RepostDto.UpdateTabRequest req = new RepostDto.UpdateTabRequest(); req.setTitle("My Tab");
            repostService.updateRepostTab(UserPrincipal.from(userA), tab.getId(), req);

            assertThat(repostTabRepository.findById(tab.getId()).get().getTitle()).isEqualTo("My Tab");
        }

        @Test
        @DisplayName("SCENE 44~48: 예외 케이스")
        void updateRepostTab_Exceptions() {
            RepostDto.UpdateTabRequest req = new RepostDto.UpdateTabRequest(); req.setTitle("Hack");

            // 44: Forbidden
            assertThatThrownBy(() -> repostService.updateRepostTab(UserPrincipal.from(userC), tab.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 48: Not Found
            assertThatThrownBy(() -> repostService.updateRepostTab(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_TAB_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 6]: Delete RepostTab (SCENE 49~57)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] Delete RepostTab")
    class DeleteRepostTab {
        private RepostTab tab;

        @BeforeEach
        void init() {
            tab = createTab(userA, archiveAPublic.getId());
            // Create Repost inside tab
            RepostDto.CreateRequest req = new RepostDto.CreateRequest();
            req.setUrl(VALID_URL_TWITTER);
            repostService.createRepost(UserPrincipal.from(userA), tab.getId(), req);
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 49~52: 정상 삭제 (Repost 포함)")
        void deleteRepostTab_Normal() {
            repostService.deleteRepostTab(UserPrincipal.from(userA), tab.getId());
            flushAndClear(); // Bulk Delete sync

            assertThat(repostTabRepository.existsById(tab.getId())).isFalse();
            assertThat(repostRepository.findAll()).isEmpty(); // Cascade delete check
        }

        @Test
        @DisplayName("SCENE 53~57: 예외 케이스")
        void deleteRepostTab_Exceptions() {
            // 53: Forbidden
            assertThatThrownBy(() -> repostService.deleteRepostTab(UserPrincipal.from(userC), tab.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 57: Not Found
            assertThatThrownBy(() -> repostService.deleteRepostTab(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_TAB_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 7]: Read-Pagination (SCENE 58~76)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 7] Read Repost")
    class ReadRepost {
        private RepostTab tab1;
        private RepostTab tab2;

        @BeforeEach
        void init() {
            // Create 2 tabs
            tab1 = createTab(userA, archiveAPublic.getId());
            tab2 = createTab(userA, archiveAPublic.getId());

            UserPrincipal principal = UserPrincipal.from(userA);
            // Tab 1: 5 reposts with different URLs
            for(int i=0; i<5; i++) {
                RepostDto.CreateRequest req = new RepostDto.CreateRequest();
                req.setUrl("https://twitter.com/test/status/" + (100 + i));
                repostService.createRepost(principal, tab1.getId(), req);
            }
            // Tab 2: 3 reposts with different URLs
            for(int i=0; i<3; i++) {
                RepostDto.CreateRequest req = new RepostDto.CreateRequest();
                req.setUrl("https://instagram.com/p/test" + (200 + i));
                repostService.createRepost(principal, tab2.getId(), req);
            }
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 58~61: PUBLIC Archive (본인, 타인, 친구, 비회원)")
        void getReposts_Public() {
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest();
            req.setTabId(tab1.getId());

            // 58: Owner
            RepostDto.RepostListResponse resOwner = repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), tab1.getId(), req.toPageable());
            assertThat(resOwner.getContent()).hasSize(5);
            assertThat(resOwner.getTab()).hasSize(2);

            // 59, 60, 61: Others
            assertThat(repostService.getReposts(UserPrincipal.from(userC), archiveAPublic.getId(), tab1.getId(), req.toPageable()).getContent()).hasSize(5);
            assertThat(repostService.getReposts(UserPrincipal.from(userB), archiveAPublic.getId(), tab1.getId(), req.toPageable()).getContent()).hasSize(5);
            assertThat(repostService.getReposts(null, archiveAPublic.getId(), tab1.getId(), req.toPageable()).getContent()).hasSize(5);
        }

        @Test
        @DisplayName("SCENE 62: tabId = null (첫 번째 탭)")
        void getReposts_DefaultTab() {
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest();
            RepostDto.RepostListResponse res = repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), null, req.toPageable());

            // Tab1 created first -> ID is smaller -> Default
            assertThat(res.getTabId()).isEqualTo(tab1.getId());
            assertThat(res.getContent()).hasSize(5);
        }

        @Test
        @DisplayName("SCENE 63~66: RESTRICTED Archive")
        void getReposts_Restricted() {
            RepostTab rTab = createTab(userA, archiveARestricted.getId());
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest(); req.setTabId(rTab.getId());

            // 63: Owner OK
            assertThat(repostService.getReposts(UserPrincipal.from(userA), archiveARestricted.getId(), rTab.getId(), req.toPageable())).isNotNull();
            // 64: Friend OK
            assertThat(repostService.getReposts(UserPrincipal.from(userB), archiveARestricted.getId(), rTab.getId(), req.toPageable())).isNotNull();
            // 65: Stranger Fail
            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userC), archiveARestricted.getId(), rTab.getId(), req.toPageable())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 67~70: PRIVATE Archive")
        void getReposts_Private() {
            RepostTab pTab = createTab(userA, archiveAPrivate.getId());
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest(); req.setTabId(pTab.getId());

            // 67: Owner OK
            assertThat(repostService.getReposts(UserPrincipal.from(userA), archiveAPrivate.getId(), pTab.getId(), req.toPageable())).isNotNull();
            // 68~70: Others Fail
            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userC), archiveAPrivate.getId(), pTab.getId(), req.toPageable())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userB), archiveAPrivate.getId(), pTab.getId(), req.toPageable())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 71~74: Edge Cases (No Tabs, NotFound)")
        void getReposts_Edge() {
            Archive newArchive = createArchiveByService(userA, Visibility.PUBLIC);
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest();

            // 71: No Tabs
            RepostDto.RepostListResponse res = repostService.getReposts(UserPrincipal.from(userA), newArchive.getId(), null, req.toPageable());
            assertThat(res.getContent()).isEmpty();
            assertThat(res.getTab()).isEmpty();

            // 72: Tab Not Found (tabId is not in archive)
            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), 99999L, req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPOST_TAB_NOT_FOUND);

            // 73: Book Not Found
            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userA), 99999L, null, req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 75: Page Out of Range")
        void getReposts_PageOut() {
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest();
            req.setPage(100); req.setTabId(tab1.getId());

            assertThatThrownBy(() -> repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), tab1.getId(), req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 76: 여러 Tab 간 Repost 분리 확인")
        void getReposts_Separation() {
            RepostDto.RepostPageRequest req = new RepostDto.RepostPageRequest();

            // Tab1: 5개
            assertThat(repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), tab1.getId(), req.toPageable()).getContent()).hasSize(5);
            // Tab2: 3개
            assertThat(repostService.getReposts(UserPrincipal.from(userA), archiveAPublic.getId(), tab2.getId(), req.toPageable()).getContent()).hasSize(3);
        }
    }

    // Helper methods
    private String generateUrlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}