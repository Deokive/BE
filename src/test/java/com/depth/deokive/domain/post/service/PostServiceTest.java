package com.depth.deokive.domain.post.service;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.test.IntegrationTestSupport;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostService 통합 테스트")
class PostServiceTest extends IntegrationTestSupport {

    @Autowired PostService postService;
    @Autowired PostRepository postRepository;
    @Autowired PostFileMapRepository postFileMapRepository;
    @Autowired FileRepository fileRepository;

    // Test Data
    private User userA;
    private User userB;

    private List<File> userAFiles;
    private List<File> userBFiles;

    @BeforeEach
    void setUp() {
        // Users Setup
        userA = createTestUser("usera@test.com", "UserA");
        userB = createTestUser("userb@test.com", "UserB");

        // Files Setup (UserA: 20 files, UserB: 5 files)
        setupMockUser(userA);
        userAFiles = createFiles(userA, 20);

        setupMockUser(userB);
        userBFiles = createFiles(userB, 5);

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

    private List<File> createFiles(User owner, int count) {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuid = UUID.randomUUID().toString();
            File file = fileRepository.save(File.builder()
                    .filename("file_" + uuid + ".jpg")
                    .s3ObjectKey("posts/" + owner.getNickname() + "/" + uuid + ".jpg")
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
    @DisplayName("[Category 1] Create Post")
    class Create {

        @Test
        @DisplayName("SCENE 1: 정상 케이스 (파일 포함, PREVIEW 파일 있음)")
        void createPost_WithPreview() {
            // Given
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = new ArrayList<>();
            // Sequence 0: CONTENT
            files.add(new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.CONTENT, 0));
            // Sequence 1: PREVIEW (Thumbnail target)
            files.add(new PostDto.AttachedFileRequest(userAFiles.get(1).getId(), MediaRole.PREVIEW, 1));

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("Title")
                    .content("Content")
                    .category(Category.IDOL)
                    .files(files)
                    .build();

            // When
            PostDto.Response response = postService.createPost(UserPrincipal.from(userA), request);

            // Then
            Post post = postRepository.findById(response.getId()).orElseThrow();
            assertThat(post.getTitle()).isEqualTo("Title");

            // File Map check
            List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
            assertThat(maps).hasSize(2);
            assertThat(maps.get(1).getMediaRole()).isEqualTo(MediaRole.PREVIEW);

            // Thumbnail check
            String expectedThumb = ThumbnailUtils.getMediumThumbnailKey(userAFiles.get(1).getS3ObjectKey());
            assertThat(post.getThumbnailKey()).isEqualTo(expectedThumb);
        }

        @Test
        @DisplayName("SCENE 2: 정상 케이스 (파일 포함, PREVIEW 파일 없음)")
        void createPost_NoPreview() {
            // Given
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = new ArrayList<>();
            files.add(new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.CONTENT, 0));

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("Title")
                    .content("Content")
                    .category(Category.ACTOR)
                    .files(files)
                    .build();

            // When
            PostDto.Response response = postService.createPost(UserPrincipal.from(userA), request);

            // Then
            Post post = postRepository.findById(response.getId()).orElseThrow();
            String expectedThumb = ThumbnailUtils.getMediumThumbnailKey(userAFiles.get(0).getS3ObjectKey());
            assertThat(post.getThumbnailKey()).isEqualTo(expectedThumb);
        }

        @Test
        @DisplayName("SCENE 3: 정상 케이스 (다양한 카테고리)")
        void createPost_Categories() {
            setupMockUser(userA);

            for (Category category : Category.values()) {
                PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                        .title("Post " + category)
                        .content("Content")
                        .category(category)
                        .files(null)
                        .build();

                PostDto.Response response = postService.createPost(UserPrincipal.from(userA), request);
                Post post = postRepository.findById(response.getId()).orElseThrow();
                assertThat(post.getCategory()).isEqualTo(category);
            }
        }

        @Test
        @DisplayName("SCENE 4: 파일 없이 생성 (files = null)")
        void createPost_NullFiles() {
            setupMockUser(userA);
            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("No File")
                    .content("Content")
                    .category(Category.MUSICIAN)
                    .files(null)
                    .build();

            PostDto.Response response = postService.createPost(UserPrincipal.from(userA), request);
            Post post = postRepository.findById(response.getId()).orElseThrow();

            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId())).isEmpty();
            assertThat(post.getThumbnailKey()).isNull();
        }

