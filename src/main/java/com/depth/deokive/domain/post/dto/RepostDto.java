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
        private Long postId;

        @Schema(description = "사용자 지정 제목 (비워두면 원본 제목 사용)")
        private String customTitle;
    }

    @Data @NoArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "제목은 필수입니다.")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "리포스트 응답")
    public static class Response {
        private Long id;
        private Long postId; // 프론트는 이 ID로 /posts/{postId} 호출 -> 404 체크
        private String title;
        private String thumbnailUrl;
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
    public static class UpdateTabRequest {
        @NotBlank(message = "탭 제목은 필수입니다.") // update만 있는 이유는 UX 상 탭추가 누르면 기본 이름(ex. 탭이름)이 먼저 생성됨
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    public static class TabResponse {
        private Long id;
        private String title;
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