package com.depth.deokive.domain.post.controller;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.domain.post.dto.PostDto;
import com.depth.deokive.domain.post.service.PostService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
@Tag(name = "Post", description = "게시글 관리 API")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "게시글 생성", description = "파일 선업로드 후 받은 fileId들을 포함하여 게시글을 생성합니다.")
    @ApiResponse(responseCode = "201", description = "게시글 생성 성공")
    public ResponseEntity<PostDto.Response> createPost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PostDto.CreateRequest request
    ) {
        PostDto.Response response = postService.createPost(userPrincipal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "게시글 조회 성공")
    public ResponseEntity<PostDto.Response> getPost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            HttpServletRequest request
    ) {
        PostDto.Response response = postService.getPost(userPrincipal, postId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "게시글 제목, 내용, 카테고리 및 첨부파일 목록을 수정합니다.")
    @ApiResponse(responseCode = "200", description = "게시글 수정 성공")
    public ResponseEntity<PostDto.Response> updatePost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody PostDto.UpdateRequest request
    ) {
        PostDto.Response response = postService.updatePost(userPrincipal, postId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "게시글 삭제 성공")
    public ResponseEntity<Void> deletePost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId
    ) {
        postService.deletePost(userPrincipal, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(
            summary = "게시글 피드 목록 조회",
            description = "카테고리별 게시글을 페이징하여 조회합니다. (정렬: createdAt, viewCount, likeCount, hotScore)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<PageDto.PageListResponse<PostDto.PostPageResponse>> getPosts(
            @Valid @ModelAttribute PostDto.PostPageRequest request
    ) {
        return ResponseEntity.ok(postService.getPosts(request));
    }

    @PostMapping("/{postId}/like")
    @Operation(summary = "게시글 좋아요 토글", description = "좋아요를 처리합니다.")
    public ResponseEntity<PostDto.LikeResponse> toggleLike(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.toggleLike(userPrincipal, postId));
    }
}
