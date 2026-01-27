package com.depth.deokive.domain.ticket.controller;

import com.depth.deokive.common.dto.PageDto;
import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.depth.deokive.domain.ticket.service.TicketService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tickets")
@Tag(name = "Ticket", description = "티켓 관리 API")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @Operation(summary = "티켓 생성")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "티켓 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "생성 권한 없음 (아카이브 소유자가 아님) 또는 파일 접근 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브 또는 파일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TicketDto.Response> createTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody TicketDto.CreateRequest request
    ) {
        TicketDto.Response response = ticketService.createTicket(userPrincipal, archiveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{ticketId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @Operation(summary = "티켓 상세 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "티켓 조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 티켓입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TicketDto.Response> getTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId
    ) {
        TicketDto.Response response = ticketService.getTicket(userPrincipal, ticketId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{ticketId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "티켓 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "티켓 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님) 또는 파일 접근 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 티켓 또는 파일입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TicketDto.Response> updateTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId,
            @Valid @RequestBody TicketDto.UpdateRequest request
    ) {
        TicketDto.Response response = ticketService.updateTicket(userPrincipal, ticketId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{ticketId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "티켓 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "티켓 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 티켓입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId
    ) {
        ticketService.deleteTicket(userPrincipal, ticketId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/book/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @Operation(summary = "티켓북 제목 수정")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "티켓북 제목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (제목 누락 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (아카이브 소유자가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TicketDto.UpdateBookTitleResponse> updateTicketBookTitle(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody TicketDto.UpdateBookTitleRequest request
    ) {
        TicketDto.UpdateBookTitleResponse response = ticketService.updateTicketBookTitle(userPrincipal, archiveId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/book/{archiveId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "티켓북 페이지네이션 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 요청 (페이지 범위 초과)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 아카이브)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageDto.PageListResponse<TicketDto.TicketPageResponse>> getTicketBookPage(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @ModelAttribute TicketDto.TicketPageRequest pageRequest
    ) {
        return ResponseEntity.ok(ticketService.getTickets(userPrincipal, archiveId, pageRequest.toPageable()));
    }
}