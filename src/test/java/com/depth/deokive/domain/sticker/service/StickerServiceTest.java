package com.depth.deokive.domain.sticker.service;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.sticker.dto.StickerDto;
import com.depth.deokive.domain.sticker.entity.Sticker;
import com.depth.deokive.domain.sticker.entity.enums.StickerType;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StickerService 통합 테스트 (Full Coverage)")
class StickerServiceTest extends IntegrationTestSupport {

    @Autowired StickerService stickerService;
    @Autowired ArchiveService archiveService;

    @Autowired StickerRepository stickerRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired UserRepository userRepository;
    @Autowired FriendMapRepository friendMapRepository;

    private User userA, userB, userC;
    private Archive archiveAPublic, archiveARestricted, archiveAPrivate;

    @BeforeEach
    void setUp() {
        // Users
        userA = createTestUser("stickera@test.com", "StickerA");
        userB = createTestUser("stickerb@test.com", "StickerB");
        userC = createTestUser("stickerc@test.com", "StickerC");

        // Friend (A <-> B)
        friendMapRepository.save(FriendMap.builder().user(userA).friend(userB).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());
        friendMapRepository.save(FriendMap.builder().user(userB).friend(userA).requestedBy(userA).friendStatus(FriendStatus.ACCEPTED).build());

        // Archives
        setupMockUser(userA);
        archiveAPublic = createArchive(userA, Visibility.PUBLIC);
        archiveARestricted = createArchive(userA, Visibility.RESTRICTED);
        archiveAPrivate = createArchive(userA, Visibility.PRIVATE);

        SecurityContextHolder.clearContext();
    }

    private User createTestUser(String email, String nickname) {
        User user = User.builder().email(email).username("user_" + UUID.randomUUID()).nickname(nickname).password("pw").role(Role.USER).userType(UserType.COMMON).isEmailVerified(true).build();
        return userRepository.save(user);
    }

