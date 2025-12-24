package com.depth.deokive.domain.archive.dto;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "아카이브 목록 페이징 응답 DTO", name = "ArchivePageListResponse")
    public static class PageListResponse {
        @Schema(description = "아카이브 목록")
        private List<Response> content;

        @Schema(description = "페이지 메타데이터")
        private PageInfo page;

        public static PageListResponse of(Page<Response> pageData) {
            return PageListResponse.builder()
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
                    .build();
        }
    }

    @Data
    @Schema(description = "아카이브 목록 조회 요청 DTO")
    public static class ArchivePageRequest {
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (0부터 시작)", defaultValue = "0")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "페이지 크기는 1000을 초과할 수 없습니다.")
        @Schema(description = "페이지 크기", defaultValue = "10")
        private int size = 10;

        @Pattern(regexp = "^(createdAt|lastModifiedAt)$", message = "정렬 기준은 createdAt 또는 lastModifiedAt 만 가능합니다.")
        @Schema(description = "정렬 기준 컬럼", defaultValue = "createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "정렬 방향은 ASC 또는 DESC 여야 합니다.")
        @Schema(description = "정렬 방향", defaultValue = "DESC")
        private String direction = "DESC";

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.Direction.fromString(direction.toUpperCase()), sort);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "페이지 정보 메타데이터 DTO")
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