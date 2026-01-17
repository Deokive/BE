package com.depth.deokive.domain.notification.controller;

import com.depth.deokive.domain.notification.service.NotificationService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 구독", description = "로그인한 유저가 알림을 받기 위해 SSE에 연결합니다.")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE 연결 성공 (실시간 스트림 시작)", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 누락 또는 만료)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SseEmitter subscribe(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return notificationService.subscribe(userPrincipal.getUserId());
    }
}
