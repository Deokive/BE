package com.depth.deokive.domain.post.dto;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.post.entity.Repost;
import com.depth.deokive.domain.post.entity.RepostBook;
import com.depth.deokive.domain.post.entity.RepostTab;
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

public class RepostDto {

    @Data @NoArgsConstructor
    @Schema(name = "RepostCreateRequest", description = "리포스트 생성 요청")
    public static class CreateRequest {
        @NotBlank(message = "URL은 필수입니다.")
        @Pattern(regexp = "^https?://.*", message = "올바른 URL 형식이 아닙니다.")
        @Size(max = 2048, message = "URL은 2048자를 초과할 수 없습니다.")
        @Schema(description = "외부 SNS URL", example = "https://twitter.com/username/status/123")
        private String url;
    }

    @Data @NoArgsConstructor
    @Schema(name = "RepostUpdateRequest", description = "리포스트 제목 수정 요청")
    public static class UpdateRequest {
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "변경할 리포스트 제목", example = "수정된 제목")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(name = "RepostResponse", description = "리포스트 응답")
    public static class Response {
        @Schema(description = "리포스트 아이디", example = "1")
        private Long id;

        @Schema(description = "외부 SNS URL", example = "https://twitter.com/username/status/123")
        private String url;

        @Schema(description = "리포스트 제목", example = "내가 리포스트한 게시글")
        private String title;

        @Schema(description = "썸네일 이미지 URL (nullable)", nullable = true)
        private String thumbnailUrl;

        @Schema(description = "소속 리포스트 탭 ID", example = "1")
        private Long repostTabId;

        public static Response of(Repost repost) {
            return Response.builder()
                    .id(repost.getId())
                    .url(repost.getUrl())
                    .title(repost.getTitle())
                    .thumbnailUrl(repost.getThumbnailUrl()) // Direct URL, no CDN conversion
                    .repostTabId(repost.getRepostTab().getId())
                    .build();
        }
    }

    @Data @NoArgsConstructor
    @Schema(name = "RepostUpdateTabRequest", description = "리포스트 탭 제목 수정 요청")
    public static class UpdateTabRequest {
        @NotBlank(message = "탭 제목은 필수입니다.") // update만 있는 이유는 UX 상 탭추가 누르면 기본 이름(ex. 탭이름)이 먼저 생성됨
        @Schema(description = "변경할 탭 제목", example = "내가 좋아하는 게시글")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(name = "RepostTabResponse", description = "리포스트 탭 응답")
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

    @Data @NoArgsConstructor
    @Schema(description = "리포스트 목록 요소 응답 DTO")
    public static class RepostElementResponse {
        @Schema(description = "리포스트 아이디", example = "1")
        private Long id;

        @Schema(description = "외부 SNS URL", example = "https://twitter.com/username/status/123")
        private String url;

        @Schema(description = "리포스트 제목", example = "제목")
        private String title;

        @Schema(description = "썸네일 이미지 URL (nullable)", nullable = true)
        private String thumbnailUrl;

        @Schema(description = "소속 탭 ID")
        private Long repostTabId;

        @Schema(description = "생성 시간")
        private LocalDateTime createdAt;

        @QueryProjection
        public RepostElementResponse(
                Long id, String url, String title,
                String thumbnailUrl, Long repostTabId, LocalDateTime createdAt) {
            this.id = id;
            this.url = url; // Changed from postId
            this.title = title;
            this.thumbnailUrl = thumbnailUrl; // Direct URL, no conversion
            this.repostTabId = repostTabId;
            this.createdAt = createdAt;
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
        private List<RepostElementResponse> content;

        @Schema(description = "페이지 메타데이터")
        private PageDto.PageInfo page;

        public static RepostListResponse of(
                String title, Long currentTabId, List<TabResponse> tabs, Page<RepostElementResponse> pageData) {
            return RepostListResponse.builder()
                    .title(title)
                    .tabId(currentTabId)
                    .tab(tabs)
                    .content(pageData.getContent())
                    .page(new PageDto.PageInfo(pageData))
                    .build();
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(name = "RepostBookUpdateResponse", description = "리포스트북 업데이트 응답")
    public static class RepostBookUpdateResponse {
        @Schema(description = "소속 리포스트북 ID", example = "1")
        private Long repostBookId;

        @Schema(description = "리포스트북 타이틀", example = "리포스트북 타이틀")
        private String title;

        public static RepostBookUpdateResponse of(RepostBook repostBook) {
            return RepostBookUpdateResponse.builder()
                    .repostBookId(repostBook.getId())
                    .title(repostBook.getTitle())
                    .build();
        }
    }
}