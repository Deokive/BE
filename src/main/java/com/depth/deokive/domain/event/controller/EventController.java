package com.depth.deokive.domain.event.controller;

import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.service.EventService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
@Tag(name = "Event", description = "캘린더 일정 관리 API")
public class EventController {

    private final EventService eventService;

    @PostMapping("/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @Operation(summary = "일정 생성", description = "특정 아카이브(캘린더)에 일정을 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "일정 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (날짜 포맷 오류 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "일정 개수 초과 (하루 최대 4개)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"CONFLICT\", \"error\": \"EVENT_LIMIT_EXCEEDED\", \"message\": \"이벤트는 일정 당 최대 4개까지만 등록할 수 있습니다.\"}")
                    ))
    })
    public ResponseEntity<EventDto.Response> createEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "아카이브 ID", example = "1") @PathVariable Long archiveId,
            @Valid @RequestBody EventDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(user, archiveId, request));
    }

    @GetMapping("/{eventId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @Operation(summary = "일정 상세 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일정 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 캘린더)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventDto.Response> getEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "조회할 일정 ID", example = "10") @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(eventService.getEvent(user, eventId));
    }

    @PatchMapping("/{eventId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "일정 수정", description = "일정 정보를 수정합니다. (스포츠 토글 변경 시 데이터 처리 포함)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일정 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "일정 개수 초과 (날짜 변경 시 해당 날짜가 꽉 찬 경우)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventDto.Response> updateEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "수정할 일정 ID", example = "10") @PathVariable Long eventId,
            @Valid @RequestBody EventDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEvent(user, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "일정 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "일정 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "삭제할 일정 ID", example = "10") @PathVariable Long eventId
    ) {
        eventService.deleteEvent(user, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly/{archiveId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "월별 일정 조회", description = "특정 연/월의 일정을 모두 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "월별 일정 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 캘린더)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<EventDto.Response>> getMonthlyEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "아카이브 ID", example = "1") @PathVariable Long archiveId,
            @Parameter(description = "조회할 연도", example = "2025") @RequestParam int year,
            @Parameter(description = "조회할 월 (1~12)", example = "5") @RequestParam int month
    ) {
        return ResponseEntity.ok(eventService.getMonthlyEvents(user, archiveId, year, month));
    }
}