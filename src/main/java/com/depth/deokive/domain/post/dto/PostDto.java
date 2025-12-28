package com.depth.deokive.domain.post.dto;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PostDto {
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "게시글 작성 요청 DTO")
    public static class Request {
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "게시글 제목", example = "짱구는 못말려: 어른 제국의 역습 후기")
        private String title;

        @NotBlank(message = "게시글 작성은 필수입니다.")
        @Schema(description = "게시글 본문", example = "신형만의 회상씬은 정말 최고였다 ... (중략)")
        private String content;

        @NotNull(message = "게시글 카테고리 설정은 필수입니다.")
        @Schema(description = "게시글 카테고리", example = "IDOL | ACTOR | MUSICIAN | SPORT | ARTIST | ANIMATION")
        private Category category;

        @Schema(description = "첨부된 파일 연결 정보 리스트")
        private List<AttachedFileRequest> files;

        public static Post from(PostDto.Request request, User user) {
            return Post.builder()
                    .title(request.getTitle())
                    .content(request.getContent())
                    .category(request.getCategory())
                    .user(user)
                    .build();
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "게시글 정보 응답 DTO")
    public static class Response {
        @Schema(description = "게시글 아이디", example = "1")
        private Long id;

        @Schema(description = "게시글 제목", example = "짱구는 못말려: 어른 제국의 역습 후기")
        private String title;

        @Schema(description = "게시글 본문", example = "신형만의 회상씬은 정말 최고였다 ... (중략)")
        private String content;

        @Schema(description = "게시글 카테고리", example = "IDOL | ACTOR | MUSICIAN | SPORT | ARTIST | ANIMATION")
        private Category category;

        @Schema(description = "게시글 생성 시간", example = "KST Datetime")
        private LocalDateTime createdAt;

        @Schema(description = "게시글 수정 시간", example = "KST Datetime")
        private LocalDateTime lastModifiedAt;

        @Schema(description = "게시글 작성자 아이디", example = "5")
        private Long createdBy;

        @Schema(description = "게시글 수정자 아이디", example = "5")
        private Long lastModifiedBy;

        @Schema(description = "조회수", example = "150")
        private Long viewCount;

        @Schema(description = "좋아요 수", example = "25")
        private Long likeCount;

        @Schema(description = "첨부 파일 객체 리스트", example = """
            [
              {
                "fileId": 1,
                "filename": "a1b2c3d4-image1.png",
                "cdnUrl": "https://cdn.example.com/posts/123/a1b2c3d4-image1.png",
                "fileSize": 102400,
                "mediaType": "IMAGE",
                "mediaRole": "CONTENT",
                "sequence": 0
              },
              {
                "fileId": 2,
                "filename": "e5f6g7h8-preview.jpg",
                "cdnUrl": "https://cdn.example.com/posts/123/e5f6g7h8-preview.jpg",
                "fileSize": 20480,
                "mediaType": "IMAGE",
                "mediaRole": "PREVIEW",
                "sequence": 1
              },
              {
                "fileId": 3,
                "filename": "i9j0k1l2-video1.mp4",
                "cdnUrl": "https://cdn.example.com/posts/123/i9j0k1l2-video1.mp4",
                "fileSize": 5242880,
                "mediaType": "VIDEO",
                "mediaRole": "CONTENT",
                "sequence": 2
              }
            ]
        """)
        private List<FileDto.UploadFileResponse> files;

        public static Response of(Post post, List<PostFileMap> maps) {
            return Response.builder()
                    .id(post.getId())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .category(post.getCategory())
                    .createdAt(post.getCreatedAt())
                    .lastModifiedAt(post.getLastModifiedAt())
                    .createdBy(post.getUser().getId())
                    .lastModifiedBy(post.getUser().getId())
                    .files(toFileResponses(maps))
                    .build();
        }

        private static List<FileDto.UploadFileResponse> toFileResponses(List<PostFileMap> maps) {
            if (maps == null || maps.isEmpty()) { return Collections.emptyList(); }

            return maps.stream()
                .map(map -> {
                    File file = map.getFile();
                    return FileDto.UploadFileResponse.builder()
                        .fileId(file.getId())
                        .filename(file.getFilename())
                        .cdnUrl(file.getFilePath())
                        .fileSize(file.getFileSize())
                        .mediaType(file.getMediaType().name())
                        .mediaRole(map.getMediaRole())
                        .sequence(map.getSequence())
                        .build();
                })
                .collect(Collectors.toList());
        }
    }

    /**
     * 게시글 생성 시 파일 연결을 위한 내부 DTO
     * 이미 업로드된 File Entity의 ID만 받는다.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AttachedFileRequest {
        @NotNull(message = "파일 ID는 필수입니다.")
        @Schema(description = "업로드 완료된 파일 ID", example = "105")
        private Long fileId;

        @Schema(description = "파일 역할 (CONTENT: 본문, PREVIEW: 썸네일/대표)", example = "PREVIEW")
        private MediaRole mediaRole;

        @Schema(description = "파일 정렬 순서", example = "1")
        private Integer sequence;
    }

    // DESCRIPTION: PAGINATION DTOS
    @Data @NoArgsConstructor
    @Schema(description = "게시글 피드 목록 조회 요청 DTO")
    public static class FeedRequest {
        @Min(value = 0)
        @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
        private int page = 0;

        @Min(value = 1) @Max(value = 1000)
        @Schema(description = "페이지 크기", example = "10")
        private int size = 10;

        @Schema(description = "카테고리 필터 (없을 시 전체 조회)", example = "IDOL")
        private Category category;

        @Pattern(regexp = "^(createdAt|viewCount|likeCount|hotScore)$")
        @Schema(description = "정렬 기준", defaultValue = "createdAt", allowableValues = {"createdAt", "viewCount", "likeCount", "hotScore"})
        private String sort = "createdAt";

        @Schema(description = "정렬 방향", defaultValue = "DESC")
        private String direction = "DESC";

        public Pageable toPageable() {
            Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            return PageRequest.of(page, size, sortDirection, sort);
        }
    }

    @Data @NoArgsConstructor
    @Schema(description = "게시글 피드 응답 DTO (Lightweight)")
    public static class FeedResponse {
        @Schema(description = "게시글 ID", example = "1")
        private Long postId;

        @Schema(description = "제목", example = "게시글 제목")
        private String title;

        @Schema(description = "카테고리", example = "IDOL")
        private Category category;

        @Schema(description = "썸네일 URL",
                example = "https://cdn.example.com/files/thumbnails/thumbnail/thumbnail123.jpg")
        private String thumbnailUrl;

        @Schema(description = "작성자 닉네임", example = "홍길동")
        private String writerNickname;

        @Schema(description = "좋아요 수", example = "10")
        private Long likeCount;

        @Schema(description = "조회수", example = "100")
        private Long viewCount;

        @Schema(description = "핫 스코어", example = "50.5")
        private Double hotScore;

        @Schema(description = "생성 시간")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시간")
        private LocalDateTime lastModifiedAt;

        @QueryProjection // Q-Class 생성용
        public FeedResponse(Long postId, String title, Category category, String thumbnailUrl,
                            String writerNickname, Long likeCount, Long viewCount, Double hotScore,
                            LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
            this.postId = postId;
            this.title = title;
            this.category = category;
            this.thumbnailUrl = ThumbnailUtils.getSmallThumbnailUrl(thumbnailUrl); // TODO: Check isOrigin or realThumbnail
            this.writerNickname = writerNickname;
            this.likeCount = likeCount;
            this.viewCount = viewCount;
            this.hotScore = hotScore;
            this.createdAt = createdAt;
            this.lastModifiedAt = lastModifiedAt;
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "게시글 피드 페이징 응답 Wrapper")
    public static class PageListResponse {
        @Schema(description = "페이지 제목", example = "아이돌 게시판")
        private String pageTitle;
        private List<FeedResponse> content;
        private PageInfo page;

        public static PageListResponse of(String pageTitle, Page<FeedResponse> pageData) {
            return PageListResponse.builder()
                    .pageTitle(pageTitle)
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
                    .build();
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
