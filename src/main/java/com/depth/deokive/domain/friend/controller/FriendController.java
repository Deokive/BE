package com.depth.deokive.domain.friend.controller;


import com.depth.deokive.domain.friend.dto.FriendDto;
import com.depth.deokive.domain.friend.service.FriendService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Friend API", description = "친구 관련 API")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "친구 요청 보내기", description = "특정 유저에게 친구 요청을 보냅니다.")
    @PostMapping("/request/{friendId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.sendFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 수락하기", description = "특정 유저의 친구 요청을 수락합니다.")
    @PostMapping("/{friendId}/accept")
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.acceptFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 거절하기", description = "특정 유저의 친구 요청을 거절합니다.")
    @PostMapping("/{friendId}/reject")
    public ResponseEntity<Void> rejectFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.rejectFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 요청 취소하기", description = "특정 유저에게 보낸 친구 요청을 취소합니다.")
    @DeleteMapping("/request/{friendId}")
    public ResponseEntity<Void> friendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.friendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 끊기", description = "특정 유저와의 친구 관계를 끊습니다.")
    @DeleteMapping("/{friendId}/cancel")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.cancelFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "친구 끊기 취소", description = "특정 유저와의 친구 끊기를 취소합니다.")
    @PostMapping("/{friendId}/recover")
    public ResponseEntity<Void> recoverFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long friendId
    ) {
        friendService.recoverFriendRequest(userPrincipal, friendId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내 친구 목록 조회", description = "현재 사용자와 친구(ACCEPTED)인 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<FriendDto.FriendListResponse> getMyFriends(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        FriendDto.FriendListResponse response = friendService.getMyFriends(userPrincipal, pageable);
        return ResponseEntity.ok(response);
    }
}
