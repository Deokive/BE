package com.depth.deokive.domain.post.controller;

import com.depth.deokive.domain.post.dto.RepostDto;
import com.depth.deokive.domain.post.service.RepostService;
import com.depth.deokive.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/repost")
@Tag(name = "Repost", description = "리포스트(스크랩) 관리 API")
public class RepostController {

    private final RepostService repostService;

    @PostMapping("/tabs/{archiveId}")
    @Operation(summary = "리포스트 탭 생성", description = "최대 10개까지 생성 가능")
    @ApiResponse(responseCode = "201", description = "리포스트 탭 생성 성공")
    public ResponseEntity<RepostDto.TabResponse> createTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long archiveId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repostService.createRepostTab(user, archiveId));
    }

    @PatchMapping("/tabs/{tabId}")
    @Operation(summary = "리포스트 탭 제목 수정")
    @ApiResponse(responseCode = "200", description = "리포스트 탭 제목 수정 성공")
    public ResponseEntity<RepostDto.TabResponse> updateTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId,
            @Valid @RequestBody RepostDto.UpdateTabRequest request
    ) {
        return ResponseEntity.ok(repostService.updateRepostTab(user, tabId, request));
    }

    @DeleteMapping("/tabs/{tabId}")
    @Operation(summary = "리포스트 탭 삭제")
    @ApiResponse(responseCode = "204", description = "리포스트 탭 삭제 성공")
    public ResponseEntity<Void> deleteTab(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId
    ) {
        repostService.deleteRepostTab(user, tabId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tabId}")
    @Operation(summary = "리포스트 생성", description = "원본 Post를 내 보관함에 저장합니다.")
    @ApiResponse(responseCode = "201", description = "리포스트 생성 성공")
    public ResponseEntity<RepostDto.Response> createRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long tabId,
            @Valid @RequestBody RepostDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repostService.createRepost(user, tabId, request));
    }

    @PatchMapping("/{repostId}")
    @Operation(summary = "리포스트 제목 수정", description = "내가 설정한 리포스트 제목만 수정합니다.")
    @ApiResponse(responseCode = "200", description = "리포스트 제목 수정 성공")
    public ResponseEntity<RepostDto.Response> updateRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long repostId,
            @Valid @RequestBody RepostDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(repostService.updateRepost(user, repostId, request));
    }

    @DeleteMapping("/{repostId}")
    @Operation(summary = "리포스트 삭제")
    @ApiResponse(responseCode = "204", description = "리포스트 삭제 성공")
    public ResponseEntity<Void> deleteRepost(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long repostId
    ) {
        repostService.deleteRepost(user, repostId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{archiveId}")
    @Operation(summary = "리포스트 목록 조회")
    @ApiResponse(responseCode = "200", description = "리포스트 목록 조회 성공")
    public ResponseEntity<RepostDto.RepostListResponse> getRepost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("archiveId") Long archiveId,
            @ParameterObject @ModelAttribute @Valid RepostDto.RepostPageRequest request
    ) {
        Pageable pageable = request.toPageable();
        Long tabId = request.getTabId();
        RepostDto.RepostListResponse response = repostService.getReposts(userPrincipal, archiveId, tabId, pageable);

        return ResponseEntity.ok(response);
    }
}