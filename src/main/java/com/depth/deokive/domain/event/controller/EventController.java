package com.depth.deokive.domain.event.controller;

import com.depth.deokive.domain.event.dto.EventDto;
import com.depth.deokive.domain.event.service.EventService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<EventDto.Response> getEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long eventId
    ) {
        return ResponseEntity.ok(eventService.getEvent(user, eventId));
    }

    @PatchMapping("/{eventId}")
    @Operation(summary = "일정 수정", description = "일정 정보를 수정합니다. (스포츠 토글 변경 시 데이터 처리 포함)")
    public ResponseEntity<EventDto.Response> updateEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long eventId,
            @Valid @RequestBody EventDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEvent(user, eventId, request));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "일정 삭제")
    public ResponseEntity<Void> deleteEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long eventId
    ) {
        eventService.deleteEvent(user, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly/{archiveId}")
    @Operation(summary = "월별 일정 조회", description = "특정 연/월의 일정을 모두 조회합니다.")
    public ResponseEntity<List<EventDto.Response>> getMonthlyEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(eventService.getMonthlyEvents(user, archiveId, year, month));
    }
}