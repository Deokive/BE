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
    public static class Response {
        private Long id;
        private String thumbnailUrl;
        private LocalDateTime createdAt;
        private LocalDateTime lastModifiedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageListResponse {
        private String title;
        private List<Response> content;
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
    public static class PageInfo {
        private int size;
        private int pageNumber;
        private long totalElements;
        private int totalPages;
        private boolean hasPrev;
        private boolean hasNext;
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
