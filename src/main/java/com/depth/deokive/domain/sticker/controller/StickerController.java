package com.depth.deokive.domain.sticker.controller;

import com.depth.deokive.domain.sticker.dto.StickerDto;
import com.depth.deokive.domain.sticker.service.StickerService;
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
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.depth.deokive.system.exception.dto.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stickers")
@Tag(name = "Sticker", description = "캘린더 스티커 관리 API")
public class StickerController {

    private final StickerService stickerService;

    @PostMapping("/{archiveId}")
    @RateLimit(type = RateLimitType.USER, capacity = 50, refillTokens = 50, refillPeriodSeconds = 3600)
    @Operation(summary = "스티커 등록", description = "해당 날짜에 스티커를 등록합니다. (날짜당 1개 제한)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL_INVALID_PARAMETER\", \"message\": \"유효성 검사 실패: 필수 필드가 누락되었습니다.\"}"))),
            @ApiResponse(responseCode = "403", description = "등록 권한 없음 (아카이브 소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "해당 날짜에 이미 스티커가 존재합니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"CONFLICT\", \"error\": \"STICKER_ALREADY_EXISTS\", \"message\": \"이미 해당 일정에 스티커가 존재합니다.\"}")))
    })
    public ResponseEntity<StickerDto.Response> createSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @Valid @RequestBody StickerDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stickerService.createSticker(user, archiveId, request));
    }

    @PatchMapping("/{stickerId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "스티커 수정", description = "스티커의 타입이나 날짜를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "스티커 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"BAD_REQUEST\", \"error\": \"GLOBAL_INVALID_PARAMETER\", \"message\": \"유효성 검사 실패: 필수 필드가 누락되었습니다.\"}"))),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스티커입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"STICKER_NOT_FOUND\", \"message\": \"존재하지 않는 스티커입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "수정하려는 날짜에 이미 다른 스티커가 존재합니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"CONFLICT\", \"error\": \"STICKER_ALREADY_EXISTS\", \"message\": \"이미 해당 일정에 스티커가 존재합니다.\"}")))
    })
    public ResponseEntity<StickerDto.Response> updateSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long stickerId,
            @Valid @RequestBody StickerDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(stickerService.updateSticker(user, stickerId, request));
    }

    @DeleteMapping("/{stickerId}")
    @RateLimit(type = RateLimitType.USER, capacity = 60, refillTokens = 60, refillPeriodSeconds = 3600)
    @Operation(summary = "스티커 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (소유자가 아님)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스티커입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"STICKER_NOT_FOUND\", \"message\": \"존재하지 않는 스티커입니다.\"}")))
    })
    public ResponseEntity<Void> deleteSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long stickerId
    ) {
        stickerService.deleteSticker(user, stickerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly/{archiveId}")
    @RateLimit(type = RateLimitType.AUTO, capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @Operation(summary = "월별 스티커 조회", description = "특정 연/월의 스티커 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음 (비공개 캘린더)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"FORBIDDEN\", \"error\": \"AUTH_FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 아카이브입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"ARCHIVE_NOT_FOUND\", \"message\": \"존재하지 않는 아카이브입니다.\"}")))
    })
    public ResponseEntity<List<StickerDto.Response>> getMonthlyStickers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(stickerService.getMonthlyStickers(user, archiveId, year, month));
    }
}