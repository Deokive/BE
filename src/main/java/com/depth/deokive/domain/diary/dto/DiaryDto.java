package com.depth.deokive.domain.diary.dto;

import com.depth.deokive.common.util.FileUrlUtils;
import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DiaryDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "일기 작성 요청 DTO")
    public static class CreateRequest {
        @NotBlank(message = "일기 제목은 필수입니다.")
        @Schema(description = "일기 제목", example = "오늘의 일기")
        private String title;

        @NotBlank(message = "일기 내용은 필수입니다.")
        @Schema(description = "일기 내용", example = "일기 내용")
        private String content;

        @NotNull(message = "날짜는 필수입니다.")
        @Schema(description = "일기 기록 날짜 (사용자 지정)", example = "2024-12-25")
        private LocalDate recordedAt;

        @NotBlank(message = "색상은 필수입니다.")
        @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "올바른 HEX 컬러 코드가 아닙니다.")
        @Schema(description = "일기 배경/테마 색상", example = "#FF5733")
        private String color;

        @NotNull(message = "공개 범위는 필수입니다.")
        @Schema(description = "공개 범위 (PUBLIC, RESTRICTED, PRIVATE)", example = "PUBLIC")
        private Visibility visibility;

        @Schema(description = "첨부 파일 리스트")
        private List<AttachedFileRequest> files;

        public Diary toEntity(DiaryBook diaryBook) {
            return Diary.builder()
                    .title(title)
                    .content(content)
                    .recordedAt(recordedAt)
                    .color(color)
                    .visibility(visibility)
                    .diaryBook(diaryBook)
                    .build();
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "일기 수정 요청 DTO")
    public static class UpdateRequest {
        @Schema(description = "일기 제목", example = "오늘의 일기")
        private String title;

        @Schema(description = "일기 내용", example = "일기 내용")
        private String content;

        @Schema(description = "일기 기록 날짜 (사용자 지정)", example = "2024-12-25")
        private LocalDate recordedAt;

        @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "올바른 HEX 컬러 코드가 아닙니다.")
        @Schema(description = "일기 배경/테마 색상", example = "#FF5733")
        private String color;

        @Schema(description = "공개 범위 (PUBLIC, RESTRICTED, PRIVATE)", example = "PUBLIC")
        private Visibility visibility;

        @Schema(description = "첨부 파일 리스트")
        private List<AttachedFileRequest> files;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "일기 상세 응답 DTO")
    public static class Response {
        private Long id;
        private String title;
        private String content;
        private LocalDate recordedAt;
        private String color;
        private Visibility visibility;
        private Long diaryBookId;
        private Long createdBy;
        private List<FileDto.UploadFileResponse> files;

        public static Response of(Diary diary, List<DiaryFileMap> maps) {
            return Response.builder()
                    .id(diary.getId())
                    .title(diary.getTitle())
                    .content(diary.getContent())
                    .recordedAt(diary.getRecordedAt())
                    .color(diary.getColor())
                    .visibility(diary.getVisibility())
                    .diaryBookId(diary.getDiaryBook().getId()) // Archive ID와 동일
                    .createdBy(diary.getCreatedBy())
                    .files(toFileResponses(maps))
                    .build();
        }

        private static List<FileDto.UploadFileResponse> toFileResponses(List<DiaryFileMap> maps) {
            if (maps == null || maps.isEmpty()) return Collections.emptyList();
            return maps.stream().map(map -> {
                File file = map.getFile();
                return FileDto.UploadFileResponse.builder()
                        .fileId(file.getId())
                        .filename(file.getFilename())
                        .cdnUrl(FileUrlUtils.buildCdnUrl(file.getS3ObjectKey()))
                        .mediaRole(map.getMediaRole())
                        .sequence(map.getSequence())
                        .build();
            }).collect(Collectors.toList());
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "다이어리북 제목 수정 요청 DTO")
    public static class UpdateBookTitleRequest {
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "변경할 다이어리북 제목", example = "2025년 나의 기록 (수정)")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "다이어리북 제목 수정 성공 응답")
    public static class UpdateBookTitleResponse {
        @Schema(description = "수정된 다이어리북 ID (Archive ID)", example = "1")
        private Long diaryBookId;

        @Schema(description = "수정된 제목", example = "2025년 나의 기록 (수정)")
        private String updatedTitle;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AttachedFileRequest {
        @NotNull private Long fileId;
        private MediaRole mediaRole;
        private Integer sequence;
    }

    // For Pagination
    @Data @NoArgsConstructor
    @Schema(description = "다이어리 목록 페이징 요청")
    public static class DiaryPageRequest {
        @Min(0) @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
        private int page = 0;

        @Min(1) @Max(100) @Schema(description = "페이지 크기", example = "12") // 3x4 그리드 고려
        private int size = 12;

        public Pageable toPageable() {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        }
    }

    @Data @NoArgsConstructor
    @Schema(description = "다이어리 페이지네이션 응답 항목 (경량화)")
    public static class DiaryPageResponse {
        @Schema(description = "다이어리 ID", example = "1")
        private Long diaryId;

        @Schema(description = "일기 제목", example = "오늘의 기록")
        private String title;

        @Schema(description = "대표 이미지 URL (썸네일)",
                example = "https://cdn.exanmple.com/files/thumbnails/thumbnail/dummy.jpg")
        private String thumbnailUrl;

        @Schema(description = "다이어리 일정", example = "KST DateTime")
        private LocalDate recordedAt;

        @Schema(description = "공개 범위 (아이콘 표시용)", example = "PUBLIC")
        private Visibility visibility;

        @QueryProjection
        public DiaryPageResponse(Long diaryId, String title, String thumbnailKey, LocalDate recordedAt, Visibility visibility) {
            this.diaryId = diaryId;
            this.title = title;
            this.thumbnailUrl = FileUrlUtils.buildCdnUrl(thumbnailKey);
            this.recordedAt = recordedAt;
            this.visibility = visibility;
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "다이어리 목록 페이징 응답")
    public static class PageListResponse {
        @Schema(description = "아카이브(다이어리북) 제목", example = "2025년 나의 기록")
        private String bookTitle;

        @Schema(description = "다이어리 목록")
        private List<DiaryPageResponse> content;

        @Schema(description = "페이징 메타데이터")
        private ArchiveDto.PageInfo page; // ArchiveDto 재사용을 일단 여기서 했는데, 리팩터링 때 아예 common 쪽으로 옮겨버릴 것

        public static PageListResponse of(String bookTitle, Page<DiaryPageResponse> pageData) {
            return PageListResponse.builder()
                    .bookTitle(bookTitle)
                    .content(pageData.getContent())
                    .page(new ArchiveDto.PageInfo(pageData))
                    .build();
        }
    }
}
