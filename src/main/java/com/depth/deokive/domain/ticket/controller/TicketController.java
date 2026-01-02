package com.depth.deokive.domain.ticket.controller;

import com.depth.deokive.domain.ticket.dto.TicketDto;
import com.depth.deokive.domain.ticket.service.TicketService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tickets")
@Tag(name = "Ticket", description = "티켓 관리 API")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/{archiveId}")
    @Operation(summary = "티켓 생성")
    public ResponseEntity<TicketDto.Response> createTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody TicketDto.CreateRequest request
    ) {
        TicketDto.Response response = ticketService.createTicket(userPrincipal, archiveId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "티켓 상세 조회")
    public ResponseEntity<TicketDto.Response> getTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId
    ) {
        TicketDto.Response response = ticketService.getTicket(userPrincipal, ticketId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{ticketId}")
    @Operation(summary = "티켓 수정")
    public ResponseEntity<TicketDto.Response> updateTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId,
            @Valid @RequestBody TicketDto.UpdateRequest request
    ) {
        TicketDto.Response response = ticketService.updateTicket(userPrincipal, ticketId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{ticketId}")
    @Operation(summary = "티켓 삭제")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long ticketId
    ) {
        ticketService.deleteTicket(userPrincipal, ticketId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/book/{archiveId}")
    @Operation(summary = "티켓북 제목 수정")
    public ResponseEntity<TicketDto.UpdateBookTitleResponse> updateTicketBookTitle(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @RequestBody TicketDto.UpdateBookTitleRequest request
    ) {
        TicketDto.UpdateBookTitleResponse response = ticketService.updateTicketBookTitle(userPrincipal, archiveId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/book/{archiveId}")
    @Operation(summary = "티켓북 페이지네이션 조회")
    public ResponseEntity<TicketDto.PageListResponse> getTicketBookPage(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long archiveId,
            @Valid @ModelAttribute TicketDto.TicketPageRequest pageRequest
    ) {
        TicketDto.PageListResponse response = ticketService.getTickets(userPrincipal, archiveId, pageRequest.toPageable());
        return ResponseEntity.ok(response);
    }
}