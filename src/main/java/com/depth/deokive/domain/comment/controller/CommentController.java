package com.depth.deokive.domain.comment.controller;

import com.depth.deokive.domain.comment.dto.CommentDto;
import com.depth.deokive.domain.comment.service.CommentService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Comment API", description = "댓글 관련 API")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 생성", description = "게시글에 댓글(또는 대댓글)을 작성합니다.")
    @PostMapping("/comments")
    public ResponseEntity<Void> createComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CommentDto.Request request
    ) {
        commentService.createComment(userPrincipal, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 조회", description = "특정 게시글의 댓글 목록을 무한 스크롤로 조회.")
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<Slice<CommentDto.Response>> getComments(
            @PathVariable Long postId,
            @RequestParam(required = false) Long lastCommentId,
            @PageableDefault(size = 10) Pageable pageable
            ) {
        return ResponseEntity.ok(commentService.getComments(postId, lastCommentId, pageable));
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다. 자식이 있으면 '삭제된 댓글'로 표시됩니다.")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(userPrincipal, commentId);
        return ResponseEntity.ok().build();
    }
}