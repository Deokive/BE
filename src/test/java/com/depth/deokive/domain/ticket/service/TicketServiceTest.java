package com.depth.deokive.domain.ticket.service;

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
import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TicketService 통합 테스트")
class TicketServiceTest extends IntegrationTestSupport {

    @Autowired TicketService ticketService;
    @Autowired ArchiveService archiveService;

    // Core Repositories
    @Autowired TicketRepository ticketRepository;
    @Autowired TicketBookRepository ticketBookRepository;
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

        // 4. Files Setup
        setupMockUser(userA);
        userAFiles = createFiles(userA, 10);
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
            String uuid = UUID.randomUUID().toString();
            File file = fileRepository.save(File.builder()
                    .filename("file_" + uuid + ".jpg")
                    .s3ObjectKey("tickets/" + owner.getNickname() + "/" + uuid + ".jpg")
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
    @DisplayName("[Category 1] Create Ticket")
    class Create {

        @Test
        @DisplayName("SCENE 1: 정상 케이스 (파일 포함, PUBLIC Archive)")
        void createTicket_WithFile_Public() {
            // Given
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();

            // When
            TicketDto.Response response = ticketService.createTicket(principal, archiveAPublic.getId(), request);

            // Then
            Ticket ticket = ticketRepository.findById(response.getId()).orElseThrow();
            assertThat(ticket.getTicketBook().getId()).isEqualTo(archiveAPublic.getId());
            assertThat(ticket.getFile()).isNotNull();
            assertThat(ticket.getFile().getId()).isEqualTo(userAFiles.get(0).getId());
        }

        @Test
        @DisplayName("SCENE 2: 정상 케이스 (파일 포함, RESTRICTED Archive)")
        void createTicket_WithFile_Restricted() {
            // Given
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();

            // When
            TicketDto.Response response = ticketService.createTicket(UserPrincipal.from(userA), archiveARestricted.getId(), request);

            // Then
            assertThat(ticketRepository.existsById(response.getId())).isTrue();
            assertThat(response.getFile()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 3: 정상 케이스 (파일 포함, PRIVATE Archive)")
        void createTicket_WithFile_Private() {
            // Given
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();

            // When
            TicketDto.Response response = ticketService.createTicket(UserPrincipal.from(userA), archiveAPrivate.getId(), request);

            // Then
            assertThat(ticketRepository.existsById(response.getId())).isTrue();
            assertThat(response.getFile()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 4: 파일 없이 생성 (PUBLIC Archive)")
        void createTicket_NoFile_Public() {
            // Given
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(null).build();

            // When
            TicketDto.Response response = ticketService.createTicket(UserPrincipal.from(userA), archiveAPublic.getId(), request);

            // Then
            Ticket ticket = ticketRepository.findById(response.getId()).orElseThrow();
            assertThat(ticket.getFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 5: 파일 없이 생성 (RESTRICTED Archive)")
        void createTicket_NoFile_Restricted() {
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(null).build();

            TicketDto.Response response = ticketService.createTicket(UserPrincipal.from(userA), archiveARestricted.getId(), request);
            assertThat(ticketRepository.findById(response.getId()).get().getFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 6: 파일 없이 생성 (PRIVATE Archive)")
        void createTicket_NoFile_Private() {
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("Concert").date(LocalDateTime.now()).fileId(null).build();

            TicketDto.Response response = ticketService.createTicket(UserPrincipal.from(userA), archiveAPrivate.getId(), request);
            assertThat(ticketRepository.findById(response.getId()).get().getFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 7: 존재하지 않는 Archive")
        void createTicket_ArchiveNotFound() {
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder().title("T").date(LocalDateTime.now()).build();

            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userA), 99999L, request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 8~11: 권한 예외 케이스")
        void createTicket_Forbidden() {
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder().title("T").date(LocalDateTime.now()).build();

            // 8: Stranger -> Public (Forbidden)
            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userC), archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 9: Stranger -> Restricted (Forbidden)
            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userC), archiveARestricted.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 10: Stranger -> Private (Forbidden)
            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userC), archiveAPrivate.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 11: Friend -> Restricted (Read-only)
            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userB), archiveARestricted.getId(), request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("SCENE 12: 다른 사용자의 파일 사용 시도")
        void createTicket_IDOR() {
            setupMockUser(userA);
            TicketDto.CreateRequest request = TicketDto.CreateRequest.builder()
                    .title("IDOR").date(LocalDateTime.now()).fileId(userCFiles.get(0).getId()).build();

            // Service: fileService.validateFileOwner calls -> AUTH_FORBIDDEN
            assertThatThrownBy(() -> ticketService.createTicket(UserPrincipal.from(userA), archiveAPublic.getId(), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 2]: Read
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Ticket")
    class Read {
        private Ticket ticketWithFile;
        private Ticket ticketNoFile;

        @BeforeEach
        void initTickets() {
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);

            // Ticket with file
            TicketDto.CreateRequest req1 = TicketDto.CreateRequest.builder()
                    .title("With File").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();
            TicketDto.Response res1 = ticketService.createTicket(principal, archiveAPublic.getId(), req1);
            ticketWithFile = ticketRepository.findById(res1.getId()).orElseThrow();

            // Ticket no file
            TicketDto.CreateRequest req2 = TicketDto.CreateRequest.builder()
                    .title("No File").date(LocalDateTime.now()).fileId(null).build();
            TicketDto.Response res2 = ticketService.createTicket(principal, archiveAPublic.getId(), req2);
            ticketNoFile = ticketRepository.findById(res2.getId()).orElseThrow();

            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 13~16: PUBLIC Archive (파일 포함)")
        void getTicket_Public_WithFile() {
            // 13: Owner
            TicketDto.Response resOwner = ticketService.getTicket(UserPrincipal.from(userA), ticketWithFile.getId());
            assertThat(resOwner.getFile()).isNotNull();
            assertThat(resOwner.getFile().getCdnUrl()).isNotNull();

            // 14: Stranger
            assertThat(ticketService.getTicket(UserPrincipal.from(userC), ticketWithFile.getId()).getFile()).isNotNull();
            // 15: Friend
            assertThat(ticketService.getTicket(UserPrincipal.from(userB), ticketWithFile.getId()).getFile()).isNotNull();
            // 16: Anonymous
            assertThat(ticketService.getTicket(null, ticketWithFile.getId()).getFile()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 17: PUBLIC Archive (파일 없음)")
        void getTicket_Public_NoFile() {
            TicketDto.Response res = ticketService.getTicket(UserPrincipal.from(userA), ticketNoFile.getId());
            assertThat(res.getFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 18~21: RESTRICTED Archive")
        void getTicket_Restricted() {
            // Setup Restricted Ticket
            setupMockUser(userA);
            TicketDto.Response res = ticketService.createTicket(UserPrincipal.from(userA), archiveARestricted.getId(),
                    TicketDto.CreateRequest.builder().title("R").date(LocalDateTime.now()).fileId(userAFiles.get(1).getId()).build());
            Long ticketId = res.getId();
            flushAndClear();

            // 18: Owner OK
            assertThat(ticketService.getTicket(UserPrincipal.from(userA), ticketId).getFile()).isNotNull();
            // 19: Friend OK
            assertThat(ticketService.getTicket(UserPrincipal.from(userB), ticketId).getFile()).isNotNull();

            // 20: Stranger Fail
            assertThatThrownBy(() -> ticketService.getTicket(UserPrincipal.from(userC), ticketId)).isInstanceOf(RestException.class);
            // 21: Anonymous Fail
            assertThatThrownBy(() -> ticketService.getTicket(null, ticketId)).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 22~25: PRIVATE Archive")
        void getTicket_Private() {
            // Setup Private Ticket
            setupMockUser(userA);
            TicketDto.Response res = ticketService.createTicket(UserPrincipal.from(userA), archiveAPrivate.getId(),
                    TicketDto.CreateRequest.builder().title("P").date(LocalDateTime.now()).fileId(userAFiles.get(2).getId()).build());
            Long ticketId = res.getId();
            flushAndClear();

            // 22: Owner OK
            assertThat(ticketService.getTicket(UserPrincipal.from(userA), ticketId).getFile()).isNotNull();

            // 23~25: Others Fail
            assertThatThrownBy(() -> ticketService.getTicket(UserPrincipal.from(userC), ticketId)).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> ticketService.getTicket(UserPrincipal.from(userB), ticketId)).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> ticketService.getTicket(null, ticketId)).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 26: 존재하지 않는 Ticket")
        void getTicket_NotFound() {
            assertThatThrownBy(() -> ticketService.getTicket(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 3]: Read-Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Read Ticket List")
    class ReadPagination {
        @BeforeEach
        void initTickets() {
            setupMockUser(userA);
            UserPrincipal principal = UserPrincipal.from(userA);
            for(int i=0; i<10; i++) {
                TicketDto.CreateRequest req = TicketDto.CreateRequest.builder()
                        .title("Ticket " + i).date(LocalDateTime.now())
                        .fileId(userAFiles.get(i).getId()) // Use files 0-9
                        .build();
                ticketService.createTicket(principal, archiveAPublic.getId(), req);
                ticketService.createTicket(principal, archiveARestricted.getId(), req);
                ticketService.createTicket(principal, archiveAPrivate.getId(), req);
            }
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 27~30: PUBLIC Archive")
        void getTickets_Public() {
            TicketDto.TicketPageRequest req = new TicketDto.TicketPageRequest();
            req.setPage(0); req.setSize(20);

            // 27: Owner
            PageDto.PageListResponse<TicketDto.TicketPageResponse> resOwner = ticketService.getTickets(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable());
            assertThat(resOwner.getContent()).hasSize(10);
            assertThat(resOwner.getContent().get(0).getThumbnail()).isNotNull();

            // 정렬 검증: 기본 정렬은 createdAt DESC (최신순)
            List<TicketDto.TicketPageResponse> content = resOwner.getContent();
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getCreatedAt())
                        .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
            }

            // 28, 29, 30: Others
            assertThat(ticketService.getTickets(UserPrincipal.from(userC), archiveAPublic.getId(), req.toPageable()).getContent()).hasSize(10);
            assertThat(ticketService.getTickets(UserPrincipal.from(userB), archiveAPublic.getId(), req.toPageable()).getContent()).hasSize(10);
            assertThat(ticketService.getTickets(null, archiveAPublic.getId(), req.toPageable()).getContent()).hasSize(10);
        }

        @Test
        @DisplayName("SCENE 31~34: RESTRICTED Archive")
        void getTickets_Restricted() {
            TicketDto.TicketPageRequest req = new TicketDto.TicketPageRequest();

            // 31: Owner OK
            assertThat(ticketService.getTickets(UserPrincipal.from(userA), archiveARestricted.getId(), req.toPageable()).getContent()).hasSize(10);
            // 32: Friend OK
            assertThat(ticketService.getTickets(UserPrincipal.from(userB), archiveARestricted.getId(), req.toPageable()).getContent()).hasSize(10);

            // 33: Stranger Fail
            assertThatThrownBy(() -> ticketService.getTickets(UserPrincipal.from(userC), archiveARestricted.getId(), req.toPageable())).isInstanceOf(RestException.class);
            // 34: Anonymous Fail
            assertThatThrownBy(() -> ticketService.getTickets(null, archiveARestricted.getId(), req.toPageable())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 35~38: PRIVATE Archive")
        void getTickets_Private() {
            TicketDto.TicketPageRequest req = new TicketDto.TicketPageRequest();

            // 35: Owner OK
            assertThat(ticketService.getTickets(UserPrincipal.from(userA), archiveAPrivate.getId(), req.toPageable()).getContent()).hasSize(10);

            // 36~38: Others Fail
            assertThatThrownBy(() -> ticketService.getTickets(UserPrincipal.from(userC), archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> ticketService.getTickets(UserPrincipal.from(userB), archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
            assertThatThrownBy(() -> ticketService.getTickets(null, archiveAPrivate.getId(), req.toPageable())).isInstanceOf(RestException.class);
        }

        @Test
        @DisplayName("SCENE 39~42: Edge Cases")
        void getTickets_Edge() {
            TicketDto.TicketPageRequest req = new TicketDto.TicketPageRequest();

            // 39: Not Found Archive
            assertThatThrownBy(() -> ticketService.getTickets(UserPrincipal.from(userA), 99999L, req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);

            // 40: Empty
            Archive newArchive = createArchiveByService(userA, Visibility.PUBLIC);
            assertThat(ticketService.getTickets(UserPrincipal.from(userA), newArchive.getId(), req.toPageable()).getContent()).isEmpty();

            // 41: Page Out Range
            req.setPage(100);
            assertThatThrownBy(() -> ticketService.getTickets(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);

            // 42: Pagination Check
            req.setPage(0); req.setSize(5);
            PageDto.PageListResponse<TicketDto.TicketPageResponse> page1 = ticketService.getTickets(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable());
            assertThat(page1.getContent()).hasSize(5);
            assertThat(page1.getPage().getTotalElements()).isEqualTo(10);
            assertThat(page1.getPage().getTotalPages()).isEqualTo(2);

            // 두 번째 페이지 검증
            req.setPage(1);
            PageDto.PageListResponse<TicketDto.TicketPageResponse> page2 = ticketService.getTickets(UserPrincipal.from(userA), archiveAPublic.getId(), req.toPageable());
            assertThat(page2.getContent()).hasSize(5);
            
            // 페이지 간 중복 없음 검증
            List<Long> page1Ids = page1.getContent().stream().map(TicketDto.TicketPageResponse::getId).toList();
            List<Long> page2Ids = page2.getContent().stream().map(TicketDto.TicketPageResponse::getId).toList();
            assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
        }
    }

    // ========================================================================================
    // [Category 4]: Update
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Update Ticket")
    class Update {
        private Ticket ticket;

        @BeforeEach
        void init() {
            setupMockUser(userA);
            TicketDto.CreateRequest req = TicketDto.CreateRequest.builder()
                    .title("Old").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();
            TicketDto.Response res = ticketService.createTicket(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            ticket = ticketRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 43~46: 정상 수정 (필드별)")
        void updateTicket_Fields() {
            // Given: 초기 상태 저장
            Ticket original = ticketRepository.findById(ticket.getId()).get();
            Long originalFileId = original.getFile().getId();

            // 43: Full Update
            LocalDateTime newDate = LocalDateTime.of(2025, 12, 25, 14, 30);
            TicketDto.UpdateRequest req = TicketDto.UpdateRequest.builder()
                    .title("New")
                    .review("Great")
                    .date(newDate)
                    .location("Seoul")
                    .seat("A-10")
                    .build();
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), req);
            flushAndClear();

            Ticket updated = ticketRepository.findById(ticket.getId()).get();
            assertThat(updated.getTitle()).isEqualTo("New");
            assertThat(updated.getReview()).isEqualTo("Great");
            assertThat(updated.getDate()).isEqualTo(newDate);
            assertThat(updated.getLocation()).isEqualTo("Seoul");
            assertThat(updated.getSeat()).isEqualTo("A-10");
            assertThat(updated.getFile().getId()).isEqualTo(originalFileId); // File Kept

            // 44: Title Only - 다른 필드 보존 검증
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), 
                    TicketDto.UpdateRequest.builder().title("T").build());
            flushAndClear();

            Ticket afterTitleUpdate = ticketRepository.findById(ticket.getId()).get();
            assertThat(afterTitleUpdate.getTitle()).isEqualTo("T");
            assertThat(afterTitleUpdate.getReview()).isEqualTo("Great"); // 보존 확인
            assertThat(afterTitleUpdate.getDate()).isEqualTo(newDate); // 보존 확인
            assertThat(afterTitleUpdate.getLocation()).isEqualTo("Seoul"); // 보존 확인
            assertThat(afterTitleUpdate.getFile().getId()).isEqualTo(originalFileId); // 보존 확인

            // 45: Review Only - 다른 필드 보존 검증
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), 
                    TicketDto.UpdateRequest.builder().review("Updated Review").build());
            flushAndClear();

            Ticket afterReviewUpdate = ticketRepository.findById(ticket.getId()).get();
            assertThat(afterReviewUpdate.getTitle()).isEqualTo("T"); // 보존 확인
            assertThat(afterReviewUpdate.getReview()).isEqualTo("Updated Review");
            assertThat(afterReviewUpdate.getDate()).isEqualTo(newDate); // 보존 확인

            // 46: Date Only - 다른 필드 보존 검증
            LocalDateTime anotherDate = LocalDateTime.of(2026, 1, 1, 10, 0);
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), 
                    TicketDto.UpdateRequest.builder().date(anotherDate).build());
            flushAndClear();

            Ticket afterDateUpdate = ticketRepository.findById(ticket.getId()).get();
            assertThat(afterDateUpdate.getTitle()).isEqualTo("T"); // 보존 확인
            assertThat(afterDateUpdate.getReview()).isEqualTo("Updated Review"); // 보존 확인
            assertThat(afterDateUpdate.getDate()).isEqualTo(anotherDate);
        }

        @Test
        @DisplayName("SCENE 47: 파일 교체")
        void updateTicket_ReplaceFile() {
            TicketDto.UpdateRequest req = TicketDto.UpdateRequest.builder().fileId(userAFiles.get(1).getId()).build();
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), req);
            flushAndClear();

            assertThat(ticketRepository.findById(ticket.getId()).get().getFile().getId()).isEqualTo(userAFiles.get(1).getId());
        }

        @Test
        @DisplayName("SCENE 48: 파일 추가 (기존 없음 -> 있음)")
        void updateTicket_AddFile() {
            // Setup no file ticket
            TicketDto.Response res = ticketService.createTicket(UserPrincipal.from(userA), archiveAPublic.getId(),
                    TicketDto.CreateRequest.builder().title("NoFile").date(LocalDateTime.now()).build());
            flushAndClear();

            TicketDto.UpdateRequest req = TicketDto.UpdateRequest.builder().fileId(userAFiles.get(0).getId()).build();
            ticketService.updateTicket(UserPrincipal.from(userA), res.getId(), req);
            flushAndClear();

            assertThat(ticketRepository.findById(res.getId()).get().getFile()).isNotNull();
        }

        @Test
        @DisplayName("SCENE 49: 파일 삭제 (deleteFile=true)")
        void updateTicket_DeleteFile() {
            TicketDto.UpdateRequest req = TicketDto.UpdateRequest.builder().deleteFile(true).build();
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), req);
            flushAndClear();

            assertThat(ticketRepository.findById(ticket.getId()).get().getFile()).isNull();
        }

        @Test
        @DisplayName("SCENE 50~51: 파일 유지")
        void updateTicket_KeepFile() {
            // Given: 초기 파일 상태 저장
            Ticket original = ticketRepository.findById(ticket.getId()).get();
            Long originalFileId = original.getFile().getId();
            String originalOriginalKey = original.getOriginalKey();

            // 50: fileId=null, deleteFile=null -> 파일 유지
            TicketDto.UpdateRequest req1 = TicketDto.UpdateRequest.builder().title("U").build();
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), req1);
            flushAndClear();

            Ticket afterUpdate1 = ticketRepository.findById(ticket.getId()).get();
            assertThat(afterUpdate1.getFile()).isNotNull();
            assertThat(afterUpdate1.getFile().getId()).isEqualTo(originalFileId); // 동일한 파일 유지
            assertThat(afterUpdate1.getOriginalKey()).isEqualTo(originalOriginalKey); // OriginalKey도 유지
            assertThat(afterUpdate1.getTitle()).isEqualTo("U"); // 제목은 업데이트됨

            // 51: deleteFile=false (명시적 유지) -> 파일 유지
            TicketDto.UpdateRequest req2 = TicketDto.UpdateRequest.builder()
                    .title("U2")
                    .deleteFile(false)
                    .build();
            ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), req2);
            flushAndClear();

            Ticket afterUpdate2 = ticketRepository.findById(ticket.getId()).get();
            assertThat(afterUpdate2.getFile()).isNotNull();
            assertThat(afterUpdate2.getFile().getId()).isEqualTo(originalFileId); // 여전히 유지
        }

        @Test
        @DisplayName("SCENE 52~57: 예외 케이스")
        void updateTicket_Exceptions() {
            TicketDto.UpdateRequest req = TicketDto.UpdateRequest.builder().title("Hacked").build();

            // 52: Forbidden (Stranger)
            assertThatThrownBy(() -> ticketService.updateTicket(UserPrincipal.from(userC), ticket.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 56: Not Found
            assertThatThrownBy(() -> ticketService.updateTicket(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);

            // 57: IDOR
            TicketDto.UpdateRequest idorReq = TicketDto.UpdateRequest.builder().fileId(userCFiles.get(0).getId()).build();
            assertThatThrownBy(() -> ticketService.updateTicket(UserPrincipal.from(userA), ticket.getId(), idorReq))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 5]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Delete Ticket")
    class Delete {
        private Ticket ticket;

        @BeforeEach
        void init() {
            setupMockUser(userA);
            TicketDto.CreateRequest req = TicketDto.CreateRequest.builder()
                    .title("Del").date(LocalDateTime.now()).fileId(userAFiles.get(0).getId()).build();
            TicketDto.Response res = ticketService.createTicket(UserPrincipal.from(userA), archiveAPublic.getId(), req);
            ticket = ticketRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 58~62: 정상 삭제")
        void deleteTicket_Normal() {
            ticketService.deleteTicket(UserPrincipal.from(userA), ticket.getId());
            flushAndClear();

            assertThat(ticketRepository.existsById(ticket.getId())).isFalse();
            assertThat(fileRepository.existsById(userAFiles.get(0).getId())).isTrue(); // File remains
        }

        @Test
        @DisplayName("SCENE 63~67: 예외 케이스")
        void deleteTicket_Exceptions() {
            // 63: Forbidden
            assertThatThrownBy(() -> ticketService.deleteTicket(UserPrincipal.from(userC), ticket.getId()))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 67: Not Found
            assertThatThrownBy(() -> ticketService.deleteTicket(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 6]: Update Book Title
    // ========================================================================================
    @Nested
    @DisplayName("[Category 6] Update TicketBook Title")
    class UpdateBookTitle {
        @Test
        @DisplayName("SCENE 68~70: 정상 수정")
        void updateBookTitle_Normal() {
            setupMockUser(userA);
            TicketDto.UpdateBookTitleRequest req = new TicketDto.UpdateBookTitleRequest(); req.setTitle("New Book");
            TicketDto.UpdateBookTitleResponse res = ticketService.updateTicketBookTitle(UserPrincipal.from(userA), archiveAPublic.getId(), req);

            assertThat(res.getUpdatedTitle()).isEqualTo("New Book");
            assertThat(ticketBookRepository.findById(archiveAPublic.getId()).get().getTitle()).isEqualTo("New Book");
        }

        @Test
        @DisplayName("SCENE 71~75: 예외 케이스")
        void updateBookTitle_Exceptions() {
            setupMockUser(userA);
            TicketDto.UpdateBookTitleRequest req = new TicketDto.UpdateBookTitleRequest(); req.setTitle("Hack");

            // 71: Forbidden
            assertThatThrownBy(() -> ticketService.updateTicketBookTitle(UserPrincipal.from(userC), archiveAPublic.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 75: Not Found
            assertThatThrownBy(() -> ticketService.updateTicketBookTitle(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
        }
    }
}