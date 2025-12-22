package com.depth.deokive.domain.ticket.dto;

import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TicketDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "티켓 생성/수정 요청 DTO")
    public static class Request {
        @NotBlank(message = "공연명은 필수입니다.")
        @Schema(description = "공연명", example = "BTS 월드투어 2024")
        private String title;

        @NotNull(message = "날짜는 필수입니다.")
        @Schema(description = "공연 날짜 및 시간", example = "2024-12-25T19:00:00")
        private LocalDateTime date;

        @Size(max = 20)
        @Schema(description = "공연 장소", example = "올림픽공원")
        private String location;
        
        @Size(max = 20)
        @Schema(description = "좌석 정보", example = "VIP석 1열")
        private String seat;
        
        @Schema(description = "출연진 정보", example = "BTS")
        private String casting;

        @Min(0) @Max(5)
        @Schema(description = "평점 (0~5)", example = "5")
        private Integer score;

        @Size(max = 100)
        @Schema(description = "후기", example = "정말 최고의 공연이었습니다!")
        private String review;

        @Schema(description = "티켓 이미지 파일 ID (단일)", example = "100")
        private Long fileId;

        public Ticket toEntity(TicketBook ticketBook, File file) {
            return Ticket.builder()
                    .title(title)
                    .date(date)
                    .location(location)
                    .seat(seat)
                    .casting(casting)
                    .score(score)
                    .review(review)
                    .ticketBook(ticketBook)
                    .file(file)
                    .build();
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "티켓 상세 응답 DTO")
    public static class Response {
        @Schema(description = "티켓 아이디", example = "1")
        private Long id;
        
        @Schema(description = "공연명", example = "BTS 월드투어 2024")
        private String title;
        
        @Schema(description = "공연 날짜 및 시간", example = "2024-12-25T19:00:00")
        private LocalDateTime date;
        
        @Schema(description = "공연 장소", example = "올림픽공원")
        private String location;
        
        @Schema(description = "좌석 정보", example = "VIP석 1열")
        private String seat;
        
        @Schema(description = "출연진 정보", example = "BTS")
        private String casting;
        
        @Schema(description = "평점 (0~5)", example = "5")
        private Integer score;
        
        @Schema(description = "후기", example = "정말 최고의 공연이었습니다!")
        private String review;
        
        @Schema(description = "소속 티켓북 ID", example = "1")
        private Long ticketBookId;

        @Schema(description = "티켓 이미지 정보")
        private FileDto.UploadFileResponse file;

        public static Response of(Ticket ticket) {
            return Response.builder()
                    .id(ticket.getId())
                    .title(ticket.getTitle())
                    .date(ticket.getDate())
                    .location(ticket.getLocation())
                    .seat(ticket.getSeat())
                    .casting(ticket.getCasting())
                    .score(ticket.getScore())
                    .review(ticket.getReview())
                    .ticketBookId(ticket.getTicketBook().getId())
                    .file(ticket.getFile() != null ? toFileResponse(ticket.getFile()) : null)
                    .build();
        }

        private static FileDto.UploadFileResponse toFileResponse(File file) {
            return FileDto.UploadFileResponse.builder()
                    .fileId(file.getId())
                    .filename(file.getFilename())
                    .cdnUrl(file.getFilePath())
                    .mediaType(file.getMediaType().name())
                    .build();
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "티켓북 제목 수정 요청 DTO")
    public static class UpdateBookTitleRequest {
        @NotBlank @Size(max = 50)
        @Schema(description = "변경할 티켓북 제목", example = "2024년 콘서트 티켓 모음")
        private String title;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "티켓북 제목 수정 성공 응답 DTO")
    public static class UpdateBookTitleResponse {
        @Schema(description = "수정된 티켓북 ID", example = "1")
        private Long ticketBookId;
        
        @Schema(description = "수정된 제목", example = "2024년 콘서트 티켓 모음")
        private String updatedTitle;
    }
}