    private Archive createArchive(User owner, Visibility visibility) {
        setupMockUser(owner);
        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);
        ArchiveDto.Response res = archiveService.createArchive(UserPrincipal.from(owner), req);
        return archiveRepository.findById(res.getId()).orElseThrow();
    }

    // ========================================================================================
    // [Category 1]: Create (SCENE 1 ~ 7)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Sticker")
    class Create {
        @Test
        @DisplayName("SCENE 1: 정상 생성")
        void create_Normal() {
            setupMockUser(userA);
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);

            StickerDto.Response res = stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req);

            Sticker sticker = stickerRepository.findById(res.getId()).orElseThrow();
            assertThat(sticker.getDate()).isEqualTo(LocalDate.of(2024, 5, 5));
            assertThat(sticker.getStickerType()).isEqualTo(StickerType.HEART);
        }

        @Test
        @DisplayName("SCENE 2: 윤년(Leap Year) 생성 확인 (2024-02-29)")
        void create_LeapYear() {
            setupMockUser(userA);
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 2, 29));
            req.setStickerType(StickerType.STAR);

            StickerDto.Response res = stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            assertThat(res.getDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("SCENE 3: 예외 - 중복 날짜 생성")
        void create_DuplicateDate() {
            setupMockUser(userA);
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);

            stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req);

            assertThatThrownBy(() -> stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STICKER_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("SCENE 4: 예외 - 타인이 생성 시도")
        void create_Forbidden_Stranger() {
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);

            assertThatThrownBy(() -> stickerService.createSticker(UserPrincipal.from(userC), archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 5: 예외 - 친구가 생성 시도")
        void create_Forbidden_Friend() {
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);

            assertThatThrownBy(() -> stickerService.createSticker(UserPrincipal.from(userB), archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 6: 예외 - 존재하지 않는 Archive")
        void create_NotFound_Archive() {
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);

            assertThatThrownBy(() -> stickerService.createSticker(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 2]: Update (SCENE 7 ~ 14)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Update Sticker")
    class Update {
        private Sticker sticker;

        @BeforeEach
        void init() {
            setupMockUser(userA);
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);
            StickerDto.Response res = stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            sticker = stickerRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 7: 정상 수정 - 타입만 변경")
        void update_TypeOnly() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setStickerType(StickerType.STAR);
            // date is null

            stickerService.updateSticker(UserPrincipal.from(userA), sticker.getId(), req);
            flushAndClear();

            Sticker updated = stickerRepository.findById(sticker.getId()).orElseThrow();
            assertThat(updated.getStickerType()).isEqualTo(StickerType.STAR);
            assertThat(updated.getDate()).isEqualTo(LocalDate.of(2024, 5, 5));
        }

        @Test
        @DisplayName("SCENE 8: 정상 수정 - 날짜만 변경 (충돌 없음)")
        void update_DateOnly() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setDate(LocalDate.of(2024, 5, 6));

            stickerService.updateSticker(UserPrincipal.from(userA), sticker.getId(), req);
            flushAndClear();

            Sticker updated = stickerRepository.findById(sticker.getId()).orElseThrow();
            assertThat(updated.getDate()).isEqualTo(LocalDate.of(2024, 5, 6));
            assertThat(updated.getStickerType()).isEqualTo(StickerType.HEART);
        }

        @Test
        @DisplayName("SCENE 9: 정상 수정 - 자기 자신의 날짜로 업데이트 (Idempotent)")
        void update_SameDate() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setDate(LocalDate.of(2024, 5, 5)); // 기존과 동일
            req.setStickerType(StickerType.CIRCLE);

            stickerService.updateSticker(UserPrincipal.from(userA), sticker.getId(), req);
            flushAndClear();

            Sticker updated = stickerRepository.findById(sticker.getId()).orElseThrow();
            assertThat(updated.getDate()).isEqualTo(LocalDate.of(2024, 5, 5));
            assertThat(updated.getStickerType()).isEqualTo(StickerType.CIRCLE);
        }

        @Test
        @DisplayName("SCENE 10: 예외 - 다른 스티커가 있는 날짜로 변경 (Conflict)")
        void update_ConflictDate() {
            // 5월 10일에 스티커 추가
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 5, 10));

            // 기존 스티커(5/5)를 5/10으로 이동 시도
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setDate(LocalDate.of(2024, 5, 10));

            assertThatThrownBy(() -> stickerService.updateSticker(UserPrincipal.from(userA), sticker.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STICKER_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("SCENE 11: 예외 - 타인이 수정 시도")
        void update_Forbidden() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setStickerType(StickerType.STAR);

            assertThatThrownBy(() -> stickerService.updateSticker(UserPrincipal.from(userC), sticker.getId(), req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 12: 예외 - 존재하지 않는 스티커")
        void update_NotFound() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest();
            req.setStickerType(StickerType.STAR);

            assertThatThrownBy(() -> stickerService.updateSticker(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STICKER_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 13: 아무 값도 변경 안함 (null inputs)")
        void update_NullInputs() {
            StickerDto.UpdateRequest req = new StickerDto.UpdateRequest(); // all null
            stickerService.updateSticker(UserPrincipal.from(userA), sticker.getId(), req);

            Sticker updated = stickerRepository.findById(sticker.getId()).orElseThrow();
            assertThat(updated.getStickerType()).isEqualTo(StickerType.HEART);
        }
    }

    // ========================================================================================
    // [Category 3]: Delete (SCENE 14 ~ 16)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Delete Sticker")
    class Delete {
        @Test
        @DisplayName("SCENE 14: 정상 삭제")
        void delete_Normal() {
            setupMockUser(userA);
            StickerDto.CreateRequest req = new StickerDto.CreateRequest();
            req.setDate(LocalDate.of(2024, 5, 5));
            req.setStickerType(StickerType.HEART);
            StickerDto.Response res = stickerService.createSticker(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            flushAndClear();

            stickerService.deleteSticker(UserPrincipal.from(userA), res.getId());
            flushAndClear();

            assertThat(stickerRepository.existsById(res.getId())).isFalse();
        }

        @Test
        @DisplayName("SCENE 15: 예외 - 타인이 삭제 시도")
        void delete_Forbidden() {
            Sticker sticker = createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 5, 5));

            assertThatThrownBy(() -> stickerService.deleteSticker(UserPrincipal.from(userB), sticker.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 16: 예외 - 존재하지 않는 스티커")
        void delete_NotFound() {
            assertThatThrownBy(() -> stickerService.deleteSticker(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STICKER_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 4]: Get Monthly (Read & Permissions) (SCENE 17 ~ 30)
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Get Monthly Stickers (권한 매트릭스)")
    class GetMonthly {
        @BeforeEach
        void initData() {
            setupMockUser(userA);
            // Public Archive: 4/30, 5/1, 5/15, 5/31, 6/1
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 4, 30));
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 5, 1));
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 5, 15));
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 5, 31));
            createStickerDirectly(userA, archiveAPublic.getId(), LocalDate.of(2024, 6, 1));

            // Restricted Archive: 5/10
            createStickerDirectly(userA, archiveARestricted.getId(), LocalDate.of(2024, 5, 10));

            // Private Archive: 5/20
            createStickerDirectly(userA, archiveAPrivate.getId(), LocalDate.of(2024, 5, 20));

            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 17: 월별 조회 경계값 검증 (5월 데이터만 조회)")
        void getMonthly_Boundary() {
            List<StickerDto.Response> result = stickerService.getMonthlyStickers(UserPrincipal.from(userA), archiveAPublic.getId(), 2024, 5);

            // 5/1, 5/15, 5/31 (총 3개)
            assertThat(result).hasSize(3);
            assertThat(result).extracting(StickerDto.Response::getDate)
                    .containsExactly(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 15), LocalDate.of(2024, 5, 31));
        }

        @ParameterizedTest
        @CsvSource({
                "PUBLIC, OWNER, 3",    // SCENE 18
                "PUBLIC, FRIEND, 3",   // SCENE 19
                "PUBLIC, STRANGER, 3", // SCENE 20
                "PUBLIC, ANON, 3"      // SCENE 21
        })
        @DisplayName("SCENE 18~21: PUBLIC 아카이브 권한별 조회")
        void getMonthly_Public_Permissions(String visibility, String role, int expectedCount) {
            UserPrincipal principal = getPrincipalByRole(role);
            List<StickerDto.Response> result = stickerService.getMonthlyStickers(principal, archiveAPublic.getId(), 2024, 5);
            assertThat(result).hasSize(expectedCount);
        }

        @ParameterizedTest
        @CsvSource({
                "RESTRICTED, OWNER, 1",    // SCENE 22
                "RESTRICTED, FRIEND, 1",   // SCENE 23
                "RESTRICTED, STRANGER, 0", // SCENE 24 (Forbidden)
                "RESTRICTED, ANON, 0"      // SCENE 25 (Forbidden)
        })
        @DisplayName("SCENE 22~25: RESTRICTED 아카이브 권한별 조회")
        void getMonthly_Restricted_Permissions(String visibility, String role, int expectedCount) {
            UserPrincipal principal = getPrincipalByRole(role);
            Long archiveId = archiveARestricted.getId();

            if (expectedCount == 0) {
                assertThatThrownBy(() -> stickerService.getMonthlyStickers(principal, archiveId, 2024, 5))
                        .isInstanceOf(RestException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
            } else {
                assertThat(stickerService.getMonthlyStickers(principal, archiveId, 2024, 5)).hasSize(expectedCount);
            }
        }

        @ParameterizedTest
        @CsvSource({
                "PRIVATE, OWNER, 1",    // SCENE 26
                "PRIVATE, FRIEND, 0",   // SCENE 27 (Forbidden)
                "PRIVATE, STRANGER, 0", // SCENE 28 (Forbidden)
                "PRIVATE, ANON, 0"      // SCENE 29 (Forbidden)
        })
        @DisplayName("SCENE 26~29: PRIVATE 아카이브 권한별 조회")
        void getMonthly_Private_Permissions(String visibility, String role, int expectedCount) {
            UserPrincipal principal = getPrincipalByRole(role);
            Long archiveId = archiveAPrivate.getId();

            if (expectedCount == 0) {
                assertThatThrownBy(() -> stickerService.getMonthlyStickers(principal, archiveId, 2024, 5))
                        .isInstanceOf(RestException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
            } else {
                assertThat(stickerService.getMonthlyStickers(principal, archiveId, 2024, 5)).hasSize(expectedCount);
            }
        }

        @Test
        @DisplayName("SCENE 30: 빈 달 조회 (2025년 1월)")
        void getMonthly_Empty() {
            assertThat(stickerService.getMonthlyStickers(UserPrincipal.from(userA), archiveAPublic.getId(), 2025, 1)).isEmpty();
        }

        // --- Helper for ParameterizedTest ---
        private UserPrincipal getPrincipalByRole(String role) {
            switch (role) {
                case "OWNER": return UserPrincipal.from(userA);
                case "FRIEND": return UserPrincipal.from(userB);
                case "STRANGER": return UserPrincipal.from(userC);
                case "ANON": return null;
                default: throw new IllegalArgumentException("Unknown Role");
            }
        }
    }

    // Helper
    private Sticker createStickerDirectly(User u, Long archiveId, LocalDate date) {
        StickerDto.CreateRequest req = new StickerDto.CreateRequest();
        req.setDate(date); req.setStickerType(StickerType.HEART);
        StickerDto.Response res = stickerService.createSticker(UserPrincipal.from(u), archiveId, req);
        return stickerRepository.findById(res.getId()).get();
    }
}