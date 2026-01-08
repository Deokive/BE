package com.depth.deokive.domain.sticker.controller;

import com.depth.deokive.domain.sticker.dto.StickerDto;
import com.depth.deokive.domain.sticker.service.StickerService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/v1/stickers")
@Tag(name = "Sticker", description = "캘린더 스티커 관리 API")
public class StickerController {

    private final StickerService stickerService;

    @PostMapping("/{archiveId}")
    @Operation(summary = "스티커 등록", description = "해당 날짜에 스티커를 등록합니다. (날짜당 1개 제한)")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    public ResponseEntity<StickerDto.Response> createSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @Valid @RequestBody StickerDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stickerService.createSticker(user, archiveId, request));
    }

    @PatchMapping("/{stickerId}")
    @Operation(summary = "스티커 수정", description = "스티커의 타입이나 날짜를 수정합니다.")
    public ResponseEntity<StickerDto.Response> updateSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long stickerId,
            @Valid @RequestBody StickerDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(stickerService.updateSticker(user, stickerId, request));
    }

    @DeleteMapping("/{stickerId}")
    @Operation(summary = "스티커 삭제")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    public ResponseEntity<Void> deleteSticker(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long stickerId
    ) {
        stickerService.deleteSticker(user, stickerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly/{archiveId}")
    @Operation(summary = "월별 스티커 조회", description = "특정 연/월의 스티커 목록을 조회합니다.")
    public ResponseEntity<List<StickerDto.Response>> getMonthlyStickers(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(stickerService.getMonthlyStickers(user, archiveId, year, month));
    }
}