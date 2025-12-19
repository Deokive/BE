package com.depth.deokive.domain.post.controller;

import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.file.repository.FileRepository;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.post.repository.PostRepository;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.repository.UserRepository;
import com.depth.deokive.system.security.model.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean(name = "s3Client")
    private S3Client s3Client;

    @Autowired private UserRepository userRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private PostRepository postRepository;

    private User savedUser;
    private File savedFile;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        fileRepository.deleteAll();
        userRepository.deleteAll();

        savedUser = userRepository.save(User.builder()
                .email("test@depth.com")
                .username("testUser")
                .nickname("Tester")
                .password("password")
                .role(Role.USER)
                .isEmailVerified(true)
                .build());

        savedFile = fileRepository.save(File.builder()
                .s3ObjectKey("test/key/1")
                .filename("image.jpg")
                .filePath("http://cdn.com/image.jpg")
                .fileSize(1024L)
                .mediaType(com.depth.deokive.domain.file.entity.enums.MediaType.IMAGE)
                .build());
    }

    @Test
    @DisplayName("✅ 게시글 생성: 정상 요청 시 201 Created 반환")
    void createPost_Success() throws Exception {
        PostDto.AttachedFileRequest fileReq = PostDto.AttachedFileRequest.builder()
                .fileId(savedFile.getId())
                .mediaRole(MediaRole.CONTENT)
                .sequence(1)
                .build();

        PostDto.Request request = PostDto.Request.builder()
                .title("New Post")
                .content("Content")
                .category(Category.IDOL)
                .files(List.of(fileReq))
                .build();

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(UserPrincipal.from(savedUser))) // 인증 정보 주입
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Post"));
    }

    @Test
    @DisplayName("User Flow: 생성 -> 조회 -> 수정 -> 삭제")
    void userFlow_FullCycle() throws Exception {
        // 1. Create
        PostDto.Request createRequest = PostDto.Request.builder()
                .title("Flow Title")
                .content("Flow Content")
                .category(Category.ARTIST)
                .files(List.of(new PostDto.AttachedFileRequest(savedFile.getId(), MediaRole.CONTENT, 1)))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                        .with(user(UserPrincipal.from(savedUser)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        PostDto.Response createdResponse = objectMapper.readValue(responseJson, PostDto.Response.class);
        Long postId = createdResponse.getId();

        // 2. Get
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .with(user(UserPrincipal.from(savedUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Flow Title"));

        // 3. Update
        PostDto.Request updateRequest = PostDto.Request.builder()
                .title("Updated Title")
                .content("Updated Content")
                .category(Category.ARTIST)
                .files(List.of())
                .build();

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .with(user(UserPrincipal.from(savedUser)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));

        // 4. Delete
        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .with(user(UserPrincipal.from(savedUser)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // 5. Verify Delete (404)
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .with(user(UserPrincipal.from(savedUser))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Authorization: 다른 사람의 글을 삭제하려 하면 403 Forbidden")
    void deletePost_Forbidden_Fail() throws Exception {
        Post post = postRepository.save(Post.builder()
                .title("A's Post")
                .content("..")
                .category(Category.IDOL)
                .user(savedUser)
                .build());

        User attacker = userRepository.save(User.builder()
                .email("hacker@depth.com").username("hacker").nickname("Hacker")
                .password("pw").role(Role.USER).build());

        mockMvc.perform(delete("/api/v1/posts/{postId}", post.getId())
                        .with(user(UserPrincipal.from(attacker)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}