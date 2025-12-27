package com.depth.deokive.domain.ticket.dto;

import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.file.dto.FileDto;
import com.depth.deokive.domain.file.entity.File;
import com.depth.deokive.domain.ticket.entity.Ticket;
import com.depth.deokive.domain.ticket.entity.TicketBook;
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

    @Data @NoArgsConstructor
    @Schema(description = "티켓 목록 요소 응답 DTO", name = "TicketElementResponse")
    public static class TicketElementResponse {
        @Schema(description = "티켓 아이디", example = "10")
        private Long id;

        @Schema(description = "티켓 타이틀", example = "티켓 타이틀입니다.")
        private String title;

        @Schema(description = "썸네일 URL (동적 생성됨)", example = "https://cdn.../thumbnails/small/...")
        private String thumbnail;

        @Schema(description = "공연 날짜", example = "2024-12-25T19:00:00")
        private LocalDateTime date;

        @Schema(description = "좌석 정보", example = "A-29")
        private String seat;

        @Schema(description = "장소", example = "목동 CGV")
        private String location;

        @Schema(description = "출연진 (10글자 절삭)", example = "박서준, 지창욱...")
        private String casting;

        @Schema(description = "생성 시간")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시간")
        private LocalDateTime lastModifiedAt;

        @Builder
        public TicketElementResponse(Ticket ticket) {
            this.id = ticket.getId();
            this.title = ticket.getTitle();
            this.date = ticket.getDate();
            this.seat = ticket.getSeat();
            this.location = ticket.getLocation();
            this.createdAt = ticket.getCreatedAt();
            this.lastModifiedAt = ticket.getLastModifiedAt();

            if (ticket.getFile() != null) {
                this.thumbnail = ThumbnailUtils.getSmallThumbnailUrl(ticket.getFile().getFilePath());
            }

            this.casting = truncateCasting(ticket.getCasting());
        }

        private String truncateCasting(String original) {
            if (original == null) return null;
            if (original.length() <= 10) return original;
            return original.substring(0, 10) + "...";
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "티켓 목록 페이징 응답 DTO", name = "TicketPageListResponse")
    public static class PageListResponse {
        @Schema(description = "티켓북 타이틀", example = "티켓북 타이틀입니다")
        private String title;

        @Schema(description = "티켓 목록", type = "array", implementation = TicketElementResponse.class)
        private List<TicketElementResponse> content;

        @Schema(description = "페이지 메타데이터")
        private PageInfo page;

        public static PageListResponse of(String title, Page<TicketElementResponse> pageData) {
            return PageListResponse.builder()
                    .title(title)
                    .content(pageData.getContent())
                    .page(new PageInfo(pageData))
                    .build();
        }
    }

    @Data
    @Schema(description = "티켓 목록 조회 요청 DTO")
    public static class TicketPageRequest {
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (Zero-Index Based)", defaultValue = "0", example = "0")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "페이지 크기는 1000을 초과할 수 없습니다.")
        @Schema(description = "페이지 크기", defaultValue = "10", example = "10")
        private int size = 10;

        @Pattern(regexp = "^(createdAt|date)$", message = "정렬은 'createdAt' 또는 'date' 만 가능합니다.")
        @Schema(description = "정렬 기준 (createdAt: 생성일, date: 공연일)", defaultValue = "createdAt", example = "createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "정렬 순서는 'asc' 또는 'desc' 여야 합니다.")
        @Schema(description = "정렬 순서", defaultValue = "desc", example = "desc")
        private String direction = "desc";

        public Pageable toPageable() {
            Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            return PageRequest.of(page, size, sortDirection, sort);
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
        private String sort;
        private String direction;

        public PageInfo(Page<?> page) {
            this.size = page.getSize();
            this.pageNumber = page.getNumber();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.hasPrev = page.hasPrevious();
            this.hasNext = page.hasNext();
            this.empty = page.isEmpty();

            // Sort 정보 추출 (첫 번째 정렬 기준만 사용)
            if (page.getSort().isSorted()) {
                Sort.Order order = page.getSort().stream().findFirst().orElse(null);
                if (order != null) {
                    this.sort = order.getProperty();
                    this.direction = order.getDirection().name().toLowerCase();
                }
            } else {
                this.sort = "createdAt";
                this.direction = "desc";
            }
        }
    }
}