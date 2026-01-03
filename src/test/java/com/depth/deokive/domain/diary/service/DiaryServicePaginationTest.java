package com.depth.deokive.domain.diary.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryQueryRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.friend.repository.FriendMapRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(classes = DiaryService.class)
class DiaryServicePaginationTest {

    @Autowired
    private DiaryService diaryService;

    @MockitoBean private DiaryRepository diaryRepository;
    @MockitoBean private DiaryBookRepository diaryBookRepository;
    @MockitoBean private DiaryFileMapRepository diaryFileMapRepository;
    @MockitoBean private DiaryQueryRepository diaryQueryRepository;
    @MockitoBean private FileService fileService;
    @MockitoBean private FriendMapRepository friendMapRepository;

    // --- Fixture Helpers ---
    private User createUser(Long id) {
        return User.builder().id(id).nickname("user" + id).build();
    }

    private DiaryBook createDiaryBook(Long id, User owner, Visibility visibility) {
        Archive archive = Archive.builder()
                .id(id).user(owner).title("Test Archive").visibility(visibility)
                .build();
        return DiaryBook.builder().id(id).title("Test Book").archive(archive).build();
    }

    @Nested
    @DisplayName("1. 다이어리 목록 조회 (Security & Performance)")
    class GetDiariesTest {

        @Test
        @DisplayName("Case 1: 타인이 PUBLIC 아카이브 조회 -> PUBLIC 다이어리만 쿼리 조건에 포함되어야 함 (Layer 2 Security)")
        void strangerViewingPublicArchive() {
            // given
            Long ownerId = 1L;
            Long strangerId = 2L;
            Long bookId = 100L;

            User owner = createUser(ownerId);
            DiaryBook diaryBook = createDiaryBook(bookId, owner, Visibility.PUBLIC);
            UserPrincipal stranger = new UserPrincipal(strangerId, "stranger", null, null);

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(diaryBook));
            given(friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(any(), any(), any())).willReturn(false);

            // Pagination Mock
            Page<DiaryDto.DiaryPageResponse> emptyPage = new PageImpl<>(Collections.emptyList());
            given(diaryQueryRepository.findDiaries(any(), any(), any())).willReturn(emptyPage);

            // when
            diaryService.getDiaries(stranger, bookId, new DiaryDto.DiaryPageRequest());

            // then
            ArgumentCaptor<List<Visibility>> visibilityCaptor = ArgumentCaptor.forClass(List.class);
            verify(diaryQueryRepository).findDiaries(eq(bookId), visibilityCaptor.capture(), any(Pageable.class));

            // 검증: 타인은 PUBLIC만 볼 수 있어야 함
            assertThat(visibilityCaptor.getValue()).containsExactly(Visibility.PUBLIC);

            // N+1 검증: 엔티티 조회 레포지토리는 호출되지 않아야 함
            verify(diaryRepository, never()).findAll();
        }

        @Test
        @DisplayName("Case 2: 친구가 RESTRICTED 아카이브 조회 -> PUBLIC, RESTRICTED 다이어리 조회 (Layer 2 Security)")
        void friendViewingRestrictedArchive() {
            // given
            Long ownerId = 1L;
            Long friendId = 2L;
            Long bookId = 100L;

            User owner = createUser(ownerId);
            DiaryBook diaryBook = createDiaryBook(bookId, owner, Visibility.RESTRICTED);
            UserPrincipal friend = new UserPrincipal(friendId, "friend", null, null);

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(diaryBook));
            given(friendMapRepository.existsByUserIdAndFriendIdAndFriendStatus(
                    eq(friendId), eq(ownerId), eq(FriendStatus.ACCEPTED))
            ).willReturn(true); // 친구 관계

            given(diaryQueryRepository.findDiaries(any(), any(), any())).willReturn(new PageImpl<>(Collections.emptyList()));

            // when
            diaryService.getDiaries(friend, bookId, new DiaryDto.DiaryPageRequest());

            // then
            ArgumentCaptor<List<Visibility>> visibilityCaptor = ArgumentCaptor.forClass(List.class);
            verify(diaryQueryRepository).findDiaries(eq(bookId), visibilityCaptor.capture(), any(Pageable.class));

