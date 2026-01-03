package com.depth.deokive.domain.gallery.dto;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.querydsl.core.annotations.QueryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

public class GalleryDto {
    @Data @NoArgsConstructor
    @Schema(description = "갤러리 이미지 응답 DTO", name = "GalleryResponse")
    public static class Response {
        @Schema(description = "갤러리 아이디", example = "1")
        private Long id;
        
        @Schema(description = "썸네일 이미지 URL",
                example = "https://cdn.deokive.hooby-server.com/files/thumbnails/medium/uuid_filename.jpg")
        private String thumbnailUrl;

        @Schema(description = "원본 이미지 URL", example = "https://cdn.deokive.hooby-server.com/files/uuid_filename.jpg")
        private String originalUrl; // 사용자가 클릭 시 원본 이미지를 띄움
        
        @Schema(description = "생성 시간", example = "KST DateTime")
        private LocalDateTime createdAt;
        
        @Schema(description = "수정 시간", example = "KST DateTime")
        private LocalDateTime lastModifiedAt;

        @QueryProjection
        public Response(Long id, String originalKey, LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
            this.id = id;
            this.createdAt = createdAt;
            this.lastModifiedAt = lastModifiedAt;
            this.originalUrl = FileUrlUtils.buildCdnUrl(originalKey);
            this.thumbnailUrl = FileUrlUtils.buildCdnUrl(ThumbnailUtils.getMediumThumbnailKey(originalKey));
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리 목록 페이징 응답 DTO", name = "GalleryPageListResponse")
    public static class PageListResponse {
        @Schema(description = "갤러리 제목", example = "2024년 1월 갤러리")
        private String title;
        
        @Schema(description = "갤러리 이미지 목록", type = "array", implementation = Response.class)
        private List<Response> content;
        
        @Schema(description = "페이지 메타데이터")
        private PageDto.PageInfo page; // 커스텀 페이지 메타데이터

        public static PageListResponse of(String title, Page<Response> pageData) {
            return PageListResponse.builder()
                    .title(title)
                    .content(pageData.getContent())
                    .page(new PageDto.PageInfo(pageData))
                    .build();
        }
    }

    // DESCRIPTION: Pageable로 받을 수 있는데 Validation을 못함 -> 커스텀으로 Request를 만듦
    @Data
    @Schema(description = "갤러리 목록 조회 요청 DTO")
    public static class GalleryPageRequest {
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (0부터 시작)", defaultValue = "0", example = "?page=1")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "페이지 크기는 1000 초과할 수 없습니다.")
        @Schema(description = "페이지 크기", defaultValue = "10", example = "?page=1&size=9")
        private int size = 10;

        @Pattern(regexp = "^(createdAt|lastModifiedAt)$", message = "정렬은 'createdAt' 또는 'lastModifiedAt' 만 가능합니다.")
        @Schema(description = "정렬 기준 컬럼", defaultValue = "createdAt",
                allowableValues = {"createdAt", "lastModifiedAt"}, example = "?sort=createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "정렬 방향은 'ASC' 또는 'DESC' 여야 합니다.")
        @Schema(description = "정렬 방향", defaultValue = "DESC", allowableValues = {"ASC", "DESC"}, example = "?direction=asc")
        private String direction = "DESC";

        public Pageable toPageable() {
            Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            return PageRequest.of(page, size, sortDirection, sort);
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리 이미지 등록 요청 DTO")
    public static class CreateRequest {
        @NotEmpty(message = "파일 ID 리스트는 비어있을 수 없습니다.")
        @Size(max = 10, message = "한 번에 최대 10장까지만 업로드 가능합니다.") // 정책에 따라 조정
        @Schema(description = "업로드된 파일 ID 리스트", example = "[101, 102, 103]")
        private List<Long> fileIds;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리북 제목 수정 요청 DTO")
    public static class UpdateTitleRequest {
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "변경할 갤러리북 제목", example = "2024 제주도 여행 (수정됨)")
        private String title;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리 이미지 삭제 요청 DTO")
    public static class DeleteRequest {
        @NotEmpty(message = "삭제할 갤러리 ID 리스트는 비어있을 수 없습니다.")
        @Schema(description = "삭제할 갤러리 ID 리스트", example = "[1, 2, 5]")
        private List<Long> galleryIds;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "갤러리 이미지 등록 성공 응답")
    public static class CreateResponse {
        @Schema(description = "생성된 갤러리 아이템 개수", example = "5")
        private int createdCount;

        @Schema(description = "소속 아카이브 ID", example = "1")
        private Long archiveId;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "갤러리북 제목 수정 성공 응답")
    public static class UpdateTitleResponse {
        @Schema(description = "수정된 갤러리북 ID (Archive ID)", example = "1")
        private Long galleryBookId;

        @Schema(description = "수정된 제목", example = "2024 제주도 여행 (수정됨)")
        private String updatedTitle;
    }
}
