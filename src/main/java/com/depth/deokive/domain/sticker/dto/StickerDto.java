package com.depth.deokive.domain.sticker.dto;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.sticker.entity.Sticker;
import com.depth.deokive.domain.sticker.entity.enums.StickerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class StickerDto {

    @Data @NoArgsConstructor
    @Schema(name = "StickerCreateRequest", description = "스티커 등록 요청")
    public static class CreateRequest {
        @NotNull(message = "날짜는 필수입니다.")
        @Schema(description = "스티커 날짜", example = "2025-12-25")
        private LocalDate date;

        @NotNull(message = "스티커 타입은 필수입니다.")
        @Schema(description = "스티커 종류 (HEART, STAR, CIRCLE...)", example = "HEART")
        private StickerType stickerType;

        public Sticker toEntity(Archive archive) {
            return Sticker.builder()
                    .archive(archive)
                    .date(date)
                    .stickerType(stickerType)
                    .build();
        }
    }

    @Data @NoArgsConstructor
    @Schema(name = "StickerUpdateRequest", description = "스티커 수정 요청")
    public static class UpdateRequest {
        @Schema(description = "변경할 날짜 (null이면 유지)", example = "2025-12-25")
        private LocalDate date;

        @Schema(description = "변경할 스티커 종류 (null이면 유지)", example = "STAR")
        private StickerType stickerType;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(name = "StickerResponse", description = "스티커 응답")
    public static class Response {
        @Schema(description = "스티커 ID", example = "1")
        private Long id;

        @Schema(description = "날짜", example = "2025-12-25")
        private LocalDate date;

        @Schema(description = "스티커 타입", example = "HEART")
        private StickerType stickerType;

        public static Response from(Sticker sticker) {
            return Response.builder()
                    .id(sticker.getId())
                    .date(sticker.getDate())
                    .stickerType(sticker.getStickerType())
                    .build();
        }
    }
}