            // 검증: 친구는 PUBLIC과 RESTRICTED를 볼 수 있음 (PRIVATE 제외)
            assertThat(visibilityCaptor.getValue()).containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.RESTRICTED);
            assertThat(visibilityCaptor.getValue()).doesNotContain(Visibility.PRIVATE);
        }

        @Test
        @DisplayName("Case 3: 본인은 PRIVATE 아카이브 내의 모든 다이어리(PRIVATE 포함) 조회 가능")
        void ownerViewingPrivateArchive() {
            // given
            Long ownerId = 1L;
            Long bookId = 100L;
            User owner = createUser(ownerId);
            DiaryBook diaryBook = createDiaryBook(bookId, owner, Visibility.PRIVATE);
            UserPrincipal ownerPrincipal = new UserPrincipal(ownerId, "owner", null, null);

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(diaryBook));
            given(diaryQueryRepository.findDiaries(any(), any(), any())).willReturn(new PageImpl<>(Collections.emptyList()));

            // when
            diaryService.getDiaries(ownerPrincipal, bookId, new DiaryDto.DiaryPageRequest());

            // then
            ArgumentCaptor<List<Visibility>> visibilityCaptor = ArgumentCaptor.forClass(List.class);
            verify(diaryQueryRepository).findDiaries(eq(bookId), visibilityCaptor.capture(), any(Pageable.class));

            // 검증: 주인은 모두 볼 수 있음
            assertThat(visibilityCaptor.getValue())
                    .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.RESTRICTED, Visibility.PRIVATE);
        }

        @Test
        @DisplayName("Case 4: 타인이 PRIVATE 아카이브 접근 시 -> 1차 방어선(Service Layer)에서 차단")
        void strangerAccessingPrivateArchive_ShouldFail() {
            // given
            Long ownerId = 1L;
            Long strangerId = 2L;
            DiaryBook privateBook = createDiaryBook(100L, createUser(ownerId), Visibility.PRIVATE);
            UserPrincipal stranger = new UserPrincipal(strangerId, "stranger", null, null);

            given(diaryBookRepository.findById(100L)).willReturn(Optional.of(privateBook));

            // when & then
            assertThatThrownBy(() -> diaryService.getDiaries(stranger, 100L, new DiaryDto.DiaryPageRequest()))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 검증: 값비싼 QueryRepository 호출 자체를 안 해야 함
            verify(diaryQueryRepository, never()).findDiaries(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("2. 다이어리 생성 (Denormalization Logic)")
    class CreateDiaryTest {

        @Test
        @DisplayName("성공: 파일 업로드 시 PREVIEW(썸네일) 역할의 이미지가 있다면 해당 경로가 Entity에 역정규화되어야 한다.")
        void createDiary_WithThumbnailMapping() {
            // given
            Long userId = 1L;
            Long bookId = 100L;
            UserPrincipal user = new UserPrincipal(userId, "user", null, null);
            User owner = createUser(userId);

            DiaryBook diaryBook = createDiaryBook(bookId, owner, Visibility.PUBLIC);

            // 파일 2개 요청: 1번은 영상, 2번은 썸네일(PREVIEW)
            File videoFile = File.builder().id(10L).filePath("video.mp4").build();
            File thumbFile = File.builder().id(11L).filePath("thumb.jpg").build();

            DiaryDto.Request request = DiaryDto.Request.builder()
                    .title("일기").content("내용").color("#000000").recordedAt(LocalDate.now()).visibility(Visibility.PUBLIC)
                    .files(List.of(
                            new DiaryDto.AttachedFileRequest(10L, MediaRole.CONTENT, 1),
                            new DiaryDto.AttachedFileRequest(11L, MediaRole.PREVIEW, 2) // 이게 썸네일 타겟
                    ))
                    .build();

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(diaryBook));
            given(fileService.validateFileOwners(anyList(), eq(userId))).willReturn(List.of(videoFile, thumbFile));

            // saveAll Mocking: 인자로 넘어온 리스트를 그대로 반환해야 서비스 로직이 이어짐
            given(diaryFileMapRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            diaryService.createDiary(user, bookId, request);

            // then
            ArgumentCaptor<Diary> diaryCaptor = ArgumentCaptor.forClass(Diary.class);
            verify(diaryRepository).save(diaryCaptor.capture()); // 1. 저장 호출 확인

            Diary savedDiary = diaryCaptor.getValue();

            // ★ 핵심 검증: updateThumbnail()이 정상적으로 호출되어 thumbnailUrl 필드가 채워졌는지 확인
            // (Service 로직에서 save 후 updateThumbnail을 호출하므로, capture된 시점엔 null일 수 있으나
            //  실제로는 트랜잭션 내에서 객체 상태가 변했어야 함. Mock 객체라 상태 변화 추적이 어려우면 메서드 호출로 검증)
            //  -> 여기서는 로직상 updateThumbnail 호출 후 리턴하므로, Entity 상태보다는 로직 흐름을 믿거나
            //     ArgumentCaptor를 2번 쓰거나 해야 하지만, 가장 확실한 건 결과값 검증이나 로직상 호출 여부임.

            // 여기서는 `createDiary` 내부에서 `diary.updateThumbnail(...)`을 호출하므로
            // 캡처된 객체의 상태값 검증을 시도합니다. (레퍼런스 타입이라 변경사항 반영됨)
            assertThat(savedDiary.getThumbnailUrl()).isEqualTo("thumb.jpg");
        }
    }
}