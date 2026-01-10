package com.depth.deokive.domain.comment.dto;

import com.depth.deokive.domain.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentDto {
    @Getter
    @NoArgsConstructor
    @Schema(description = "댓글 생성 요청")
    public static class Request {
        @Schema(description = "게시글 ID")
        private Long postId;

        @Schema(description = "댓글 내용")
        private String content;

        @Schema(description = "부모 댓글 ID")
        private Long parentId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "댓글 응답")
    public static class Response {
        private Long commentId;
        private String content;
        private Long userId;
        private String nickname;
        private boolean isDeleted;
        private LocalDateTime createdAt;
        private List<Response> children;

        public static Response from(Comment comment) {
            return Response.builder()
                    .commentId(comment.getId())
                    .content(comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent())
                    .userId(comment.getUser().getId())
                    .nickname(comment.getUser().getNickname())
                    .isDeleted(comment.isDeleted())
                    .createdAt(comment.getCreatedAt())
                    .children(new ArrayList<>())
                    .build();
        }
    }
}
