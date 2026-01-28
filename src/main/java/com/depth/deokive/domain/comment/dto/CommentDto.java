package com.depth.deokive.domain.comment.dto;

import com.depth.deokive.domain.comment.entity.Comment;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentDto {

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(name = "CommentSliceResponse", description = "댓글 목록 응답 (totalCount 포함)")
    public static class SliceResponse {
        @Schema(description = "댓글 목록")
        private List<Response> content;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private boolean hasNext;

        @Schema(description = "전체 댓글 수", example = "42")
        private long totalCount;

        public static SliceResponse of(Slice<Response> slice, long totalCount) {
            return SliceResponse.builder()
                    .content(slice.getContent())
                    .hasNext(slice.hasNext())
                    .totalCount(totalCount)
                    .build();
        }
    }
    @Getter
    @NoArgsConstructor
    @Schema(description = "댓글 생성 요청")
    public static class Request {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;

        @Schema(description = "댓글 내용", example = "ㅋㅋㅋㅋㅋ")
        private String content;

        @Schema(description = "부모 댓글 ID", example = "null")
        private Long parentId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(name = "CommentResponse", description = "댓글 응답 DTO (계층형 구조)")
    public static class Response {
        @Schema(description = "댓글 ID", example = "10")
        private Long commentId;

        @Schema(description = "댓글 내용", example = "개꿀!")
        private String content;

        @Schema(description = "작성자 User ID", example = "5")
        private Long userId;

        @Schema(description = "작성자 닉네임", example = "덕카이브 홧팅")
        private String nickname;

        @Schema(description = "삭제 여부", example = "false")
        private boolean isDeleted;

        @Schema(description = "작성 일시")
        private LocalDateTime createdAt;

        @Builder.Default
        @JsonProperty("isOwner")
        @Schema(description = "본인 작성 여부 (true면 삭제 버튼 노출)", example = "true")
        private Boolean isOwner = false;

        @Schema(description = "대댓글 리스트")
        private List<Response> children;

        public static Response from(Comment comment, Long currentUserId) {
            return Response.builder()
                    .commentId(comment.getId())
                    .content(comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent())
                    .userId(comment.getUser().getId())
                    .nickname(comment.getUser().getNickname())
                    .isDeleted(comment.isDeleted())
                    .createdAt(comment.getCreatedAt())
                    .isOwner(currentUserId != null && currentUserId.equals(comment.getUser().getId()))
                    .children(new ArrayList<>())
                    .build();
        }
    }
}
