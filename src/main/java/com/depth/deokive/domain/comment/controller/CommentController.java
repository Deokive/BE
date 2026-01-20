package com.depth.deokive.domain.comment.controller;

import com.depth.deokive.domain.comment.dto.CommentDto;
import com.depth.deokive.domain.comment.service.CommentService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "댓글 작성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (대댓글 깊이 초과 / 부모 댓글 불일치)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL BAD REQUEST\", \"message\": \"잘못된 요청입니다. (대대댓글 작성 불가)\"}")
                    )),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글 또는 부모 댓글",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"POST_NOT_FOUND\", \"message\": \"존재하지 않는 게시글입니다.\"}")
                    ))
    })
    public ResponseEntity<Void> createComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CommentDto.Request request
    ) {
        commentService.createComment(userPrincipal, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 조회", description = "특정 게시글의 댓글 목록을 무한 스크롤로 조회.")
    @GetMapping("/posts/{postId}/comments")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 게시글",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"POST_NOT_FOUND\", \"message\": \"존재하지 않는 게시글입니다.\"}")
                    ))
    })
    public ResponseEntity<Slice<CommentDto.Response>> getComments(
            @PathVariable Long postId,
            @RequestParam(required = false) Long lastCommentId,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal userPrincipal
            ) {
        // 로그인 X -> null, 로그인 O -> Id
        Long currentUserId = (userPrincipal != null) ? userPrincipal.getUserId() : null;

        return ResponseEntity.ok(commentService.getComments(postId, lastCommentId, pageable, currentUserId));
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다.")
    @DeleteMapping("/comments/{commentId}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (본인 댓글 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}")
                    )),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 댓글",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"COMMENT_NOT_FOUND\", \"message\": \"존재하지 않는 댓글입니다.\"}")
                    ))
    })
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(userPrincipal, commentId);
        return ResponseEntity.ok().build();
    }
}