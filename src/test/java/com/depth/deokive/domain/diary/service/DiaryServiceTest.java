package com.depth.deokive.domain.diary.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.friend.entity.FriendMap;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DiaryService 통합 테스트")
class DiaryServiceTest extends IntegrationTestSupport {

    @Autowired DiaryService diaryService;
    @Autowired ArchiveService archiveService;

    // Core Repositories
    @Autowired DiaryRepository diaryRepository;
    @Autowired DiaryBookRepository diaryBookRepository;
    @Autowired DiaryFileMapRepository diaryFileMapRepository;
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
    private Archive archiveBPublic; // 타인 Archive 테스트용

    private List<File> userAFiles; // UserA 소유 파일들 (10개)
    private List<File> userCFiles; // UserC 소유 파일들 (10개)

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
        archiveAPublic = createArchiveByService(userA, Visibility.PUBLIC, null);
        archiveARestricted = createArchiveByService(userA, Visibility.RESTRICTED, null);
        archiveAPrivate = createArchiveByService(userA, Visibility.PRIVATE, null);

        setupMockUser(userB);
        archiveBPublic = createArchiveByService(userB, Visibility.PUBLIC, null);

        // 4. Files Setup (각 User당 10개씩)
        setupMockUser(userA);
        userAFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            File file = fileRepository.save(File.builder()
                    .filename("fileA_" + i + ".jpg")
                    .s3ObjectKey("files/fileA_" + i + ".jpg")
                    .fileSize(100L)
                    .mediaType(MediaType.IMAGE)
                    .build());
            userAFiles.add(file);
        }

        setupMockUser(userC);
        userCFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            File file = fileRepository.save(File.builder()
                    .filename("fileC_" + i + ".jpg")
                    .s3ObjectKey("files/fileC_" + i + ".jpg")
                    .fileSize(100L)
                    .mediaType(MediaType.IMAGE)
                    .build());
            userCFiles.add(file);
        }

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

    private Archive createArchiveByService(User owner, Visibility visibility, File banner) {
        setupMockUser(owner);
        UserPrincipal principal = UserPrincipal.from(owner);
        
        ArchiveDto.CreateRequest req = new ArchiveDto.CreateRequest();
        req.setTitle("Archive " + visibility);
        req.setVisibility(visibility);
        req.setBannerImageId(banner != null ? banner.getId() : null);

        ArchiveDto.Response response = archiveService.createArchive(principal, req);
        SecurityContextHolder.clearContext();
        return archiveRepository.findById(response.getId()).orElseThrow();
    }

    // ========================================================================================
    // [Category 1]: Create
    // ========================================================================================
    @Nested
    @DisplayName("[Category 1] Create Diary")
    class Create {

        @Test
        @DisplayName("SCENE 1: 정상 케이스 (파일 포함, PREVIEW 파일 있음)")
        void createDiary_WithFiles_Preview() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(0).getId())
                    .mediaRole(MediaRole.PREVIEW)
                    .sequence(0)
                    .build());
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(1).getId())
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(1)
                    .build());

            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Test Diary");
            request.setContent("Test Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);
            request.setFiles(files);

            // When
            DiaryDto.Response response = diaryService.createDiary(principal, archiveAPublic.getId(), request);

            // Then
            Diary savedDiary = diaryRepository.findById(response.getId()).orElseThrow();
            assertThat(savedDiary.getTitle()).isEqualTo(request.getTitle());
            assertThat(savedDiary.getVisibility()).isEqualTo(Visibility.PUBLIC);

            List<DiaryFileMap> maps = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(savedDiary.getId());
            assertThat(maps).hasSize(2);
            assertThat(maps.get(0).getSequence()).isEqualTo(0);
            assertThat(maps.get(1).getSequence()).isEqualTo(1);
            assertThat(maps.get(0).getMediaRole()).isEqualTo(MediaRole.PREVIEW);
            assertThat(savedDiary.getThumbnailKey()).isNotNull(); // PREVIEW 파일 기반 썸네일
        }

        @Test
        @DisplayName("SCENE 2: 파일 없이 생성")
        void createDiary_NoFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("No Files Diary");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);
            request.setFiles(null);

            // When
            DiaryDto.Response response = diaryService.createDiary(principal, archiveAPublic.getId(), request);

            // Then
            Diary savedDiary = diaryRepository.findById(response.getId()).orElseThrow();
            assertThat(savedDiary.getTitle()).isEqualTo(request.getTitle());

            List<DiaryFileMap> maps = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(savedDiary.getId());
            assertThat(maps).isEmpty();
            assertThat(savedDiary.getThumbnailKey()).isNull();
        }

        @Test
        @DisplayName("SCENE 3: 존재하지 않는 Archive")
        void createDiary_ArchiveNotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Test");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);

            // When & Then
            assertThatThrownBy(() -> diaryService.createDiary(principal, 99999L, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 4: 타인 Archive에 생성 시도")
        void createDiary_Forbidden() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Hacked");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);

            // When & Then
            assertThatThrownBy(() -> diaryService.createDiary(principal, archiveBPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 5: 다른 사용자의 파일 사용 시도 (IDOR)")
        void createDiary_IDOR() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userCFiles.get(0).getId()) // UserC의 파일
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(0)
                    .build());

            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Test");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);
            request.setFiles(files);

            // When & Then
            assertThatThrownBy(() -> diaryService.createDiary(principal, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 6: 중복된 파일 ID")
        void createDiary_DuplicateFileId() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(0).getId())
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(0)
                    .build());
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(0).getId()) // 중복
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(1)
                    .build());

            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Test");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);
            request.setFiles(files);

            // When & Then
            assertThatThrownBy(() -> diaryService.createDiary(principal, archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 7: MediaRole PREVIEW 우선 썸네일")
        void createDiary_PreviewThumbnail() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(0).getId())
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(0)
                    .build());
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(1).getId())
                    .mediaRole(MediaRole.PREVIEW) // PREVIEW가 나중에 와도 우선
                    .sequence(1)
                    .build());

            DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
            request.setTitle("Test");
            request.setContent("Content");
            request.setRecordedAt(LocalDate.now());
            request.setColor("#FF5733");
            request.setVisibility(Visibility.PUBLIC);
            request.setFiles(files);

            // When
            DiaryDto.Response response = diaryService.createDiary(principal, archiveAPublic.getId(), request);

            // Then
            Diary savedDiary = diaryRepository.findById(response.getId()).orElseThrow();
            assertThat(savedDiary.getThumbnailKey()).isNotNull();
            // PREVIEW 파일 기반 썸네일인지 확인 (실제 구현에 따라 검증)
        }
    }

    // ========================================================================================
    // [Category 2]: Read
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Diary")
    class Read {
        private Diary diaryPublicArchive_PublicDiary;
        private Diary diaryPublicArchive_RestrictedDiary;
        private Diary diaryPublicArchive_PrivateDiary;
        private Diary diaryRestrictedArchive_PublicDiary;
        private Diary diaryRestrictedArchive_RestrictedDiary;
        private Diary diaryRestrictedArchive_PrivateDiary;
        private Diary diaryPrivateArchive_PublicDiary;
        private Diary diaryPrivateArchive_RestrictedDiary;
        private Diary diaryPrivateArchive_PrivateDiary;

        @BeforeEach
        void initDiaries() {
            // Public Archive + 각 Visibility Diary
            diaryPublicArchive_PublicDiary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.PUBLIC, userAFiles.subList(0, 3));
            diaryPublicArchive_RestrictedDiary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.RESTRICTED, userAFiles.subList(3, 6));
            diaryPublicArchive_PrivateDiary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.PRIVATE, userAFiles.subList(6, 9));

            // Restricted Archive + 각 Visibility Diary
            diaryRestrictedArchive_PublicDiary = createDiaryByService(userA, archiveARestricted.getId(), Visibility.PUBLIC, null);
            diaryRestrictedArchive_RestrictedDiary = createDiaryByService(userA, archiveARestricted.getId(), Visibility.RESTRICTED, null);
            diaryRestrictedArchive_PrivateDiary = createDiaryByService(userA, archiveARestricted.getId(), Visibility.PRIVATE, null);

            // Private Archive + 각 Visibility Diary
            diaryPrivateArchive_PublicDiary = createDiaryByService(userA, archiveAPrivate.getId(), Visibility.PUBLIC, null);
            diaryPrivateArchive_RestrictedDiary = createDiaryByService(userA, archiveAPrivate.getId(), Visibility.RESTRICTED, null);
            diaryPrivateArchive_PrivateDiary = createDiaryByService(userA, archiveAPrivate.getId(), Visibility.PRIVATE, null);
        }

        @Test
        @DisplayName("SCENE 8~11: PUBLIC Archive + PUBLIC Diary (본인, 타인, 친구, 비회원)")
        void retrieveDiary_PublicArchive_PublicDiary() {
            // Scene 8: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPublicArchive_PublicDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPublicArchive_PublicDiary.getId());
            assertThat(resOwner.getFiles()).hasSize(3);

            // Scene 9: 타인 조회
            DiaryDto.Response resStranger = diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPublicArchive_PublicDiary.getId());
            assertThat(resStranger.getId()).isEqualTo(diaryPublicArchive_PublicDiary.getId());

            // Scene 10: 친구 조회
            DiaryDto.Response resFriend = diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPublicArchive_PublicDiary.getId());
            assertThat(resFriend.getId()).isEqualTo(diaryPublicArchive_PublicDiary.getId());

            // Scene 11: 비회원 조회
            DiaryDto.Response resAnon = diaryService.retrieveDiary(null, diaryPublicArchive_PublicDiary.getId());
            assertThat(resAnon.getId()).isEqualTo(diaryPublicArchive_PublicDiary.getId());
        }

        @Test
        @DisplayName("SCENE 12~15: PUBLIC Archive + RESTRICTED Diary")
        void retrieveDiary_PublicArchive_RestrictedDiary() {
            // Scene 12: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPublicArchive_RestrictedDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPublicArchive_RestrictedDiary.getId());

            // Scene 13: 친구 조회
            DiaryDto.Response resFriend = diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPublicArchive_RestrictedDiary.getId());
            assertThat(resFriend.getId()).isEqualTo(diaryPublicArchive_RestrictedDiary.getId());

            // Scene 14: 타인 조회 (No Friend) -> Fail
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPublicArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 15: 비회원 조회 -> Fail
            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryPublicArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 16~19: PUBLIC Archive + PRIVATE Diary")
        void retrieveDiary_PublicArchive_PrivateDiary() {
            // Scene 16: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPublicArchive_PrivateDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPublicArchive_PrivateDiary.getId());

            // Scene 17~19: 타인/친구/비회원 조회 -> 모두 Fail
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPublicArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPublicArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryPublicArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 20~23: RESTRICTED Archive + PUBLIC Diary")
        void retrieveDiary_RestrictedArchive_PublicDiary() {
            // Scene 20: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryRestrictedArchive_PublicDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryRestrictedArchive_PublicDiary.getId());

            // Scene 21: 친구 조회
            DiaryDto.Response resFriend = diaryService.retrieveDiary(UserPrincipal.from(userB), diaryRestrictedArchive_PublicDiary.getId());
            assertThat(resFriend.getId()).isEqualTo(diaryRestrictedArchive_PublicDiary.getId());

            // Scene 22: 타인 조회 (No Friend) -> Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryRestrictedArchive_PublicDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 23: 비회원 조회 -> Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryRestrictedArchive_PublicDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 24~27: RESTRICTED Archive + RESTRICTED Diary")
        void retrieveDiary_RestrictedArchive_RestrictedDiary() {
            // Scene 24: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryRestrictedArchive_RestrictedDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryRestrictedArchive_RestrictedDiary.getId());

            // Scene 25: 친구 조회
            DiaryDto.Response resFriend = diaryService.retrieveDiary(UserPrincipal.from(userB), diaryRestrictedArchive_RestrictedDiary.getId());
            assertThat(resFriend.getId()).isEqualTo(diaryRestrictedArchive_RestrictedDiary.getId());

            // Scene 26~27: 타인/비회원 조회 -> Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryRestrictedArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryRestrictedArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 28~31: RESTRICTED Archive + PRIVATE Diary")
        void retrieveDiary_RestrictedArchive_PrivateDiary() {
            // Scene 28: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryRestrictedArchive_PrivateDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryRestrictedArchive_PrivateDiary.getId());

            // Scene 29: 친구 조회 -> Fail (Diary 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userB), diaryRestrictedArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // Scene 30~31: 타인/비회원 조회 -> Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryRestrictedArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryRestrictedArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 32~35: PRIVATE Archive + PUBLIC Diary")
        void retrieveDiary_PrivateArchive_PublicDiary() {
            // Scene 32: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPrivateArchive_PublicDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPrivateArchive_PublicDiary.getId());

            // Scene 33~35: 타인/친구/비회원 조회 -> 모두 Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPrivateArchive_PublicDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPrivateArchive_PublicDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryPrivateArchive_PublicDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 36~39: PRIVATE Archive + RESTRICTED Diary")
        void retrieveDiary_PrivateArchive_RestrictedDiary() {
            // Scene 36: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPrivateArchive_RestrictedDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPrivateArchive_RestrictedDiary.getId());

            // Scene 37~39: 타인/친구/비회원 조회 -> 모두 Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPrivateArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPrivateArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryPrivateArchive_RestrictedDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 40~43: PRIVATE Archive + PRIVATE Diary")
        void retrieveDiary_PrivateArchive_PrivateDiary() {
            // Scene 40: 본인 조회
            DiaryDto.Response resOwner = diaryService.retrieveDiary(UserPrincipal.from(userA), diaryPrivateArchive_PrivateDiary.getId());
            assertThat(resOwner.getId()).isEqualTo(diaryPrivateArchive_PrivateDiary.getId());

            // Scene 41~43: 타인/친구/비회원 조회 -> 모두 Fail (Archive 레벨)
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userC), diaryPrivateArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userB), diaryPrivateArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            assertThatThrownBy(() -> diaryService.retrieveDiary(null, diaryPrivateArchive_PrivateDiary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 44~45: Not Found Cases")
        void retrieveDiary_NotFound() {
            // Scene 44: 존재하지 않는 Archive (DiaryBook 조회 실패)
            // 실제로는 DiaryBook이 없으면 Archive 조회 시 실패하므로, 존재하지 않는 archiveId로 Diary 생성 시도는 Create에서 검증됨
            // 여기서는 존재하지 않는 diaryId만 검증

            // Scene 45: 존재하지 않는 Diary
            assertThatThrownBy(() -> diaryService.retrieveDiary(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DIARY_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 3]: Update
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Update Diary")
    class Update {
        private Diary diary;

        @BeforeEach
        void init() {
            diary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.PUBLIC, userAFiles.subList(0, 3));
        }

        @Test
        @DisplayName("SCENE 46: 정상 케이스 (제목, 내용, 공개범위 수정)")
        void updateDiary_Normal() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setTitle("Updated Title");
            request.setContent("Updated Content");
            request.setVisibility(Visibility.PRIVATE);

            // When
            diaryService.updateDiary(principal, diary.getId(), request);

            // Then
            Diary updated = diaryRepository.findById(diary.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Updated Title");
            assertThat(updated.getContent()).isEqualTo("Updated Content");
            assertThat(updated.getVisibility()).isEqualTo(Visibility.PRIVATE);
        }

        @Test
        @DisplayName("SCENE 47: 파일 전체 교체")
        void updateDiary_ReplaceFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> newFiles = new ArrayList<>();
            newFiles.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userAFiles.get(9).getId())
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(0)
                    .build());

            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setFiles(newFiles);

            // When
            diaryService.updateDiary(principal, diary.getId(), request);

            // Then
            List<DiaryFileMap> maps = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diary.getId());
            assertThat(maps).hasSize(1);
            assertThat(maps.get(0).getFile().getId()).isEqualTo(userAFiles.get(9).getId());
        }

        @Test
        @DisplayName("SCENE 48: 파일 삭제 (빈 리스트)")
        void updateDiary_DeleteFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setFiles(new ArrayList<>());

            // When
            diaryService.updateDiary(principal, diary.getId(), request);

            // Then
            List<DiaryFileMap> maps = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diary.getId());
            assertThat(maps).isEmpty();
            Diary updated = diaryRepository.findById(diary.getId()).orElseThrow();
            assertThat(updated.getThumbnailKey()).isNull();
        }

        @Test
        @DisplayName("SCENE 49: 파일 유지 (파일 목록 = null)")
        void updateDiary_KeepFiles() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            int originalFileCount = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diary.getId()).size();

            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setTitle("Updated");
            request.setFiles(null); // 유지

            // When
            diaryService.updateDiary(principal, diary.getId(), request);

            // Then
            List<DiaryFileMap> maps = diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diary.getId());
            assertThat(maps).hasSize(originalFileCount);
        }

        @Test
        @DisplayName("SCENE 50: 타인 수정 시도")
        void updateDiary_Forbidden() {
            // Given
            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setTitle("Hacked");

            // When & Then
            assertThatThrownBy(() -> diaryService.updateDiary(UserPrincipal.from(userC), diary.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 51: 존재하지 않는 Diary")
        void updateDiary_NotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();

            // When & Then
            assertThatThrownBy(() -> diaryService.updateDiary(principal, 99999L, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DIARY_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 52: 다른 사용자의 파일 사용 시도 (IDOR)")
        void updateDiary_IDOR() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            List<DiaryDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(DiaryDto.AttachedFileRequest.builder()
                    .fileId(userCFiles.get(0).getId()) // UserC의 파일
                    .mediaRole(MediaRole.CONTENT)
                    .sequence(0)
                    .build());

            DiaryDto.UpdateRequest request = new DiaryDto.UpdateRequest();
            request.setFiles(files);

            // When & Then
            assertThatThrownBy(() -> diaryService.updateDiary(principal, diary.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 4]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Delete Diary")
    class Delete {
        @Test
        @DisplayName("SCENE 53: 정상 케이스")
        void deleteDiary_Normal() {
            // Given
            Diary diary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.PUBLIC, userAFiles.subList(0, 3));
            Long diaryId = diary.getId();

            // When
            flushAndClear();
            diaryService.deleteDiary(UserPrincipal.from(userA), diaryId);
            flushAndClear();

            // Then
            assertThat(diaryRepository.existsById(diaryId)).isFalse();
            assertThat(diaryFileMapRepository.findAllByDiaryIdOrderBySequenceAsc(diaryId)).isEmpty();
        }

        @Test
        @DisplayName("SCENE 54: 타인 삭제 시도")
        void deleteDiary_Forbidden() {
            // Given
            Diary diary = createDiaryByService(userA, archiveAPublic.getId(), Visibility.PUBLIC, null);

            // When & Then
            assertThatThrownBy(() -> diaryService.deleteDiary(UserPrincipal.from(userC), diary.getId()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 55: 존재하지 않는 Diary")
        void deleteDiary_NotFound() {
            // When & Then
            assertThatThrownBy(() -> diaryService.deleteDiary(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DIARY_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 5]: Update Book Title
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Update DiaryBook Title")
    class UpdateBookTitle {
        @Test
        @DisplayName("SCENE 56: 정상 케이스")
        void updateDiaryBookTitle_Normal() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            DiaryDto.UpdateBookTitleRequest request = new DiaryDto.UpdateBookTitleRequest();
            request.setTitle("Updated Book Title");

            // When
            DiaryDto.UpdateBookTitleResponse response = diaryService.updateDiaryBookTitle(principal, archiveAPublic.getId(), request);

            // Then
            DiaryBook book = diaryBookRepository.findById(archiveAPublic.getId()).orElseThrow();
            assertThat(book.getTitle()).isEqualTo("Updated Book Title");
            assertThat(response.getUpdatedTitle()).isEqualTo("Updated Book Title");
        }

        @Test
        @DisplayName("SCENE 57: 타인 수정 시도")
        void updateDiaryBookTitle_Forbidden() {
            // Given
            DiaryDto.UpdateBookTitleRequest request = new DiaryDto.UpdateBookTitleRequest();
            request.setTitle("Hacked");

            // When & Then
            assertThatThrownBy(() -> diaryService.updateDiaryBookTitle(UserPrincipal.from(userC), archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 58: 존재하지 않는 DiaryBook")
        void updateDiaryBookTitle_NotFound() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            DiaryDto.UpdateBookTitleRequest request = new DiaryDto.UpdateBookTitleRequest();
            request.setTitle("Test");

            // When & Then
            assertThatThrownBy(() -> diaryService.updateDiaryBookTitle(principal, 99999L, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 6]: Read-Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] Pagination (getDiaries)")
    class Pagination {
        @BeforeEach
        void setUpPagination() {
            // UserA의 ArchiveAPublic에 각 Visibility별 Diary 10개씩 생성
            for (int i = 0; i < 10; i++) {
                createDiaryByService(userA, archiveAPublic.getId(), Visibility.PUBLIC, null);
                createDiaryByService(userA, archiveAPublic.getId(), Visibility.RESTRICTED, null);
                createDiaryByService(userA, archiveAPublic.getId(), Visibility.PRIVATE, null);
            }
        }

        @Test
        @DisplayName("SCENE 59: 본인 조회 (전체 공개범위)")
        void getDiaries_Owner() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
            request.setPage(0);
            request.setSize(12);

            // When
            PageDto.PageListResponse<DiaryDto.DiaryPageResponse> response = 
                    diaryService.getDiaries(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            // Then
            assertThat(response.getPage().getTotalElements()).isEqualTo(30); // 10 + 10 + 10
            assertThat(response.getTitle()).contains("다이어리");
        }

        @Test
        @DisplayName("SCENE 60: 친구 조회 (PUBLIC, RESTRICTED)")
        void getDiaries_Friend() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
            request.setPage(0);
            request.setSize(12);

            // When
            PageDto.PageListResponse<DiaryDto.DiaryPageResponse> response = 
                    diaryService.getDiaries(UserPrincipal.from(userB), archiveAPublic.getId(), request);

            // Then
            assertThat(response.getPage().getTotalElements()).isEqualTo(20); // PUBLIC 10 + RESTRICTED 10
            assertThat(response.getContent()).allMatch(d -> 
                    d.getVisibility() == Visibility.PUBLIC || d.getVisibility() == Visibility.RESTRICTED);
        }

        @Test
        @DisplayName("SCENE 61: 비친구 조회 (PUBLIC만)")
        void getDiaries_Stranger() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
            request.setPage(0);
            request.setSize(12);

            // When
            PageDto.PageListResponse<DiaryDto.DiaryPageResponse> response = 
                    diaryService.getDiaries(UserPrincipal.from(userC), archiveAPublic.getId(), request);

            // Then
            assertThat(response.getPage().getTotalElements()).isEqualTo(10); // PUBLIC만
            assertThat(response.getContent()).allMatch(d -> d.getVisibility() == Visibility.PUBLIC);
        }

        @Test
        @DisplayName("SCENE 62: 비회원 조회 (PUBLIC만)")
        void getDiaries_Anonymous() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
            request.setPage(0);
            request.setSize(12);

            // When
            PageDto.PageListResponse<DiaryDto.DiaryPageResponse> result = 
                    diaryService.getDiaries(null, archiveAPublic.getId(), request);

            // Then
            assertThat(result.getPage().getTotalElements()).isEqualTo(10); // PUBLIC만
            assertThat(result.getContent()).allMatch(d -> d.getVisibility() == Visibility.PUBLIC);
        }

        @Test
        @DisplayName("SCENE 63: Archive 권한 없음")
        void getDiaries_ArchiveForbidden() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();

            // When & Then
            assertThatThrownBy(() -> diaryService.getDiaries(UserPrincipal.from(userC), archiveAPrivate.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 64: 존재하지 않는 Archive")
        void getDiaries_ArchiveNotFound() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();

            // When & Then
            assertThatThrownBy(() -> diaryService.getDiaries(UserPrincipal.from(userA), 99999L, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 65: 빈 결과")
        void getDiaries_Empty() {
            // Given: 새로운 Archive (Diary 없음)
            Archive newArchive = createArchiveByService(userA, Visibility.PUBLIC, null);
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();

            // When
            PageDto.PageListResponse<DiaryDto.DiaryPageResponse> response = 
                    diaryService.getDiaries(UserPrincipal.from(userA), newArchive.getId(), request);

            // Then
            assertThat(response.getPage().getTotalElements()).isZero();
        }

        @Test
        @DisplayName("SCENE 66: 페이지 범위 초과")
        void getDiaries_PageOutOfRange() {
            // Given
            DiaryDto.DiaryPageRequest request = new DiaryDto.DiaryPageRequest();
            request.setPage(1000);

            // When & Then
            assertThatThrownBy(() -> diaryService.getDiaries(UserPrincipal.from(userA), archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);
        }
    }

    // --- Helper for creating diaries via Service ---
    private Diary createDiaryByService(User owner, Long archiveId, Visibility visibility, List<File> files) {
        setupMockUser(owner);
        UserPrincipal principal = UserPrincipal.from(owner);

        DiaryDto.CreateRequest request = new DiaryDto.CreateRequest();
        request.setTitle("Diary " + visibility);
        request.setContent("Content");
        request.setRecordedAt(LocalDate.now());
        request.setColor("#FF5733");
        request.setVisibility(visibility);

        if (files != null && !files.isEmpty()) {
            List<DiaryDto.AttachedFileRequest> fileRequests = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                fileRequests.add(DiaryDto.AttachedFileRequest.builder()
                        .fileId(files.get(i).getId())
                        .mediaRole(i == 0 ? MediaRole.PREVIEW : MediaRole.CONTENT)
                        .sequence(i)
                        .build());
            }
            request.setFiles(fileRequests);
        }

        DiaryDto.Response response = diaryService.createDiary(principal, archiveId, request);
        SecurityContextHolder.clearContext();
        return diaryRepository.findById(response.getId()).orElseThrow();
    }
}
