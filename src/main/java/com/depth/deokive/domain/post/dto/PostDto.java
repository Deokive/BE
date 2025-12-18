package com.depth.deokive.domain.post.dto;

import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import com.depth.deokive.domain.post.entity.Post;
import com.depth.deokive.domain.post.entity.PostFileMap;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.user.dto.UserDto;
import com.depth.deokive.domain.user.entity.User;
import com.depth.deokive.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PostDto {
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "게시글 작성 요청 DTO")
    public static class Request {
        @Schema(description = "게시글 제목", example = "짱구는 못말려: 어른 제국의 역습 후기")
        private String title;

        @Schema(description = "게시글 본문", example = "신형만의 회상씬은 정말 최고였다 ... (중략)")
        private String content;

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
                    .createdBy(post.getCreatedBy())
                    .lastModifiedBy(post.getLastModifiedBy())
                    .files(toFileResponses(maps))
                    .build();
        }

        private static List<FileDto.UploadFileResponse> toFileResponses(List<PostFileMap> maps) {
            if (maps == null || maps.isEmpty()) {
                return Collections.emptyList();
            }

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
}
