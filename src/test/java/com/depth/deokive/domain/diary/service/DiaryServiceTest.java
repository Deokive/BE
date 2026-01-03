package com.depth.deokive.domain.diary.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.domain.diary.dto.DiaryDto;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.repository.DiaryBookRepository;
import com.depth.deokive.domain.diary.repository.DiaryFileMapRepository;
import com.depth.deokive.domain.diary.repository.DiaryRepository;
import com.depth.deokive.domain.file.repository.FileRepository;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @InjectMocks
    private DiaryService diaryService;

    @Mock private DiaryRepository diaryRepository;
    @Mock private DiaryBookRepository diaryBookRepository;
    @Mock private DiaryFileMapRepository diaryFileMapRepository;
    @Mock private FileRepository fileRepository;

    // --- 더미 데이터 생성 헬퍼 ---
    private User createUser(Long id) {
        return User.builder().id(id).build();
    }

    private Archive createArchive(Long id, User user) {
        return Archive.builder().id(id).user(user).build();
    }

    private DiaryBook createDiaryBook(Long id, Archive archive) {
        return DiaryBook.builder().id(id).archive(archive).build();
    }

    private Diary createDiary(Long id, User user, DiaryBook book, Visibility visibility) {
        return Diary.builder()
                .id(id)
                .title("Test Diary")
                .content("Content")
                .recordedAt(LocalDate.now())
                .color("#FFFFFF")
                .visibility(visibility)
                .diaryBook(book)
                .build();
    }

    @Nested
    @DisplayName("일기 생성 (Create)")
    class CreateTest {
        @Test
        @DisplayName("성공: 본인의 다이어리북에 일기를 생성한다.")
        void createDiary_Success() {
            // given
            Long userId = 1L;
            Long bookId = 10L;
            User user = createUser(userId);
            Archive archive = createArchive(bookId, user);
            DiaryBook book = createDiaryBook(bookId, archive);

            UserPrincipal principal = UserPrincipal.from(user);
            DiaryDto.Request request = new DiaryDto.Request(
                    "Title", "Content", LocalDate.now(), "#000000", Visibility.PUBLIC, null
            );

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(diaryRepository.save(any(Diary.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            DiaryDto.Response response = diaryService.createDiary(principal, bookId, request);

            // then
            assertThat(response.getTitle()).isEqualTo("Title");
            verify(diaryRepository, times(1)).save(any(Diary.class));
        }

        @Test
        @DisplayName("실패: 타인의 다이어리북에 생성 시도")
        void createDiary_Fail_Forbidden() {
            // given
            Long ownerId = 1L;
            Long intruderId = 2L;
            Long bookId = 10L;

            User owner = createUser(ownerId);
            Archive archive = createArchive(bookId, owner);
            DiaryBook book = createDiaryBook(bookId, archive);

            User other = createUser(intruderId);
            UserPrincipal intruder = UserPrincipal.from(other);
            DiaryDto.Request request = new DiaryDto.Request("T", "C", LocalDate.now(), "#FFF", Visibility.PUBLIC, null);

            given(diaryBookRepository.findById(bookId)).willReturn(Optional.of(book));

            // when & then
            assertThatThrownBy(() -> diaryService.createDiary(intruder, bookId, request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("일기 조회 (Read)")
    class ReadTest {
        @Test
        @DisplayName("성공: 작성자는 자신의 PRIVATE 일기를 조회할 수 있다.")
        void getDiary_Success_Owner_Private() {
            // given
            Long userId = 1L;
            Long diaryId = 100L;
            User user = createUser(userId);
            DiaryBook book = createDiaryBook(10L, createArchive(10L, user));

            // PRIVATE 일기 생성 (작성자: userId)
            Diary diary = createDiary(diaryId, user, book, Visibility.PRIVATE);
        }
    }
}