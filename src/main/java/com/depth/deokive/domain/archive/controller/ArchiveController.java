package com.depth.deokive.domain.archive.controller;

import com.depth.deokive.domain.archive.dto.ArchiveDto;
import com.depth.deokive.domain.archive.service.ArchiveService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archives")
@RequiredArgsConstructor
@Tag(name = "Archive", description = "아카이브 API")
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/me")
    @Operation(summary = "내 아카이브 목록 조회")
    public ResponseEntity<ArchiveDto.PageListResponse> getMyArchives(
            @AuthenticationPrincipal UserPrincipal userPrincipal, // 로그인 필수
            @Valid @ModelAttribute ArchiveDto.ArchivePageRequest pageRequest
    ) {
        ArchiveDto.PageListResponse response =
                archiveService.getMyArchives(userPrincipal.getUserId(), pageRequest.toPageable());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/friend/{friendId}")
    @Operation(summary = "친구 아카이브 목록 조회")
    public ResponseEntity<ArchiveDto.PageListResponse> getFriendArchives(
            @AuthenticationPrincipal UserPrincipal userPrincipal, // 로그인 필수
            @PathVariable Long friendId,
            @Valid @ModelAttribute ArchiveDto.ArchivePageRequest pageRequest
    ) {
        ArchiveDto.PageListResponse response =
                archiveService.getFriendArchives(userPrincipal.getUserId(), friendId, pageRequest.toPageable());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/hot")
    @Operation(summary = "핫피드 목록 조회", description = "핫피드 목록을 조회")
    public ResponseEntity<ArchiveDto.PageListResponse> getHotArchives(
            // 비로그인도 가능 -> USerPrincipal 필요 X
            @Valid @ModelAttribute ArchiveDto.ArchivePageRequest pageRequest
    ) {
        ArchiveDto.PageListResponse response =
                archiveService.getHotArchives(pageRequest.toPageable());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "아카이브 생성", description = "새로운 아카이브를 생성하며, 내부의 하위 도메인 북(다이어리, 갤러리 등)을 자동으로 생성합니다.")
    @ApiResponse(responseCode = "201", description = "생성 성공", content = @Content(schema = @Schema(implementation = ArchiveDto.Response.class)))
    public ResponseEntity<ArchiveDto.Response> createArchive(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody ArchiveDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(archiveService.createArchive(user, request));
    }

    @GetMapping("/{archiveId}")
    @Operation(summary = "아카이브 상세 조회", description = "아카이브의 기본 정보(제목, 배너, 카운트 등)를 조회합니다. (공개 범위 권한 체크 포함)")
    public ResponseEntity<ArchiveDto.Response> getArchiveDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId
    ) {
        return ResponseEntity.ok(archiveService.getArchiveDetail(user, archiveId));
    }

    @PatchMapping("/{archiveId}")
    @Operation(summary = "아카이브 정보 수정", description = "제목, 공개 범위, 배너 이미지를 수정합니다.")
    public ResponseEntity<ArchiveDto.Response> updateArchive(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(archiveService.updateArchive(user, archiveId, request));
    }

    @DeleteMapping("/{archiveId}")
    @Operation(summary = "아카이브 삭제", description = "아카이브와 내부에 포함된 모든 데이터(이벤트, 일기, 티켓 등)를 영구 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공 (No Content)")
    public ResponseEntity<Void> deleteArchive(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId
    ) {
        archiveService.deleteArchive(user, archiveId);
        return ResponseEntity.noContent().build();
    }
}