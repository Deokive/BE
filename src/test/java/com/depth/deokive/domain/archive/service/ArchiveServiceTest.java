package com.depth.deokive.domain.archive.service;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.*;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.archive.repository.*;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.event.repository.EventHashtagMapRepository;
import com.depth.deokive.domain.event.repository.EventRepository;
import com.depth.deokive.domain.event.repository.SportRecordRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.post.entity.RepostBook;
import com.depth.deokive.domain.post.repository.RepostBookRepository;
import com.depth.deokive.domain.post.repository.RepostRepository;
import com.depth.deokive.domain.post.repository.RepostTabRepository;
import com.depth.deokive.domain.sticker.repository.StickerRepository;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import com.depth.deokive.domain.ticket.repository.TicketBookRepository;
import com.depth.deokive.domain.ticket.repository.TicketRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @InjectMocks private ArchiveService archiveService;

    // --- Core Mocks (Refactored: FileMapRepository removed) ---
    @Mock private ArchiveRepository archiveRepository;
    @Mock private ArchiveViewCountRepository viewCountRepository;
    @Mock private ArchiveLikeCountRepository likeCountRepository;
    @Mock private ArchiveLikeRepository likeRepository;
    @Mock private FileRepository fileRepository;
    @Mock private UserRepository userRepository;

    // --- Sub-Book Mocks ---
    @Mock private DiaryBookRepository diaryBookRepository;
    @Mock private GalleryBookRepository galleryBookRepository;
    @Mock private TicketBookRepository ticketBookRepository;
    @Mock private RepostBookRepository repostBookRepository;

    // --- Bulk Delete Mocks ---
    @Mock private EventRepository eventRepository;
    @Mock private EventHashtagMapRepository eventHashtagMapRepository;
    @Mock private SportRecordRepository sportRecordRepository;
    @Mock private DiaryRepository diaryRepository;
    @Mock private DiaryFileMapRepository diaryFileMapRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private GalleryRepository galleryRepository;
    @Mock private RepostRepository repostRepository;
    @Mock private RepostTabRepository repostTabRepository;
    @Mock private StickerRepository stickerRepository;

    // --- Fixture Helpers ---
    private UserPrincipal makePrincipal(Long userId) {
        return UserPrincipal.builder().userId(userId).role(Role.USER).build();
    }

    private User createUser(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "nickname", "User" + id);
        return user;
    }

    private Archive createArchive(Long id, User owner, Visibility visibility) {
        Archive archive = Archive.builder()
                .user(owner)
                .title("Test Archive")
                .visibility(visibility)
                .build();
        ReflectionTestUtils.setField(archive, "id", id);
        return archive;
    }

    @Nested
    @DisplayName("아카이브 생성 (Create)")
    class CreateTest {

        @Test
        @DisplayName("성공: 아카이브 생성 시 4개의 하위 Book이 제목(Title)과 함께 자동 생성되어야 한다.")
        void createArchive_Success_WithBooks() {
            // given
            Long userId = 1L;
            UserPrincipal principal = makePrincipal(userId);
            User user = createUser(userId);

            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            ReflectionTestUtils.setField(request, "title", "My Archive");
            ReflectionTestUtils.setField(request, "visibility", Visibility.PUBLIC);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            // save 호출 시 인자로 넘어온 객체를 그대로 반환 (상태 유지를 위해)
            given(archiveRepository.save(any(Archive.class))).willAnswer(inv -> {
                Archive a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 100L);
                return a;
            });

            // when
            ArchiveDto.Response response = archiveService.createArchive(principal, request);

            // then
            assertThat(response.getTitle()).isEqualTo("My Archive");

            // 핵심 검증: Book 생성 및 Title 주입 확인
            ArgumentCaptor<DiaryBook> diaryCaptor = ArgumentCaptor.forClass(DiaryBook.class);
            verify(diaryBookRepository).save(diaryCaptor.capture());
            assertThat(diaryCaptor.getValue().getTitle()).isEqualTo("My Archive의 다이어리"); // Title 필수 생성 확인

            verify(galleryBookRepository).save(any(GalleryBook.class));
            verify(ticketBookRepository).save(any(TicketBook.class));
            verify(repostBookRepository).save(any(RepostBook.class));

            // Count 초기화 확인
            verify(viewCountRepository).save(any(ArchiveViewCount.class));
            verify(likeCountRepository).save(any(ArchiveLikeCount.class));
        }

        @Test
        @DisplayName("성공: 배너 이미지가 포함된 경우 1:1 관계(BannerFile)가 설정되어야 한다.")
        void createArchive_Success_WithBanner() {
            // given
            Long userId = 1L;
            UserPrincipal principal = makePrincipal(userId);
            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            ReflectionTestUtils.setField(request, "title", "Banner Archive");
            ReflectionTestUtils.setField(request, "visibility", Visibility.PUBLIC);
            ReflectionTestUtils.setField(request, "bannerImageId", 500L);

            File mockFile = File.builder().filePath("cdn.com/image.jpg").build();

            given(userRepository.findById(userId)).willReturn(Optional.of(createUser(userId)));
            given(fileRepository.findById(500L)).willReturn(Optional.of(mockFile));
            given(archiveRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            ArchiveDto.Response response = archiveService.createArchive(principal, request);

            // then
            assertThat(response.getBannerUrl()).isEqualTo("cdn.com/image.jpg");
            // FileRepo가 호출되었는지 확인
            verify(fileRepository).findById(500L);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배너 파일 ID가 주어지면 예외가 발생한다.")
        void createArchive_Fail_FileNotFound() {
            // given
            UserPrincipal principal = makePrincipal(1L);
            ArchiveDto.CreateRequest request = new ArchiveDto.CreateRequest();
            ReflectionTestUtils.setField(request, "title", "Fail Archive");
            ReflectionTestUtils.setField(request, "visibility", Visibility.PUBLIC);
            ReflectionTestUtils.setField(request, "bannerImageId", 999L);

            given(userRepository.findById(1L)).willReturn(Optional.of(createUser(1L)));
            given(archiveRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(fileRepository.findById(999L)).willReturn(Optional.empty()); // 파일 없음

            // when & then
            assertThatThrownBy(() -> archiveService.createArchive(principal, request))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("아카이브 조회 (Read)")
    class ReadTest {

        @Test
        @DisplayName("성공: Archive 엔티티의 BannerFile 필드를 통해 배너 URL을 반환한다.")
        void getArchiveDetail_Success() {
            // given
            Long archiveId = 100L;
            UserPrincipal principal = makePrincipal(1L);
            Archive archive = createArchive(archiveId, createUser(1L), Visibility.PUBLIC);

            // 배너 설정 (1:1)
            File banner = File.builder().filePath("banner.jpg").build();
            archive.updateBanner(banner);

            given(archiveRepository.findByIdWithUser(archiveId)).willReturn(Optional.of(archive));
            given(viewCountRepository.findById(archiveId)).willReturn(Optional.empty()); // 없을 시 생성 로직
            given(viewCountRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            ArchiveDto.Response response = archiveService.getArchiveDetail(principal, archiveId);

            // then
            assertThat(response.getId()).isEqualTo(archiveId);
            assertThat(response.getBannerUrl()).isEqualTo("banner.jpg"); // 엔티티에서 직접 조회 확인
        }

        @Test
        @DisplayName("실패: 비공개(PRIVATE) 아카이브는 타인이 조회할 수 없다.")
        void getArchiveDetail_Fail_Private_Forbidden() {
            // given
            Long archiveId = 100L;
            User owner = createUser(1L);
            User stranger = createUser(2L);
            Archive archive = createArchive(archiveId, owner, Visibility.PRIVATE);

            given(archiveRepository.findByIdWithUser(archiveId)).willReturn(Optional.of(archive));

            // when & then
            assertThatThrownBy(() -> archiveService.getArchiveDetail(makePrincipal(stranger.getId()), archiveId))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("아카이브 수정 (Update)")
    class UpdateTest {

        @Test
        @DisplayName("성공: 배너 삭제 요청(-1) 시 updateBanner(null)이 호출된다.")
        void updateArchive_Success_DeleteBanner() {
            // given
            Long archiveId = 100L;
            User owner = createUser(1L);
            Archive archive = createArchive(archiveId, owner, Visibility.PUBLIC);
            // 기존 배너 있음
            archive.updateBanner(File.builder().filePath("old.jpg").build());

            ArchiveDto.UpdateRequest request = new ArchiveDto.UpdateRequest();
            ReflectionTestUtils.setField(request, "title", "Updated");
            ReflectionTestUtils.setField(request, "bannerImageId", -1L); // 삭제 요청

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));

            // when
            ArchiveDto.Response response = archiveService.updateArchive(makePrincipal(1L), archiveId, request);

            // then
            assertThat(response.getTitle()).isEqualTo("Updated");
            assertThat(response.getBannerUrl()).isNull(); // 삭제됨 확인
            assertThat(archive.getBannerFile()).isNull(); // 엔티티 상태 확인
        }

        @Test
        @DisplayName("성공: 배너 변경 요청(New ID) 시 새 파일을 조회하여 교체한다.")
        void updateArchive_Success_ChangeBanner() {
            // given
            Long archiveId = 100L;
            Archive archive = createArchive(archiveId, createUser(1L), Visibility.PUBLIC);

            ArchiveDto.UpdateRequest request = new ArchiveDto.UpdateRequest();
            ReflectionTestUtils.setField(request, "bannerImageId", 200L);

            File newFile = File.builder().filePath("new.jpg").build();

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));
            given(fileRepository.findById(200L)).willReturn(Optional.of(newFile));

            // when
            ArchiveDto.Response response = archiveService.updateArchive(makePrincipal(1L), archiveId, request);

            // then
            assertThat(response.getBannerUrl()).isEqualTo("new.jpg");
            assertThat(archive.getBannerFile()).isEqualTo(newFile); // 엔티티 교체 확인
        }
    }

    @Nested
    @DisplayName("아카이브 삭제 (Delete)")
    class DeleteTest {

        @Test
        @DisplayName("성공: 아카이브 삭제 시 Map 삭제 없이 하위 도메인 Bulk Delete만 순서대로 호출된다.")
        void deleteArchive_Success() {
            // given
            Long archiveId = 100L;
            User owner = createUser(1L);
            Archive archive = createArchive(archiveId, owner, Visibility.PUBLIC);

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));

            // when
            archiveService.deleteArchive(makePrincipal(1L), archiveId);

            // then: 호출 순서 검증 (1:1 배너는 Archive 삭제 시 자동 삭제되므로 별도 로직 없음)

            // 1. Event & Sports
            verify(eventHashtagMapRepository).deleteByArchiveId(archiveId);
            verify(sportRecordRepository).deleteByArchiveId(archiveId);
            verify(eventRepository).deleteByArchiveId(archiveId);

            // 2. Diary
            verify(diaryFileMapRepository).deleteFileMapsByBookId(archiveId);
            verify(diaryRepository).deleteByBookId(archiveId);

            // 3. Ticket
            verify(ticketRepository).deleteByBookId(archiveId);

            // 4. Gallery
            verify(galleryRepository).deleteByArchiveId(archiveId);

            // 5. Repost
            verify(repostRepository).deleteByBookId(archiveId);
            verify(repostTabRepository).deleteByBookId(archiveId);

            // 6. Sticker
            verify(stickerRepository).deleteByArchiveId(archiveId);

            // 7. Root Delete
            verify(archiveRepository).delete(archive);
        }
    }
}