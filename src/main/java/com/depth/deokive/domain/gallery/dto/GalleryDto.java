package com.depth.deokive.domain.gallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리 이미지 응답 DTO", name = "GalleryResponse")
    public static class Response {
        @Schema(description = "갤러리 아이디", example = "1")
        private Long id;
        
        @Schema(description = "썸네일 이미지 URL", example = "https://cdn.example.com/gallery/123/thumbnail.jpg")
        private String thumbnailUrl;
        
        @Schema(description = "생성 시간", example = "2024-01-01T00:00:00")
        private LocalDateTime createdAt;
        
        @Schema(description = "수정 시간", example = "2024-01-01T00:00:00")
        private LocalDateTime lastModifiedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "갤러리 목록 페이징 응답 DTO", name = "GalleryPageListResponse")
    public static class PageListResponse {
        @Schema(description = "갤러리 제목", example = "2024년 1월 갤러리")
        private String title;
        
        @Schema(description = "갤러리 이미지 목록", type = "array", implementation = Response.class)
        private List<Response> content;
        
        @Schema(description = "페이지 메타데이터")
        private PageInfo page; // 커스텀 페이지 메타데이터

        public static PageListResponse of(String title, Page<Response> pageData) {
            return PageListResponse.builder()
                    .title(title)
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
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
    @Schema(description = "페이지 정보 메타데이터 DTO")
    public static class PageInfo {
        @Schema(description = "페이지 크기", example = "10")
        private int size;
        
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
        private int pageNumber;
        
        @Schema(description = "전체 요소 개수", example = "100")
        private long totalElements;
        
        @Schema(description = "전체 페이지 수", example = "10")
        private int totalPages;
        
        @Schema(description = "이전 페이지 존재 여부", example = "false")
        private boolean hasPrev;
        
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private boolean hasNext;
        
        @Schema(description = "빈 페이지 여부", example = "false")
        private boolean empty;

        public PageInfo(Page<?> page) {
            this.size = page.getSize();
            this.pageNumber = page.getNumber();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.hasPrev = page.hasPrevious();
            this.hasNext = page.hasNext();
            this.empty = page.isEmpty();
        }
    }
}
