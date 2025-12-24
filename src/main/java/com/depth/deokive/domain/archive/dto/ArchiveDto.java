package com.depth.deokive.domain.archive.dto;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ArchiveDto {

    @Data @NoArgsConstructor
    @Schema(description = "아카이브 생성 요청")
    public static class CreateRequest {
        @NotBlank(message = "아카이브 제목은 필수입니다.")
        @Schema(description = "아카이브 제목", example = "나의 첫 아카이브")
        private String title;

        @NotNull(message = "공개 범위 설정은 필수입니다.")
        @Schema(description = "공개 범위", example = "PUBLIC | RESTRICTED | PRIVATE")
        private Visibility visibility;

        @Schema(description = "배너 이미지 파일 ID (업로드 후 반환된 ID)", example = "105")
        private Long bannerImageId;
    }

    @Data @NoArgsConstructor
    @Schema(description = "아카이브 수정 요청")
    public static class UpdateRequest {
        @Schema(description = "아카이브 제목", example = "수정된 아카이브 제목")
        private String title;

        @Schema(description = "공개 범위", example = "PUBLIC | RESTRICTED | PRIVATE")
        private Visibility visibility;

        @Schema(description = "변경할 배너 이미지 ID (null: 유지, -1: 삭제, 그외: 변경)", example = "105")
        private Long bannerImageId;
    }

    @Data @Builder @AllArgsConstructor
    // @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "아카이브 응답 (피드/상세 공용)")
    public static class Response {
        @Schema(description = "아카이브 아이디", example = "1")
        private Long id;
        
        @Schema(description = "아카이브 제목", example = "나의 첫 아카이브")
        private String title;
        
        @Schema(description = "공개 범위", example = "PUBLIC | RESTRICTED | PRIVATE")
        private Visibility visibility;
        
        @Schema(description = "아카이브 뱃지", example = "NEWBIE | BEGINNER | INTERMEDIATE | ADVANCED | EXPERT | MASTER")
        private Badge badge;
        
        @Schema(description = "배너 이미지 URL", example = "https://cdn.example.com/files/banner.jpg")
        private String bannerUrl;      // 배너 이미지 URL
        
        @Schema(description = "조회수", example = "150")
        private long viewCount;
        
        @Schema(description = "좋아요 수", example = "42")
        private long likeCount;
        
        @Schema(description = "작성자 닉네임", example = "홍길동")
        private String ownerNickname;
        
        @Schema(description = "생성 시간", example = "KST Datetime")
        private LocalDateTime createdAt;
        
        @Schema(description = "내가 좋아요 눌렀는지 여부", example = "true")
        private boolean isLiked;
        
        @Schema(description = "내가 주인인지 여부", example = "true")
        private boolean isOwner;

        public static Response of(Archive archive, String bannerUrl, long viewCount, long likeCount, boolean isLiked, boolean isOwner) {
            return Response.builder()
                    .id(archive.getId())
                    .title(archive.getTitle())
                    .visibility(archive.getVisibility())
                    .badge(archive.getBadge())
                    .bannerUrl(bannerUrl)
                    .viewCount(viewCount)
                    .likeCount(likeCount)
                    .ownerNickname(archive.getUser().getNickname())
                    .createdAt(archive.getCreatedAt())
                    .isLiked(isLiked)
                    .isOwner(isOwner)
                    .build();
        }
    }
}