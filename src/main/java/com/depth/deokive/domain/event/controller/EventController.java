package com.depth.deokive.domain.event.controller;

import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.service.EventService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @Operation(summary = "일정 생성", description = "특정 아카이브(캘린더)에 일정을 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "일정 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "일정 개수 초과 (하루 최대 4개)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventDto.Response> createEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @Valid @RequestBody EventDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(user, archiveId, request));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "일정 상세 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일정 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 캘린더)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EventDto.Response> getEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(eventService.getEvent(user, eventId));
    }

    @PatchMapping("/{eventId}")
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
            @PathVariable Long eventId,
            @Valid @RequestBody EventDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEvent(user, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "일정 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "일정 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 일정입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long eventId
    ) {
        eventService.deleteEvent(user, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly/{archiveId}")
    @Operation(summary = "월별 일정 조회", description = "특정 연/월의 일정을 모두 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "월별 일정 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 캘린더)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<EventDto.Response>> getMonthlyEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(eventService.getMonthlyEvents(user, archiveId, year, month));
    }
}