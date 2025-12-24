package com.depth.deokive.domain.post.dto;

import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.entity.RepostTab;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class RepostDto {

    @Data @NoArgsConstructor
    @Schema(description = "리포스트 생성 요청")
    public static class CreateRequest {
        @NotNull(message = "원본 게시글 ID는 필수입니다.")
        @Schema(description = "원본 게시글 ID", example = "1")
        private Long postId;
    }

    @Data @NoArgsConstructor
    @Schema(description = "리포스트 제목 수정 요청")
    public static class UpdateRequest {
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "변경할 리포스트 제목", example = "수정된 제목")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "리포스트 응답")
    public static class Response {
        @Schema(description = "리포스트 아이디", example = "1")
        private Long id;
        
        @Schema(description = "원본 게시글 ID (프론트는 이 ID로 /posts/{postId} 호출)", example = "5")
        private Long postId; // 프론트는 이 ID로 /posts/{postId} 호출 -> 404 체크
        
        @Schema(description = "리포스트 제목", example = "내가 리포스트한 게시글")
        private String title;

        @Schema(description = "썸네일 이미지 URL", example = "https://cdn.example.com/files/thumbnails/thumbnail/uuid_filename.jpg")
        private String thumbnailUrl;
        
        @Schema(description = "소속 리포스트 탭 ID", example = "1")
        private Long repostTabId;

        public static Response of(Repost repost) {
            return Response.builder()
                    .id(repost.getId())
                    .postId(repost.getPostId())
                    .title(repost.getTitle())
                    .thumbnailUrl(repost.getThumbnailUrl())
                    .repostTabId(repost.getRepostTab().getId())
                    .build();
        }
    }

    @Data @NoArgsConstructor
    @Schema(description = "리포스트 탭 제목 수정 요청")
    public static class UpdateTabRequest {
        @NotBlank(message = "탭 제목은 필수입니다.") // update만 있는 이유는 UX 상 탭추가 누르면 기본 이름(ex. 탭이름)이 먼저 생성됨
        @Schema(description = "변경할 탭 제목", example = "내가 좋아하는 게시글")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "리포스트 탭 응답")
    public static class TabResponse {
        @Schema(description = "리포스트 탭 아이디", example = "1")
        private Long id;
        
        @Schema(description = "탭 제목", example = "내가 좋아하는 게시글")
        private String title;
        
        @Schema(description = "소속 리포스트북 ID", example = "1")
        private Long repostBookId;

        public static TabResponse of(RepostTab tab) {
            return TabResponse.builder()
                    .id(tab.getId())
                    .title(tab.getTitle())
                    .repostBookId(tab.getRepostBook().getId())
                    .build();
        }
    }
}