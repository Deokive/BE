package com.depth.deokive.domain.archive.dto;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.querydsl.core.annotations.QueryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        private LocalDateTime createdAt; // 이게 day-N 데이터가 될거임

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

    @Data @NoArgsConstructor
    @Schema(description = "아카이브 피드 목록 조회 요청 DTO")
    public static class ArchivePageRequest {
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100을 초과할 수 없습니다.")
        @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
        private int size = 10;

        @Pattern(regexp = "^(createdAt|lastModifiedAt|viewCount|likeCount|hotScore)$", message = "정렬 기준이 올바르지 않습니다.")
        @Schema(description = "정렬 기준 컬럼", defaultValue = "createdAt",
                allowableValues = {"createdAt", "lastModifiedAt", "viewCount", "likeCount", "hotScore"}, example = "createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "정렬 방향은 'ASC' 또는 'DESC' 여야 합니다.")
        @Schema(description = "정렬 방향", defaultValue = "DESC", allowableValues = {"ASC", "DESC"}, example = "desc")
        private String direction = "DESC";

        public Pageable toPageable() {
            Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            return PageRequest.of(page, size, sortDirection, sort);
        }
    }

    @Data @NoArgsConstructor
    @Schema(description = "아카이브 피드 목록 조회용 경량 DTO")
    public static class ArchivePageResponse {
        @Schema(description = "아카이브 아이디", example = "1")
        private Long archiveId;
        
        @Schema(description = "아카이브 제목", example = "나의 첫 아카이브")
        private String title;
        
        @Schema(description = "배너 썸네일 이미지 URL", example = "https://cdn.example.com/files/thumbnails/medium/banner.jpg")
        private String thumbnailUrl;
        
        @Schema(description = "조회수", example = "150")
        private Long viewCount;
        
        @Schema(description = "좋아요 수", example = "42")
        private Long likeCount;

        @Schema(description = "핫 스코어", example = "24353.23")
        private Double hotScore;

        @Schema(description = "공개 범위", example = "PUBLIC | RESTRICTED | PRIVATE")
        private Visibility visibility;
        
        @Schema(description = "생성 시간", example = "KST Datetime")
        private LocalDateTime createdAt;
        
        @Schema(description = "수정 시간", example = "KST Datetime")
        private LocalDateTime lastModifiedAt;
        
        @Schema(description = "작성자 닉네임", example = "홍길동")
        private String ownerNickname;

        @QueryProjection // Q-Class 재생성 필요
        public ArchivePageResponse(Long archiveId, String title, String thumbnailUrl,
                                   Long viewCount, Long likeCount, Double hotScore, Visibility visibility,
                                   LocalDateTime createdAt, LocalDateTime lastModifiedAt, String ownerNickname) {
            this.archiveId = archiveId;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
            this.viewCount = viewCount;
            this.likeCount = likeCount;
            this.hotScore = hotScore;
            this.visibility = visibility;
            this.createdAt = createdAt;
            this.lastModifiedAt = lastModifiedAt;
            this.ownerNickname = ownerNickname;
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "아카이브 피드 목록 페이징 응답 DTO")
    public static class PageListResponse {
        @Schema(description = "페이지 제목", example = "전체 피드")
        private String pageTitle;

        @Schema(description = "아카이브 피드 목록")
        private List<ArchivePageResponse> content;
        
        @Schema(description = "페이지 메타데이터")
        private PageInfo page;

        public static PageListResponse of(String pageTitle, Page<ArchivePageResponse> pageData) {
            return PageListResponse.builder()
                    .pageTitle(pageTitle)
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
                    .build();
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "페이지 정보 메타데이터 DTO")
    public static class PageInfo {
        @Schema(description = "페이지 크기", example = "10")
        private int size;
        
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
        private int pageNumber;
        
        @Schema(description = "전체 요소 개수", example = "150")
        private long totalElements;
        
        @Schema(description = "전체 페이지 수", example = "15")
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