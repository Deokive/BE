package com.depth.deokive.domain.post.dto;

import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.entity.RepostTab;
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

import java.util.List;

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

    @Data
    @Schema(description = "리포스트 목록 조회 요청 DTO")
    public static class RepostPageRequest {
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (0부터 시작)", defaultValue = "0", example = "0")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "페이지 크기는 1000 초과할 수 없습니다.")
        @Schema(description = "페이지 크기", defaultValue = "10", example = "10")
        private int size = 10;

        @Pattern(regexp = "^(createdAt|title)$", message = "정렬은 'createdAt' 또는 'title' 만 가능합니다.")
        @Schema(description = "정렬 기준 컬럼", defaultValue = "createdAt",
                allowableValues = {"createdAt", "title"}, example = "createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "정렬 방향은 'ASC' 또는 'DESC' 여야 합니다.")
        @Schema(description = "정렬 방향", defaultValue = "DESC", allowableValues = {"ASC", "DESC"}, example = "DESC")
        private String direction = "DESC";

        @Schema(description = "조회할 탭 ID (null일 경우 첫 번째 탭을 조회합니다)", example = "1")
        private Long tabId;

        public Pageable toPageable() {
            Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            return PageRequest.of(page, size, sortDirection, sort);
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "리포스트 응답 DTO")
    public static class RepostListResponse {
        @Schema(description = "리포스트북 제목", example = "나의 보관함")
        private String title;

        @Schema(description = "현재 선택된 탭 ID", example = "1")
        private Long tabId;

        @Schema(description = "전체 탭 목록")
        private List<TabResponse> tab;

        @Schema(description = "리포스트 데이터 목록")
        private List<Response> content;

        @Schema(description = "페이지 메타데이터")
        private PageInfo page;

        public static RepostListResponse of(String title, Long currentTabId, List<TabResponse> tabs, Page<Response> pageData) {
            return RepostListResponse.builder()
                    .title(title)
                    .tabId(currentTabId)
                    .tab(tabs)
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
                    .build();
        }
    }

    @Data @AllArgsConstructor
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