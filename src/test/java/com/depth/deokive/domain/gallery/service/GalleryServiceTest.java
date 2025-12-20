package com.depth.deokive.domain.gallery.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.repository.ArchiveRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.gallery.dto.GalleryDto;
import com.depth.deokive.domain.gallery.entity.GalleryBook;
import com.depth.deokive.domain.gallery.repository.GalleryBookRepository;
import com.depth.deokive.domain.gallery.repository.GalleryQueryRepository;
import com.depth.deokive.domain.gallery.repository.GalleryRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GalleryServiceTest {

    @InjectMocks
    private GalleryService galleryService;

    @Mock private GalleryRepository galleryRepository;
    @Mock private GalleryBookRepository galleryBookRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ArchiveRepository archiveRepository;

    private User createUser(Long id) {
        return User.builder().id(id).email("test@test.com").build();
    }

    private Archive createArchive(Long id, User user) {
        return Archive.builder().id(id).user(user).build();
    }

    private GalleryBook createGalleryBook(Long id, Archive archive, String title) {
        return GalleryBook.builder().id(id).archive(archive).title(title).build();
    }

    @Nested
    @DisplayName("갤러리 이미지 등록 (Create)")
    class CreateGalleryTest {

        @Test
        @DisplayName("성공: 파일 ID 리스트를 받아 갤러리를 생성하고 CreateResponse를 반환한다.")
        void createGalleries_Success() {
            // given
            Long userId = 1L;
            Long archiveId = 10L;
            List<Long> fileIds = List.of(100L, 101L);

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            GalleryBook galleryBook = createGalleryBook(archiveId, archive, "Title");
            UserPrincipal principal = UserPrincipal.from(user);

            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest(fileIds);
            List<File> files = List.of(File.builder().id(100L).build(), File.builder().id(101L).build());

            given(galleryBookRepository.findById(archiveId)).willReturn(Optional.of(galleryBook));
            given(fileRepository.findAllById(fileIds)).willReturn(files);

            // when
            GalleryDto.CreateResponse response = galleryService.createGalleries(principal, archiveId, request);

            // then
            assertThat(response.getCreatedCount()).isEqualTo(2);
            assertThat(response.getArchiveId()).isEqualTo(archiveId);
            verify(galleryRepository, times(1)).saveAll(anyList());
        }

        @Test
        @DisplayName("실패: 요청한 파일 중 일부가 DB에 존재하지 않음")
        void createGalleries_Fail_FileNotFound() {
            // given
            Long userId = 1L;
            Long archiveId = 10L;
            List<Long> fileIds = List.of(100L, 101L);

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            GalleryBook galleryBook = createGalleryBook(archiveId, archive, "Title");
            UserPrincipal principal = UserPrincipal.from(user);

            List<File> files = List.of(File.builder().id(100L).build()); // 101번 파일 없음

            given(galleryBookRepository.findById(archiveId)).willReturn(Optional.of(galleryBook));
            given(fileRepository.findAllById(fileIds)).willReturn(files);

            GalleryDto.CreateRequest request = new GalleryDto.CreateRequest(fileIds);

            // when & then
            assertThatThrownBy(() -> galleryService.createGalleries(principal, archiveId, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("갤러리북 제목 수정 (Update)")
    class UpdateTitleTest {

        @Test
        @DisplayName("성공: 갤러리북 제목이 변경되고 응답을 반환한다.")
        void updateTitle_Success() {
            // given
            Long userId = 1L;
            Long archiveId = 10L;
            String newTitle = "New Title";

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            GalleryBook galleryBook = createGalleryBook(archiveId, archive, "Old Title");
            UserPrincipal principal = UserPrincipal.from(user);

            GalleryDto.UpdateTitleRequest request = new GalleryDto.UpdateTitleRequest(newTitle);

            given(galleryBookRepository.findById(archiveId)).willReturn(Optional.of(galleryBook));

            // when
            GalleryDto.UpdateTitleResponse response = galleryService.updateGalleryBookTitle(principal, archiveId, request);

            // then
            assertThat(response.getUpdatedTitle()).isEqualTo(newTitle);
            assertThat(galleryBook.getTitle()).isEqualTo(newTitle);
        }
    }

    @Nested
    @DisplayName("갤러리 이미지 삭제 (Delete)")
    class DeleteGalleryTest {

        @Test
        @DisplayName("성공: Custom Query를 호출하여 삭제한다 (반환값 없음).")
        void deleteGalleries_Success() {
            // given
            Long userId = 1L;
            Long archiveId = 10L;
            List<Long> deleteIds = List.of(500L, 501L);

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            UserPrincipal principal = UserPrincipal.from(user);

            GalleryDto.DeleteRequest request = new GalleryDto.DeleteRequest(deleteIds);

            given(archiveRepository.findById(archiveId)).willReturn(Optional.of(archive));

            // when
            galleryService.deleteGalleries(principal, archiveId, request);

            // then
            // 반환값이 없으므로(void), 리포지토리 메서드가 올바른 인자로 호출되었는지만 검증합니다.
            verify(galleryRepository, times(1)).deleteByIdsAndArchiveId(deleteIds, archiveId);
        }
    }
}