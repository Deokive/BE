package com.depth.deokive.domain.post.service;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.file.service.FileService;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostFileMapRepository;
import com.depth.deokive.domain.post.repository.PostQueryRepository;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.exception.model.ErrorCode;
import com.depth.deokive.system.exception.model.RestException;
import com.depth.deokive.system.security.model.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @InjectMocks private PostService postService;

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostFileMapRepository postFileMapRepository;
    @Mock private FileService fileService;
    @Mock private PostQueryRepository postQueryRepository;

    // Helper: UserPrincipal 생성
    private UserPrincipal createUserPrincipal(Long id) {
        return UserPrincipal.builder()
                .userId(id)
                .username("tester" + id)
                .role(Role.USER)
                .build();
    }

    // --- [Scenario 1. 썸네일 선정 로직 검증 (Business Logic)] ---

    @Test
    @DisplayName("성공: 파일 중 PREVIEW 역할이 있다면 Sequence가 늦어도 썸네일로 지정된다.")
    void createPost_Thumbnail_Priority_Preview() {
        // given
        Long userId = 1L;
        UserPrincipal principal = createUserPrincipal(userId);
        User user = User.builder().id(userId).build();

        // File 1: Content 역할 (Seq 0)
        File fileContent = File.builder().id(101L).filePath("content.jpg").mediaType(MediaType.IMAGE).createdBy(userId).build();
        // File 2: Preview 역할 (Seq 1) -> 기대 썸네일
        File filePreview = File.builder().id(102L).filePath("preview.jpg").mediaType(MediaType.IMAGE).createdBy(userId).build();

        PostDto.Request request = PostDto.Request.builder()
                .title("Test Post")
                .content("Content")
                .category(Category.IDOL)
                .files(List.of(
                        new PostDto.AttachedFileRequest(101L, MediaRole.CONTENT, 0),
                        new PostDto.AttachedFileRequest(102L, MediaRole.PREVIEW, 1)
                ))
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(fileService.validateFileOwners(anyList(), any())).willReturn(List.of(fileContent, filePreview));

        // Mocking: saveAll이 호출될 때 내부적으로 생성된 PostFileMap 리스트를 그대로 반환하도록 설정
        given(postFileMapRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        // ArgumentCaptor 설정
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        ArgumentCaptor<List<PostFileMap>> postFileMapListCaptor = ArgumentCaptor.forClass(List.class);

        // when
        postService.createPost(principal, request);

        // then
        // 1. Post 저장이 호출되었는가?
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        
        // 2. [검증 핵심] PREVIEW 파일이 썸네일로 선정되었는지 확인
        assertThat(savedPost.getThumbnailFile()).isNotNull();
        assertThat(savedPost.getThumbnailFile().getId()).isEqualTo(102L); // filePreview가 썸네일로 선정됨
        assertThat(savedPost.getThumbnailFile().getFilePath()).isEqualTo("preview.jpg");

        // 3. PostFileMap 저장 로직이 수행되었는가?
        verify(postFileMapRepository).saveAll(postFileMapListCaptor.capture());
        List<PostFileMap> savedMaps = postFileMapListCaptor.getValue();
        assertThat(savedMaps).hasSize(2);
        
        // PostFileMap 내용 검증
        assertThat(savedMaps.get(0).getFile().getId()).isEqualTo(101L);
        assertThat(savedMaps.get(0).getMediaRole()).isEqualTo(MediaRole.CONTENT);
        assertThat(savedMaps.get(0).getSequence()).isEqualTo(0);
        
        assertThat(savedMaps.get(1).getFile().getId()).isEqualTo(102L);
        assertThat(savedMaps.get(1).getMediaRole()).isEqualTo(MediaRole.PREVIEW);
        assertThat(savedMaps.get(1).getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: PREVIEW 파일이 없다면 Sequence가 가장 빠른(0번) 파일이 썸네일이 된다.")
    void createPost_Thumbnail_Fallback_Sequence() {
        // given
        Long userId = 1L;
        UserPrincipal principal = createUserPrincipal(userId);
        User user = User.builder().id(userId).build();

        // [수정] mediaType을 명시적으로 설정해야 DTO 변환 시 에러가 안 납니다.
        File file1 = File.builder()
                .id(101L)
                .filePath("img1.jpg")
                .mediaType(MediaType.IMAGE) // <--- 추가 필수
                .createdBy(userId)
                .build();

        File file2 = File.builder()
                .id(102L)
                .filePath("img2.jpg")
                .mediaType(MediaType.IMAGE) // <--- 추가 필수
                .createdBy(userId)
                .build();

        PostDto.Request request = PostDto.Request.builder()
                .title("Test")
                .content("C")
                .category(Category.IDOL)
                .files(List.of(
                        new PostDto.AttachedFileRequest(101L, MediaRole.CONTENT, 0),
                        new PostDto.AttachedFileRequest(102L, MediaRole.CONTENT, 1)
                ))
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(fileService.validateFileOwners(anyList(), any())).willReturn(List.of(file1, file2));

        // Mocking: Service 로직이 saveAll에 전달한 리스트를 그대로 반환하도록 설정
        given(postFileMapRepository.saveAll(anyList())).willAnswer(i -> i.getArgument(0));

        // ArgumentCaptor 설정
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);

        // when
        // 여기서 PostDto.Response.of(...) 가 호출되면서 file.getMediaType().name()을 실행함
        postService.createPost(principal, request);

        // then
        // 1. Post 저장이 호출되었는가?
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        
        // 2. [검증 핵심] Sequence가 0인 file1이 썸네일로 선정되었는지 확인
        assertThat(savedPost.getThumbnailFile()).isNotNull();
        assertThat(savedPost.getThumbnailFile().getId()).isEqualTo(101L); // file1 (sequence=0)이 썸네일로 선정됨
        assertThat(savedPost.getThumbnailFile().getFilePath()).isEqualTo("img1.jpg");

        // 3. PostFileMap 저장 확인
        verify(postFileMapRepository).saveAll(anyList());
    }

    // --- [Scenario 2. 권한 및 예외 검증 (Security & Validation)] ---

    @Test
    @DisplayName("실패: 본인의 게시글이 아니면 수정할 수 없다 (AUTH_FORBIDDEN).")
    void updatePost_Fail_Forbidden() {
        // given
        Long ownerId = 1L;
        Long attackerId = 2L;

        User owner = User.builder().id(ownerId).build();
        Post post = Post.builder().id(10L).user(owner).build(); // 주인 설정

        UserPrincipal attackerPrincipal = createUserPrincipal(attackerId); // 공격자

        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> postService.updatePost(attackerPrincipal, 10L, new PostDto.Request()))
                .isInstanceOf(RestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("실패: 요청한 파일 ID 중 존재하지 않거나 본인 소유가 아닌 파일이 있으면 실패한다.")
    void createPost_Fail_FileNotFound() {
        // given
        UserPrincipal principal = createUserPrincipal(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(User.builder().id(1L).build()));

        PostDto.Request request = PostDto.Request.builder()
                .title("Title")
                .content("Content")
                .category(Category.IDOL)
                .files(List.of(new PostDto.AttachedFileRequest(999L, MediaRole.CONTENT, 0)))
                .build();

        // FileService가 빈 리스트를 반환하거나 예외를 던지도록 설정 (여기선 로직상 빈 리스트 반환 시 Service에서 예외 발생)
        given(fileService.validateFileOwners(anyList(), any())).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> postService.createPost(principal, request))
                .isInstanceOf(RestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("성공: 파일이 없는 경우 썸네일이 null로 설정된다.")
    void createPost_Thumbnail_Null_When_NoFiles() {
        // given
        Long userId = 1L;
        UserPrincipal principal = createUserPrincipal(userId);
        User user = User.builder().id(userId).build();

        PostDto.Request request = PostDto.Request.builder()
                .title("Test Post")
                .content("Content")
                .category(Category.IDOL)
                .files(null) // 파일 없음
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // ArgumentCaptor 설정
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(principal, request);

        // then
        // 1. Post 저장이 호출되었는가?
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        
        // 2. [검증 핵심] 파일이 없으면 썸네일이 null로 설정되는지 확인
        assertThat(savedPost.getThumbnailFile()).isNull();

        // 3. PostFileMap 저장이 호출되지 않았는지 확인
        verify(postFileMapRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("성공: 여러 PREVIEW 파일이 있을 때 첫 번째 PREVIEW가 선택된다.")
    void createPost_Thumbnail_FirstPreview_When_MultiplePreviews() {
        // given
        Long userId = 1L;
        UserPrincipal principal = createUserPrincipal(userId);
        User user = User.builder().id(userId).build();

        // File 1: PREVIEW 역할 (Seq 0) -> 첫 번째 PREVIEW, 기대 썸네일
        File filePreview1 = File.builder().id(101L).filePath("preview1.jpg").mediaType(MediaType.IMAGE).createdBy(userId).build();
        // File 2: PREVIEW 역할 (Seq 1) -> 두 번째 PREVIEW
        File filePreview2 = File.builder().id(102L).filePath("preview2.jpg").mediaType(MediaType.IMAGE).createdBy(userId).build();
        // File 3: CONTENT 역할 (Seq 2)
        File fileContent = File.builder().id(103L).filePath("content.jpg").mediaType(MediaType.IMAGE).createdBy(userId).build();

        PostDto.Request request = PostDto.Request.builder()
                .title("Test Post")
                .content("Content")
                .category(Category.IDOL)
                .files(List.of(
                        new PostDto.AttachedFileRequest(101L, MediaRole.PREVIEW, 0),
                        new PostDto.AttachedFileRequest(102L, MediaRole.PREVIEW, 1),
                        new PostDto.AttachedFileRequest(103L, MediaRole.CONTENT, 2)
                ))
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(fileService.validateFileOwners(anyList(), any())).willReturn(List.of(filePreview1, filePreview2, fileContent));

        // Mocking: saveAll이 호출될 때 내부적으로 생성된 PostFileMap 리스트를 그대로 반환하도록 설정
        given(postFileMapRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        // ArgumentCaptor 설정
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        ArgumentCaptor<List<PostFileMap>> postFileMapListCaptor = ArgumentCaptor.forClass(List.class);

        // when
        postService.createPost(principal, request);

        // then
        // 1. Post 저장이 호출되었는가?
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        
        // 2. [검증 핵심] 첫 번째 PREVIEW 파일(filePreview1)이 썸네일로 선정되었는지 확인
        assertThat(savedPost.getThumbnailFile()).isNotNull();
        assertThat(savedPost.getThumbnailFile().getId()).isEqualTo(101L); // 첫 번째 PREVIEW가 썸네일로 선정됨
        assertThat(savedPost.getThumbnailFile().getFilePath()).isEqualTo("preview1.jpg");

        // 3. PostFileMap 저장 로직이 수행되었는가?
        verify(postFileMapRepository).saveAll(postFileMapListCaptor.capture());
        List<PostFileMap> savedMaps = postFileMapListCaptor.getValue();
        assertThat(savedMaps).hasSize(3);
        
        // PostFileMap 내용 검증
        assertThat(savedMaps.get(0).getFile().getId()).isEqualTo(101L);
        assertThat(savedMaps.get(0).getMediaRole()).isEqualTo(MediaRole.PREVIEW);
        assertThat(savedMaps.get(0).getSequence()).isEqualTo(0);
        
        assertThat(savedMaps.get(1).getFile().getId()).isEqualTo(102L);
        assertThat(savedMaps.get(1).getMediaRole()).isEqualTo(MediaRole.PREVIEW);
        assertThat(savedMaps.get(1).getSequence()).isEqualTo(1);
        
        assertThat(savedMaps.get(2).getFile().getId()).isEqualTo(103L);
        assertThat(savedMaps.get(2).getMediaRole()).isEqualTo(MediaRole.CONTENT);
        assertThat(savedMaps.get(2).getSequence()).isEqualTo(2);
    }
}