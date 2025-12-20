package com.depth.deokive.domain.diary.dto;

import com.depth.deokive.domain.archive.entity.enums.Visibility;
import com.depth.deokive.domain.diary.entity.Diary;
import com.depth.deokive.domain.diary.entity.DiaryBook;
import com.depth.deokive.domain.diary.entity.DiaryFileMap;
import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.file.entity.enums.MediaRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DiaryDto {

    /* TODO:: Trade-Off 고려
        file 처리는 내부적으로 일괄처리가 깔끔함 -> PUT의 성격이 강함.
        근데 파일 외의 데이터 업데이트는 PATCH 성격이 강함 -> 공통으로 묶지 말고 CreateRequest, UpdateRequest가 더 나을 수 있음
        Post에서는 PUT으로 갔지만, 여전히 마음 한 켠에 남은 찜찜함이 존재함.
        FE 관점에서도 PATCH로 이해하는게 편하긴 할거임. 1000자 수준의 일기를 수정도 안했는데 요청에 실어보내는것도 좀 부담이고.
        그래서 Post와의 일관성을 위해서 일단은 PUT으로 가지만 추후 분리할 수도 있음.
        요청 필드는 동일한데, Validation 수준만 조절될거임 -> 코드 중복의 이슈도 있긴함
    */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "일기 작성/수정 요청 DTO")
    public static class Request {
        @NotBlank(message = "일기 제목은 필수입니다.")
        private String title;

        @NotBlank(message = "일기 내용은 필수입니다.")
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
    @Schema(description = "일기 상세 응답 DTO")
    public static class Response {
        private Long id;
        private String title;
        private String content;
        private LocalDate recordedAt;
        private String color;
        private Visibility visibility;
        private Long diaryBookId;
        private Long writerId;
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
                    .writerId(diary.getCreatedBy())
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
                        .cdnUrl(file.getFilePath())
                        .mediaRole(map.getMediaRole())
                        .sequence(map.getSequence())
                        .build();
            }).collect(Collectors.toList());
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AttachedFileRequest {
        @NotNull private Long fileId;
        private MediaRole mediaRole;
        private Integer sequence;
    }
}