        @Test
        @DisplayName("SCENE 5: 파일 없이 생성 (files = empty)")
        void createPost_EmptyFiles() {
            setupMockUser(userA);
            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("Empty File")
                    .content("Content")
                    .category(Category.SPORT)
                    .files(Collections.emptyList())
                    .build();

            PostDto.Response response = postService.createPost(UserPrincipal.from(userA), request);

            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(response.getId())).isEmpty();
        }

        @Test
        @DisplayName("SCENE 6: 존재하지 않는 사용자 (UserPrincipal ID가 DB에 없음)")
        void createPost_UserNotFound() {
            // Given: ID가 DB에 없는 Principal 생성
            UserPrincipal invalidPrincipal = UserPrincipal.builder().userId(99999L).role(Role.USER).build();

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("Fail").content("C").category(Category.ARTIST).build();

            // When & Then (Service: userRepository.findById().orElseThrow(USER_NOT_FOUND))
            assertThatThrownBy(() -> postService.createPost(invalidPrincipal, request))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 7: 다른 사용자의 파일 사용 시도")
        void createPost_IDOR() {
            setupMockUser(userA);
            // UserA가 UserB의 파일(userBFiles)을 사용 시도 -> validateFileOwners에서 걸러짐
            List<PostDto.AttachedFileRequest> files = List.of(
                    new PostDto.AttachedFileRequest(userBFiles.get(0).getId(), MediaRole.CONTENT, 0)
            );

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("IDOR")
                    .content("Content")
                    .category(Category.IDOL)
                    .files(files)
                    .build();

            // Service: if (files.size() != fileIds.size()) throw FILE_NOT_FOUND
            assertThatThrownBy(() -> postService.createPost(UserPrincipal.from(userA), request))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode")
                    .satisfies(code -> assertThat(code).isIn(ErrorCode.FILE_NOT_FOUND, ErrorCode.AUTH_FORBIDDEN));
        }

        @Test
        @DisplayName("SCENE 8: 중복된 파일 ID")
        void createPost_DuplicateFileId() {
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = List.of(
                    new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.CONTENT, 0),
                    new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.CONTENT, 1) // 중복
            );

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("Dup")
                    .content("Content")
                    .category(Category.IDOL)
                    .files(files)
                    .build();

            assertThatThrownBy(() -> postService.createPost(UserPrincipal.from(userA), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 9: 존재하지 않는 파일 ID")
        void createPost_FileNotFound() {
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = List.of(
                    new PostDto.AttachedFileRequest(99999L, MediaRole.CONTENT, 0)
            );

            PostDto.CreateRequest request = PostDto.CreateRequest.builder()
                    .title("No File")
                    .content("Content")
                    .category(Category.IDOL)
                    .files(files)
                    .build();

            assertThatThrownBy(() -> postService.createPost(UserPrincipal.from(userA), request))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 2]: Read
    // ========================================================================================
    @Nested
    @DisplayName("[Category 2] Read Post")
    class Read {
        private Post postWithFiles;
        private Post postNoFiles;

        @BeforeEach
        void initPosts() {
            setupMockUser(userA);
            // Setup Post with 3 files
            List<PostDto.AttachedFileRequest> files = new ArrayList<>();
            for(int i=0; i<3; i++) {
                files.add(new PostDto.AttachedFileRequest(userAFiles.get(i).getId(), i==0 ? MediaRole.PREVIEW : MediaRole.CONTENT, i));
            }
            PostDto.CreateRequest req1 = PostDto.CreateRequest.builder()
                    .title("With Files").content("Content").category(Category.IDOL).files(files).build();
            PostDto.Response res1 = postService.createPost(UserPrincipal.from(userA), req1);
            postWithFiles = postRepository.findById(res1.getId()).orElseThrow();

            // Setup Post with no files
            PostDto.CreateRequest req2 = PostDto.CreateRequest.builder()
                    .title("No Files").content("Content").category(Category.ACTOR).files(null).build();
            PostDto.Response res2 = postService.createPost(UserPrincipal.from(userA), req2);
            postNoFiles = postRepository.findById(res2.getId()).orElseThrow();

            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 10: 정상 케이스 (파일 포함, 본인 조회)")
        void getPost_Owner() {
            PostDto.Response response = postService.getPost(postWithFiles.getId());
            assertThat(response.getId()).isEqualTo(postWithFiles.getId());
            assertThat(response.getFiles()).hasSize(3);
            assertThat(response.getFiles().get(0).getSequence()).isEqualTo(0);
            assertThat(response.getViewCount()).isEqualTo(1); // Service increments viewCount
        }

        @Test
        @DisplayName("SCENE 11: 정상 케이스 (파일 포함, 타인 조회)")
        void getPost_Stranger() {
            PostDto.Response response = postService.getPost(postWithFiles.getId());
            assertThat(response.getId()).isEqualTo(postWithFiles.getId());
            assertThat(response.getFiles()).hasSize(3);
            assertThat(response.getViewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("SCENE 12: 정상 케이스 (파일 없음)")
        void getPost_NoFiles() {
            PostDto.Response response = postService.getPost(postNoFiles.getId());
            assertThat(response.getId()).isEqualTo(postNoFiles.getId());
            assertThat(response.getFiles()).isEmpty();
        }

        @Test
        @DisplayName("SCENE 13: 정상 케이스 (PREVIEW 파일 포함 확인)")
        void getPost_Preview() {
            PostDto.Response response = postService.getPost(postWithFiles.getId());
            assertThat(response.getFiles().get(0).getMediaRole()).isEqualTo(MediaRole.PREVIEW);
        }

        @Test
        @DisplayName("SCENE 14: 정상 케이스 (CONTENT 파일만)")
        void getPost_OnlyContent() {
            // Given: Create post with content only
            List<PostDto.AttachedFileRequest> files = List.of(
                    new PostDto.AttachedFileRequest(userAFiles.get(3).getId(), MediaRole.CONTENT, 0)
            );
            PostDto.CreateRequest req = PostDto.CreateRequest.builder()
                    .title("C").content("C").category(Category.SPORT).files(files).build();
            PostDto.Response res = postService.createPost(UserPrincipal.from(userA), req);
            flushAndClear();

            // When
            PostDto.Response response = postService.getPost(res.getId());

            // Then
            assertThat(response.getFiles().get(0).getMediaRole()).isEqualTo(MediaRole.CONTENT);
        }

        @Test
        @DisplayName("SCENE 15: 조회수 증가 확인 (DB 반영 확인)")
        void getPost_ViewCount() {
            Long id = postNoFiles.getId();

            // 1st
            postService.getPost(id);
            flushAndClear();

            // 2nd
            postService.getPost(id);
            flushAndClear();

            // 3rd
            postService.getPost(id);
            flushAndClear();

            Post post = postRepository.findById(id).orElseThrow();
            assertThat(post.getViewCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("SCENE 16: 존재하지 않는 Post")
        void getPost_NotFound() {
            assertThatThrownBy(() -> postService.getPost(99999L))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 3]: Update
    // ========================================================================================
    @Nested
    @DisplayName("[Category 3] Update Post")
    class Update {
        private Post post;

        @BeforeEach
        void init() {
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = List.of(
                    new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.PREVIEW, 0),
                    new PostDto.AttachedFileRequest(userAFiles.get(1).getId(), MediaRole.CONTENT, 1)
            );
            PostDto.CreateRequest req = PostDto.CreateRequest.builder()
                    .title("Old Title").content("Old Content").category(Category.IDOL).files(files).build();
            PostDto.Response res = postService.createPost(UserPrincipal.from(userA), req);
            post = postRepository.findById(res.getId()).orElseThrow();
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 17: 정상 케이스 (제목, 내용, 카테고리 수정)")
        void updatePost_Full() {
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder()
                    .title("New Title").content("New Content").category(Category.MUSICIAN).build();

            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);
            flushAndClear(); // Dirty checking sync

            Post updated = postRepository.findById(post.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getContent()).isEqualTo("New Content");
            assertThat(updated.getCategory()).isEqualTo(Category.MUSICIAN);

            // Files preserved (files=null in request)
            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId())).hasSize(2);
        }

        @Test
        @DisplayName("SCENE 18~20: 부분 수정 (제목만/내용만/카테고리만)")
        void updatePost_Partial() {
            // 18: Title only
            postService.updatePost(UserPrincipal.from(userA), post.getId(), PostDto.UpdateRequest.builder().title("T").build());
            flushAndClear();
            assertThat(postRepository.findById(post.getId()).get().getTitle()).isEqualTo("T");

            // 19: Content only
            postService.updatePost(UserPrincipal.from(userA), post.getId(), PostDto.UpdateRequest.builder().content("C").build());
            flushAndClear();
            assertThat(postRepository.findById(post.getId()).get().getContent()).isEqualTo("C");

            // 20: Category only
            postService.updatePost(UserPrincipal.from(userA), post.getId(), PostDto.UpdateRequest.builder().category(Category.ANIMATION).build());
            flushAndClear();
            assertThat(postRepository.findById(post.getId()).get().getCategory()).isEqualTo(Category.ANIMATION);
        }

        @Test
        @DisplayName("SCENE 21: 파일 전체 교체 (PREVIEW 포함)")
        void updatePost_ReplaceFiles_Preview() {
            // Given: 기존 파일 매핑 확인
            List<PostFileMap> originalMaps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
            assertThat(originalMaps).hasSize(2);
            List<Long> originalFileIds = originalMaps.stream()
                    .map(map -> map.getFile().getId())
                    .toList();
            assertThat(originalFileIds).containsExactlyInAnyOrder(
                    userAFiles.get(0).getId(), userAFiles.get(1).getId());

            // When: 파일 전체 교체
            List<PostDto.AttachedFileRequest> newFiles = List.of(
                    new PostDto.AttachedFileRequest(userAFiles.get(5).getId(), MediaRole.PREVIEW, 0)
            );
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().files(newFiles).build();

            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);
            flushAndClear(); // [중요] Bulk delete & insert sync

            // Then: 기존 매핑이 삭제되고 새로운 매핑만 존재
            List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
            assertThat(maps).hasSize(1);
            assertThat(maps.get(0).getFile().getId()).isEqualTo(userAFiles.get(5).getId());
            
            // 기존 파일 ID가 더 이상 매핑에 없는지 확인
            List<Long> newFileIds = maps.stream()
                    .map(map -> map.getFile().getId())
                    .toList();
            assertThat(newFileIds).doesNotContainAnyElementsOf(originalFileIds);

            // Thumbnail check
            assertThat(postRepository.findById(post.getId()).get().getThumbnailKey())
                    .isEqualTo(ThumbnailUtils.getMediumThumbnailKey(userAFiles.get(5).getS3ObjectKey()));
        }

        @Test
        @DisplayName("SCENE 22: 파일 전체 교체 (PREVIEW 없음)")
        void updatePost_ReplaceFiles_NoPreview() {
            List<PostDto.AttachedFileRequest> newFiles = List.of(
                    new PostDto.AttachedFileRequest(userAFiles.get(5).getId(), MediaRole.CONTENT, 0)
            );
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().files(newFiles).build();

            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);
            flushAndClear();

            assertThat(postRepository.findById(post.getId()).get().getThumbnailKey())
                    .isEqualTo(ThumbnailUtils.getMediumThumbnailKey(userAFiles.get(5).getS3ObjectKey())); // First file used
        }

        @Test
        @DisplayName("SCENE 23: 파일 삭제 (빈 리스트)")
        void updatePost_DeleteFiles() {
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().files(Collections.emptyList()).build();
            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);
            flushAndClear();

            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId())).isEmpty();
            assertThat(postRepository.findById(post.getId()).get().getThumbnailKey()).isNull();
        }

        @Test
        @DisplayName("SCENE 24: 파일 유지 (null)")
        void updatePost_KeepFiles() {
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().title("U").files(null).build();
            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);

            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId())).hasSize(2);
        }

        @Test
        @DisplayName("SCENE 25: 파일 추가 (기존+신규 -> 클라이언트가 전체 리스트를 보내야 함)")
        void updatePost_AddFiles() {
            // Given: 기존 파일 매핑 확인
            List<PostFileMap> originalMaps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
            assertThat(originalMaps).hasSize(2);
            Long originalFile0Id = userAFiles.get(0).getId();
            Long originalFile1Id = userAFiles.get(1).getId();

            // When: 기존 파일 + 신규 파일
            List<PostDto.AttachedFileRequest> files = new ArrayList<>();
            // Re-send existing
            files.add(new PostDto.AttachedFileRequest(originalFile0Id, MediaRole.PREVIEW, 0));
            files.add(new PostDto.AttachedFileRequest(originalFile1Id, MediaRole.CONTENT, 1));
            // Add new
            files.add(new PostDto.AttachedFileRequest(userAFiles.get(2).getId(), MediaRole.CONTENT, 2));

            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().files(files).build();
            postService.updatePost(UserPrincipal.from(userA), post.getId(), req);
            flushAndClear();

            // Then: 기존 파일 ID가 유지되고 신규 파일이 추가됨
            List<PostFileMap> maps = postFileMapRepository.findAllByPostIdOrderBySequenceAsc(post.getId());
            assertThat(maps).hasSize(3);
            
            List<Long> fileIds = maps.stream()
                    .map(map -> map.getFile().getId())
                    .toList();
            assertThat(fileIds).contains(originalFile0Id); // 기존 파일 유지
            assertThat(fileIds).contains(originalFile1Id); // 기존 파일 유지
            assertThat(fileIds).contains(userAFiles.get(2).getId()); // 신규 파일 추가
            
            // Sequence 검증
            assertThat(maps.get(0).getSequence()).isEqualTo(0);
            assertThat(maps.get(1).getSequence()).isEqualTo(1);
            assertThat(maps.get(2).getSequence()).isEqualTo(2);
        }

        @Test
        @DisplayName("SCENE 26~28: 예외 케이스")
        void updatePost_Exceptions() {
            PostDto.UpdateRequest req = PostDto.UpdateRequest.builder().title("Hacked").build();

            // 26: Forbidden
            assertThatThrownBy(() -> postService.updatePost(UserPrincipal.from(userB), post.getId(), req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 27: Not Found
            assertThatThrownBy(() -> postService.updatePost(UserPrincipal.from(userA), 99999L, req))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

            // 28: IDOR
            List<PostDto.AttachedFileRequest> idorFiles = List.of(new PostDto.AttachedFileRequest(userBFiles.get(0).getId(), MediaRole.CONTENT, 0));
            PostDto.UpdateRequest idorReq = PostDto.UpdateRequest.builder().files(idorFiles).build();
            assertThatThrownBy(() -> postService.updatePost(UserPrincipal.from(userA), post.getId(), idorReq))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // ========================================================================================
    // [Category 4]: Delete
    // ========================================================================================
    @Nested
    @DisplayName("[Category 4] Delete Post")
    class Delete {
        @Test
        @DisplayName("SCENE 29: 정상 케이스 (파일 포함)")
        void deletePost_WithFiles() {
            // Setup
            setupMockUser(userA);
            List<PostDto.AttachedFileRequest> files = List.of(new PostDto.AttachedFileRequest(userAFiles.get(0).getId(), MediaRole.CONTENT, 0));
            PostDto.CreateRequest req = PostDto.CreateRequest.builder().title("Del").content("C").category(Category.SPORT).files(files).build();
            PostDto.Response res = postService.createPost(UserPrincipal.from(userA), req);
            Long postId = res.getId();
            flushAndClear();

            // When
            postService.deletePost(UserPrincipal.from(userA), postId);
            flushAndClear(); // Bulk delete sync

            // Then
            assertThat(postRepository.existsById(postId)).isFalse();
            assertThat(postFileMapRepository.findAllByPostIdOrderBySequenceAsc(postId)).isEmpty();
            assertThat(fileRepository.existsById(userAFiles.get(0).getId())).isTrue(); // File itself remains
        }

        @Test
        @DisplayName("SCENE 30: 정상 케이스 (파일 없음)")
        void deletePost_NoFiles() {
            setupMockUser(userA);
            PostDto.CreateRequest req = PostDto.CreateRequest.builder().title("Del").content("C").category(Category.SPORT).build();
            PostDto.Response res = postService.createPost(UserPrincipal.from(userA), req);
            Long postId = res.getId();
            flushAndClear();

            postService.deletePost(UserPrincipal.from(userA), postId);
            flushAndClear();

            assertThat(postRepository.existsById(postId)).isFalse();
        }

        @Test
        @DisplayName("SCENE 31~32: 예외 케이스")
        void deletePost_Exceptions() {
            setupMockUser(userA);
            PostDto.CreateRequest req = PostDto.CreateRequest.builder().title("Del").content("C").category(Category.SPORT).build();
            PostDto.Response res = postService.createPost(UserPrincipal.from(userA), req);
            Long postId = res.getId();

            // 31: Forbidden
            assertThatThrownBy(() -> postService.deletePost(UserPrincipal.from(userB), postId))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);

            // 32: Not Found
            assertThatThrownBy(() -> postService.deletePost(UserPrincipal.from(userA), 99999L))
                    .isInstanceOf(RestException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        }
    }

    // ========================================================================================
    // [Category 5]: Read-Pagination
    // ========================================================================================
    @Nested
    @DisplayName("[Category 5] Pagination")
    class Pagination {
        @BeforeEach
        void setUpPosts() {
            // Create 30 posts mixed categories and scores for testing sorting
            setupMockUser(userA);
            for (int i = 1; i <= 30; i++) {
                Category cat = (i % 2 == 0) ? Category.SPORT : Category.IDOL;
                Post post = Post.builder()
                        .user(userA)
                        .title("Post " + i)
                        .content("Content")
                        .category(cat)
                        .viewCount((long) i) // ID 비례
                        .likeCount((long) i)
                        .hotScore(i * 10.0) // Explicit hotScore for sorting test
                        .build();
                postRepository.save(post);
            }
            flushAndClear();
        }

        @Test
        @DisplayName("SCENE 33: 전체 카테고리, 기본 정렬 (최신순)")
        void getPosts_All_Default() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setPage(0); req.setSize(10); req.setSort("createdAt"); req.setDirection("DESC");

            PageDto.PageListResponse<PostDto.PostPageResponse> res = postService.getPosts(req);

            assertThat(res.getContent()).hasSize(10);
            assertThat(res.getContent().get(0).getTitle()).isEqualTo("Post 30"); // Latest
            assertThat(res.getTitle()).contains("전체");
            
            // 정렬 검증: createdAt DESC (최신순)
            List<PostDto.PostPageResponse> content = res.getContent();
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getCreatedAt())
                        .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
            }
        }

        @Test
        @DisplayName("SCENE 34: 전체 카테고리, hotScore 정렬")
        void getPosts_All_Hot() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setSort("hotScore"); req.setDirection("DESC");

            PageDto.PageListResponse<PostDto.PostPageResponse> res = postService.getPosts(req);

            // Post 30 has highest hotScore (300.0)
            assertThat(res.getContent().get(0).getTitle()).isEqualTo("Post 30");
            assertThat(res.getContent().get(9).getTitle()).isEqualTo("Post 21");
            assertThat(res.getTitle()).contains("핫한");
            
            // 정렬 검증: hotScore DESC
            List<PostDto.PostPageResponse> content = res.getContent();
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getHotScore())
                        .isGreaterThanOrEqualTo(content.get(i + 1).getHotScore());
            }
        }

        @Test
        @DisplayName("SCENE 35: 특정 카테고리 (SPORT), 기본 정렬")
        void getPosts_Category_Default() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setCategory(Category.SPORT);

            PageDto.PageListResponse<PostDto.PostPageResponse> res = postService.getPosts(req);

            assertThat(res.getContent()).allMatch(p -> p.getCategory() == Category.SPORT);
            assertThat(res.getContent()).hasSize(10);
            assertThat(res.getContent().get(0).getTitle()).isEqualTo("Post 30"); // Even numbers are SPORT
        }

        @Test
        @DisplayName("SCENE 36: 특정 카테고리 (IDOL), hotScore 정렬")
        void getPosts_Category_Hot() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setCategory(Category.IDOL);
            req.setSort("hotScore");
            req.setDirection("DESC");

            PageDto.PageListResponse<PostDto.PostPageResponse> res = postService.getPosts(req);

            assertThat(res.getContent()).allMatch(p -> p.getCategory() == Category.IDOL);
            assertThat(res.getContent().get(0).getTitle()).isEqualTo("Post 29"); // Odd numbers are IDOL (Highest odd is 29)
            
            // 정렬 검증: hotScore DESC
            List<PostDto.PostPageResponse> content = res.getContent();
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getHotScore())
                        .isGreaterThanOrEqualTo(content.get(i + 1).getHotScore());
            }
        }

        @Test
        @DisplayName("SCENE 37: 각 카테고리별 조회")
        void getPosts_EachCategory() {
            for (Category cat : List.of(Category.SPORT, Category.IDOL)) {
                PostDto.PostPageRequest req = new PostDto.PostPageRequest();
                req.setCategory(cat);

                PageDto.PageListResponse<PostDto.PostPageResponse> res = postService.getPosts(req);
                assertThat(res.getContent()).isNotEmpty();
                assertThat(res.getContent()).allMatch(p -> p.getCategory() == cat);
            }
        }

        @Test
        @DisplayName("SCENE 38~40: HotScore 정렬 상세")
        void getPosts_HotScore_Detail() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setSort("hotScore");
            req.setDirection("DESC");

            List<PostDto.PostPageResponse> content = postService.getPosts(req).getContent();

            // Check order: 30, 29, 28...
            assertThat(content.get(0).getTitle()).isEqualTo("Post 30");
            assertThat(content.get(1).getTitle()).isEqualTo("Post 29");
        }

        @Test
        @DisplayName("SCENE 41~43: 페이지네이션 (첫/중간/끝)")
        void getPosts_Pagination() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setSize(10);

            // 41: First Page
            req.setPage(0);
            assertThat(postService.getPosts(req).getContent()).hasSize(10);

            // 42: Middle Page
            req.setPage(1);
            assertThat(postService.getPosts(req).getContent()).hasSize(10);

            // 43: Last Page (Total 30 -> 3 pages. Page 2 is last)
            req.setPage(2);
            assertThat(postService.getPosts(req).getContent()).hasSize(10);

            req.setPage(3);
            assertThatThrownBy(() -> postService.getPosts(req))
                    .isInstanceOf(RestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("SCENE 44: 페이지 크기 변경")
        void getPosts_PageSize() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();
            req.setSize(20);
            assertThat(postService.getPosts(req).getContent()).hasSize(20);
        }

        @Test
        @DisplayName("SCENE 45~48: Edge Cases")
        void getPosts_Edge() {
            PostDto.PostPageRequest req = new PostDto.PostPageRequest();

            // 45: Empty (Delete all first)
            postRepository.deleteAll();
            assertThat(postService.getPosts(req).getContent()).isEmpty();

            // 46: Specific category empty
            req.setCategory(Category.ARTIST);
            assertThat(postService.getPosts(req).getContent()).isEmpty();

            // 47: Page Out Range
            req.setCategory(null);
            req.setPage(100);
            assertThatThrownBy(() -> postService.getPosts(req)).isInstanceOf(RestException.class);
        }
    }
}