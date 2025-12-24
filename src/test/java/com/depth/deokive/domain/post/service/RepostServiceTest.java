package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.entity.*;
import com.depth.deokive.domain.post.repository.*;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RepostServiceTest {

    @InjectMocks
    private RepostService repostService;

    @Mock private RepostRepository repostRepository;
    @Mock private RepostTabRepository repostTabRepository;
    @Mock private RepostBookRepository repostBookRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostFileMapRepository postFileMapRepository;

    private UserPrincipal makePrincipal(Long userId) {
        return UserPrincipal.builder()
                .userId(userId)
                .username("testUser")
                .role(Role.USER)
                .build();
    }

    private User createUser(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Archive createArchive(Long id, User user) {
        Archive archive = Archive.builder().user(user).build();
        ReflectionTestUtils.setField(archive, "id", id);
        return archive;
    }

    private RepostBook createRepostBook(Long id, Archive archive) {
        RepostBook book = RepostBook.builder().archive(archive).build();
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    private RepostTab createRepostTab(Long id, RepostBook book) {
        RepostTab tab = RepostTab.builder().repostBook(book).title("Existing Tab").build();
        ReflectionTestUtils.setField(tab, "id", id);
        return tab;
    }

    private Post createPost(Long id, String title) {
        Post post = Post.builder().title(title).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private PostFileMap createPostFileMap(String url, MediaRole role) {
        File file = File.builder().filePath(url).build();
        return PostFileMap.builder().file(file).mediaRole(role).build();
    }

    @Nested
    @DisplayName("📂 RepostTab (탭) 기능 테스트")
    class RepostTabTest {

        @Test
        @DisplayName("성공: 탭 생성 시 '새 리포스트 탭 N' 형식으로 자동 작명된다.")
        void createTab_Success_AutoNaming() {
            // given
            Long userId = 1L;
            Long archiveId = 100L;
            UserPrincipal principal = makePrincipal(userId);

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            RepostBook book = createRepostBook(archiveId, archive);

            // Mocking
            given(repostBookRepository.findById(archiveId)).willReturn(Optional.of(book));
            given(repostTabRepository.countByRepostBookId(archiveId)).willReturn(2L); // 기존에 2개 존재
            given(repostTabRepository.save(any(RepostTab.class))).willAnswer(inv -> {
                RepostTab saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 10L);
                return saved;
            });

            // when
            RepostDto.TabResponse response = repostService.createRepostTab(principal, archiveId);

            // then
            // 기존 2개 + 1 = 3번째 탭
            assertThat(response.getTitle()).isEqualTo("3번째 탭");
            verify(repostTabRepository).save(any(RepostTab.class));
        }

        @Test
        @DisplayName("실패: 탭이 이미 10개라면 LIMIT_EXCEED 예외가 발생한다.")
        void createTab_Fail_LimitExceeded() {
            // given
            Long userId = 1L;
            Long archiveId = 100L;
            UserPrincipal principal = makePrincipal(userId);

            User user = createUser(userId);
            Archive archive = createArchive(archiveId, user);
            RepostBook book = createRepostBook(archiveId, archive);

            given(repostBookRepository.findById(archiveId)).willReturn(Optional.of(book));
            given(repostTabRepository.countByRepostBookId(archiveId)).willReturn(10L); // Limit 도달

            // when & then
            assertThatThrownBy(() -> repostService.createRepostTab(principal, archiveId))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPOST_TAB_LIMIT_EXCEED);
        }

        @Test
        @DisplayName("실패: 타인의 아카이브에 탭을 생성하려 하면 AUTH_FORBIDDEN 예외가 발생한다.")
        void createTab_Fail_Forbidden() {
            // given
            Long ownerId = 1L;
            Long intruderId = 999L;
            UserPrincipal intruderPrincipal = makePrincipal(intruderId);

            User owner = createUser(ownerId);
            Archive archive = createArchive(100L, owner);
            RepostBook book = createRepostBook(100L, archive);

            given(repostBookRepository.findById(100L)).willReturn(Optional.of(book));

            // when & then
            assertThatThrownBy(() -> repostService.createRepostTab(intruderPrincipal, 100L))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("성공: 탭 삭제 시 성능 최적화를 위해 Bulk Delete 쿼리가 먼저 실행된다.")
        void deleteTab_Success_PerformanceCheck() {
            // given
            Long userId = 1L;
            Long tabId = 10L;
            UserPrincipal principal = makePrincipal(userId);

            User user = createUser(userId);
            Archive archive = createArchive(100L, user);
            RepostBook book = createRepostBook(100L, archive);
            RepostTab tab = createRepostTab(tabId, book);

            given(repostTabRepository.findById(tabId)).willReturn(Optional.of(tab));

            // when
            repostService.deleteRepostTab(principal, tabId);

            // then
            // 1. Repost들을 먼저 한 방 쿼리로 삭제했는지 검증 (성능 핵심)
            verify(repostRepository).deleteAllByRepostTabId(tabId);
            // 2. 그 다음 탭 삭제 검증
            verify(repostTabRepository).delete(tab);
        }
    }

    @Nested
    @DisplayName("🔗 Repost (리포스트) 기능 테스트")
    class RepostTest {

        @Test
        @DisplayName("성공: 리포스트 생성 시 원본 제목과 썸네일이 '스냅샷'으로 저장된다.")
        void createRepost_Success_Snapshot() {
            // given
            Long userId = 1L;
            Long tabId = 10L;
            Long postId = 500L;
            String originalTitle = "Original Post Title";
            String thumbnailUrl = "https://cdn.test.com/thumb.jpg";

            UserPrincipal principal = makePrincipal(userId);
            RepostDto.CreateRequest request = new RepostDto.CreateRequest();
            ReflectionTestUtils.setField(request, "postId", postId);
            // customTitle이 null -> 원본 제목 사용

            // Mocks Setup
            User user = createUser(userId);
            Archive archive = createArchive(100L, user);
            RepostBook book = createRepostBook(100L, archive);
            RepostTab tab = createRepostTab(tabId, book);
            Post post = createPost(postId, originalTitle);

            // Mock Thumbnail Finding
            List<PostFileMap> files = List.of(createPostFileMap(thumbnailUrl, MediaRole.PREVIEW));

            given(repostTabRepository.findById(tabId)).willReturn(Optional.of(tab));
            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(repostRepository.existsByRepostTabIdAndPostId(tabId, postId)).willReturn(false);
            given(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId)).willReturn(files);

            // Save Capture
            given(repostRepository.save(any(Repost.class))).willAnswer(inv -> {
                Repost saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 777L);
                return saved;
            });

            // when
            RepostDto.Response response = repostService.createRepost(principal, tabId, request);

            // then
            // 1. 제목이 원본 제목으로 잘 들어갔는지 (Snapshot)
            assertThat(response.getTitle()).isEqualTo(originalTitle);
            // 2. 썸네일이 잘 들어갔는지 (Snapshot)
            assertThat(response.getThumbnailUrl()).contains("thumb.jpg");
            // 3. Post Entity가 아닌 ID값만 저장되었는지 검증 (Loose Coupling)
            assertThat(response.getPostId()).isEqualTo(postId);

            // Verify Logic
            verify(repostRepository).save(argThat(r ->
                    r.getPostId().equals(postId) && // ID 참조 확인
                            r.getTitle().equals(originalTitle) // 제목 스냅샷 확인
            ));
        }

        @Test
        @DisplayName("실패: 원본 게시글이 존재하지 않으면(삭제됨) 리포스트 생성 불가.")
        void createRepost_Fail_PostNotFound() {
            // given
            Long userId = 1L;
            Long tabId = 10L;
            Long postId = 999L;
            UserPrincipal principal = makePrincipal(userId);

            RepostDto.CreateRequest request = new RepostDto.CreateRequest();
            ReflectionTestUtils.setField(request, "postId", postId);

            User user = createUser(userId);
            Archive archive = createArchive(100L, user);
            RepostBook book = createRepostBook(100L, archive);
            RepostTab tab = createRepostTab(tabId, book);

            given(repostTabRepository.findById(tabId)).willReturn(Optional.of(tab));
            // Post 찾을 수 없음
            given(postRepository.findById(postId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> repostService.createRepost(principal, tabId, request))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 같은 탭에 동일한 게시글을 중복 저장할 수 없다.")
        void createRepost_Fail_Duplicate() {
            // given
            Long userId = 1L;
            Long tabId = 10L;
            Long postId = 500L;
            UserPrincipal principal = makePrincipal(userId);

            RepostDto.CreateRequest request = new RepostDto.CreateRequest();
            ReflectionTestUtils.setField(request, "postId", postId);

            User user = createUser(userId);
            Archive archive = createArchive(100L, user);
            RepostBook book = createRepostBook(100L, archive);
            RepostTab tab = createRepostTab(tabId, book);
            Post post = createPost(postId, "Title");

            given(repostTabRepository.findById(tabId)).willReturn(Optional.of(tab));
            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(repostRepository.existsByRepostTabIdAndPostId(tabId, postId)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> repostService.createRepost(principal, tabId, request))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REPOST_TAB_AND_POST_DUPLICATED);
        }
    }
}