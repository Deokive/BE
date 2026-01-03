package com.depth.deokive.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "공통 페이지 메타데이터")
public class PageDto {

    @Data @NoArgsConstructor @AllArgsConstructor
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

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @Schema(description = "공통 페이징 응답 Wrapper")
    public static class PageListResponse<T> {

        @Schema(description = "페이지 타이틀")
        private String title;

        @Schema(description = "데이터 목록")
        private List<T> content;

        @Schema(description = "페이징 메타데이터")
        private PageDto.PageInfo page;

        public static <T> PageListResponse<T> of(String title, Page<T> pageData) {
            return PageListResponse.<T>builder()
                    .title(title)
                    .content(pageData.getContent())
                    .page(new PageDto.PageInfo(pageData))
                    .build();
        }
    }
}