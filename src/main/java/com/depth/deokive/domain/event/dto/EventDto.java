package com.depth.deokive.domain.event.dto;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.event.entity.Event;
import com.depth.deokive.domain.event.entity.SportRecord;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class EventDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "이벤트 생성/수정 요청")
    public static class Request {
        @NotBlank(message = "일정 이름은 필수입니다.")
        @Schema(description = "이벤트 제목", example = "콘서트 관람")
        private String title;

        @NotNull(message = "날짜는 필수입니다.")
        @Schema(description = "이벤트 날짜", example = "2024-12-25")
        private LocalDate date;

        @Schema(description = "이벤트 시간 (hasTime이 true일 때만 유효)", example = "19:00")
        private LocalTime time; // 시간 설정이 꺼져있으면 null 가능

        @Builder.Default
        @Schema(description = "시간 설정 여부", example = "true")
        private Boolean hasTime = false;

        @NotBlank(message = "색상은 필수입니다.")
        @Schema(description = "이벤트 색상 코드", example = "#FF5733")
        @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "올바른 HEX 컬러 코드가 아닙니다.")
        private String color;

        @Builder.Default
        @Schema(description = "스포츠 타입 여부", example = "false")
        private Boolean isSportType = false;

        @Schema(description = "스포츠 정보 (isSportType이 true일 때만 유효)")
        private SportRequest sportInfo; // isSportType = true 일 때만 유효

        @Schema(description = "해시태그 리스트", example = "[\"콘서트\", \"라이브\"]")
        private List<String> hashtags;

        public Event toEntity(Archive archive, LocalDateTime recordAt) {
            return Event.builder()
                    .archive(archive)
                    .title(title)
                    .date(recordAt)
                    .hasTime(hasTime)
                    .color(color)
                    .isSportType(isSportType)
                    .build();
        }
    }

    @Data @NoArgsConstructor
    @Schema(description = "스포츠 경기 정보 요청 DTO")
    public static class SportRequest {
        @Schema(description = "팀 1 이름", example = "한화 이글스")
        private String team1;
        
        @Schema(description = "팀 2 이름", example = "LG 트윈스")
        private String team2;
        
        @Schema(description = "팀 1 점수", example = "5")
        private Integer score1;
        
        @Schema(description = "팀 2 점수", example = "3")
        private Integer score2;
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "이벤트 상세 응답")
    public static class Response {
        @Schema(description = "이벤트 아이디", example = "1")
        private Long id;
        
        @Schema(description = "이벤트 제목", example = "콘서트 관람")
        private String title;

        @JsonFormat(pattern = "yyyy-MM-dd")
        @Schema(description = "이벤트 날짜", example = "2024-12-25")
        private LocalDate date;

        @JsonFormat(pattern = "HH:mm")
        @Schema(description = "이벤트 시간", example = "19:00")
        private LocalTime time;

        @Schema(description = "시간 설정 여부", example = "true")
        private boolean hasTime;
        
        @Schema(description = "이벤트 색상 코드", example = "#FF5733")
        private String color;
        
        @Schema(description = "스포츠 타입 여부", example = "false")
        private boolean isSportType;

        @Schema(description = "스포츠 경기 정보")
        private SportResponse sportInfo;
        
        @Schema(description = "해시태그 리스트", example = "[\"콘서트\", \"라이브\"]")
        private List<String> hashtags;

        public static Response of(Event event, SportRecord sportRecord, List<String> hashtags) {
            return Response.builder()
                    .id(event.getId())
                    .title(event.getTitle())
                    .date(event.getDate().toLocalDate())
                    .time(event.isHasTime() ? event.getDate().toLocalTime() : null)
                    .hasTime(event.isHasTime())
                    .color(event.getColor())
                    .isSportType(event.isSportType())
                    .sportInfo(sportRecord != null ? SportResponse.of(sportRecord) : null)
                    .hashtags(hashtags)
                    .build();
        }
    }

    @Data @Builder @AllArgsConstructor
    @Schema(description = "스포츠 경기 정보 응답 DTO")
    public static class SportResponse {
        @Schema(description = "팀 1 이름", example = "한화 이글스")
        private String team1;
        
        @Schema(description = "팀 2 이름", example = "LG 트윈스")
        private String team2;
        
        @Schema(description = "팀 1 점수", example = "5")
        private Integer score1;
        
        @Schema(description = "팀 2 점수", example = "3")
        private Integer score2;

        public static SportResponse of(SportRecord record) {
            return SportResponse.builder()
                    .team1(record.getTeam1())
                    .team2(record.getTeam2())
                    .score1(record.getScore1())
                    .score2(record.getScore2())
                    .build();
        }
    }
}