package com.depth.deokive.domain.friend.controller;


import com.depth.deokive.domain.friend.dto.FriendDto;
import com.depth.deokive.domain.friend.service.FriendService;
import com.depth.deokive.system.ratelimit.annotation.RateLimit;
import com.depth.deokive.system.ratelimit.annotation.RateLimitType;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Friend API", description = "친구 관련 API")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "친구 요청 보내기", description = "특정 유저에게 친구 요청을 보냅니다.")
    @PostMapping("/request/{friendId}")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 요청 전송 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (자기 자신에게 요청 또는 이미 요청을 보냄)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 유저입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "충돌 (이미 친구 관계이거나, 상대방이 이미 나에게 요청을 보냄)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.sendFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 수락하기", description = "특정 유저의 친구 요청을 수락합니다.")
    @PostMapping("/{friendId}/accept")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 요청 수락 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (자기 자신 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "받은 친구 요청이 존재하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 친구 관계입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.acceptFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 거절하기", description = "특정 유저의 친구 요청을 거절합니다.")
    @PostMapping("/{friendId}/reject")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 요청 거절 성공"),
            @ApiResponse(responseCode = "404", description = "받은 친구 요청이 존재하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 친구 관계입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> rejectFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.rejectFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 취소하기", description = "특정 유저에게 보낸 친구 요청을 취소합니다.")
    @DeleteMapping("/request/{friendId}")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 요청 취소 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (대기 상태가 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "보낸 친구 요청이 존재하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> friendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.friendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 끊기", description = "특정 유저와의 친구 관계를 끊습니다.")
    @DeleteMapping("/{friendId}/cancel")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 끊기 성공"),
            @ApiResponse(responseCode = "404", description = "친구 관계가 존재하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.cancelFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 끊기 취소", description = "특정 유저와의 친구 끊기를 취소합니다.")
    @PostMapping("/{friendId}/recover")
    @RateLimit(type = RateLimitType.USER, capacity = 30, refillTokens = 30, refillPeriodSeconds = 3600)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "친구 복구 성공"),
            @ApiResponse(responseCode = "400", description = "복구할 수 없는 상태입니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "끊은 친구 내역이 존재하지 않습니다.", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> recoverFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.recoverFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내 친구 목록 조회", description = "현재 사용자와 친구(ACCEPTED)인 목록을 조회합니다.")
    @GetMapping
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<FriendDto.FriendListResponse<FriendDto.Response>> getMyFriends(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) Long lastFriendId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastAcceptedAt,
            @RequestParam(defaultValue = "20") int size
    ) {

        Pageable pageable = PageRequest.of(0, size);
        var response = friendService.getMyFriends(
                userPrincipal,
                lastFriendId,
                lastAcceptedAt,
                pageable
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "친구 요청 목록 조회", description = "보낸/받은 요청 목록을 조회합니다.")
    @GetMapping("/requests")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 타입 (type 파라미터 오류)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FriendDto.FriendListResponse<FriendDto.RequestResponse>> getFriendRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String type,
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastCreatedAt,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(0, size);

        var response = friendService.getFriendRequests(
                userPrincipal,
                type,
                lastId,
                lastCreatedAt,
                pageable
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "특정 유저와 관계 조회", description = "특정 유저와의 친구 상태(PENDING, ACCEPTED, REJECTED, CANCELED)를 조회합니다. 관계가 없으면 404를 반환합니다.")
    @GetMapping("/{friendId}/status")
    @RateLimit(type = RateLimitType.USER, capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (자기 자신 조회)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "관계 없음 (친구 아님)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FriendDto.FriendStatusResponse> getFriendStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        FriendDto.FriendStatusResponse response = friendService.getFriendStatus(userPrincipal, friendId);
        return ResponseEntity.ok(response);
    }
}
